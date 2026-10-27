package com.jujutsuenhancement.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Visual test entity for the imported Mahito model and a first-pass stage-2 transformation animation. */
public final class MahitoPrototypeEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Boolean> TRANSFORMING =
            SynchedEntityData.defineId(MahitoPrototypeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRANSFORMED =
            SynchedEntityData.defineId(MahitoPrototypeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRANSFORM_ANIMATION_PLAYING =
            SynchedEntityData.defineId(MahitoPrototypeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOMAIN_INTRO_PLAYING =
            SynchedEntityData.defineId(MahitoPrototypeEntity.class, EntityDataSerializers.BOOLEAN);
    // 0 = no skill animation, 1 = soul strike, 2 = idle-transfiguration burst.
    private static final EntityDataAccessor<Integer> SKILL_ANIMATION =
            SynchedEntityData.defineId(MahitoPrototypeEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mahito.domain_idle");
    private static final RawAnimation TRANSFORM = RawAnimation.begin().thenPlay("animation.mahito.stage2");
    private static final RawAnimation DOMAIN_INTRO = RawAnimation.begin().thenPlay("animation.mahito.2s_domain_start");
    private static final RawAnimation STAGE2_IDLE = RawAnimation.begin().thenLoop("animation.mahito.2s_domain_idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int transformTicks;
    private int transformAnimationTicks;
    private int domainIntroTicks;
    private int soulStrikeCooldown;
    private int idleSkillCooldown;
    private int skillAnimationTicks;
    private UUID transformationOwner;
    private UUID domainHandsUuid;
    private UUID domainBarrierUuid;
    // A save may contain visual UUIDs after the domain phase has already ended.
    // Defer cleanup until the first server tick, when entities in the chunk have
    // normally been attached to the ServerLevel entity lookup.
    private boolean domainVisualCleanupPending;
    // Active domain intro may be restored before one or both visual entities are attached.
    private boolean domainVisualRepairPending;

    public MahitoPrototypeEntity(EntityType<? extends MahitoPrototypeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override public void tick() {
        super.tick();
        this.setNoGravity(true);
        if (!level().isClientSide && domainVisualCleanupPending && level() instanceof ServerLevel serverLevel) {
            discardDomainVisual(serverLevel, domainHandsUuid);
            discardDomainVisual(serverLevel, domainBarrierUuid);
            domainHandsUuid = null;
            domainBarrierUuid = null;
            domainVisualCleanupPending = false;
        }
        if (!level().isClientSide && domainVisualRepairPending && level() instanceof ServerLevel serverLevel) {
            // Keep retrying if the level refuses an insertion; do not silently abandon a
            // partially repaired visual pair after a transient entity-add failure.
            domainVisualRepairPending = !ensureDomainVisuals(serverLevel);
        }
        // Do not consume the first tick of the domain intro in the same tick that
        // the transformation animation finishes and starts the intro timer.
        boolean beganDomainIntroThisTick = false;
        if (!level().isClientSide && soulStrikeCooldown > 0) soulStrikeCooldown--;
        if (!level().isClientSide && idleSkillCooldown > 0) idleSkillCooldown--;
        if (!level().isClientSide && skillAnimationTicks > 0 && --skillAnimationTicks == 0) {
            entityData.set(SKILL_ANIMATION, 0);
        }
        if (!level().isClientSide && transformAnimationTicks > 0 && --transformAnimationTicks == 0) {
            entityData.set(TRANSFORM_ANIMATION_PLAYING, false);
            entityData.set(DOMAIN_INTRO_PLAYING, true);
            domainIntroTicks = 60; // Keep the full 60-tick (3-second) opening sequence.
            beganDomainIntroThisTick = true;
            // Spawn the domain visuals and apply its opening pulse when the intro starts,
            // not when the earlier 5.5-second transformation animation begins. This keeps
            // the 60-tick hands/barrier lifetime aligned with the visible domain opening.
            expandTransfiguredDomain();
        }
        if (!level().isClientSide && !beganDomainIntroThisTick
                && domainIntroTicks > 0 && --domainIntroTicks == 0) {
            entityData.set(DOMAIN_INTRO_PLAYING, false);
            domainVisualRepairPending = false;
            // The opening timer is the authoritative lifetime boundary. Entity ticking
            // order can differ by one tick when newly spawned entities are queued, so
            // explicitly remove any visual that has not expired itself yet.
            if (level() instanceof ServerLevel serverLevel) {
                discardDomainVisual(serverLevel, domainHandsUuid);
                discardDomainVisual(serverLevel, domainBarrierUuid);
                domainHandsUuid = null;
                domainBarrierUuid = null;
            }
        }
        if (!level().isClientSide && entityData.get(TRANSFORMED) && level() instanceof ServerLevel serverLevel) {
            if (tickCount % 8 == 0) {
                serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(), 3, 0.35, 0.65, 0.35, 0.015);
                serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 5, 0.45, 0.8, 0.45, 0.04);
            }
        }
        if (!level().isClientSide && entityData.get(TRANSFORMING)) {
            if (++transformTicks >= 4) {
                entityData.set(TRANSFORMING, false);
                entityData.set(TRANSFORMED, true);
                entityData.set(TRANSFORM_ANIMATION_PLAYING, true);
                entityData.set(DOMAIN_INTRO_PLAYING, false);
                domainIntroTicks = 0;
                transformAnimationTicks = 110; // 5.5-second Bedrock-derived stage2 animation at 20 TPS.
                transformTicks = 0;
                // The domain pulse is deferred until the transformation animation finishes
                // and the 3-second domain-opening animation actually begins.
            }
        }
    }

    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && entityData.get(TRANSFORMED)) {
            // Keep skill effects from interrupting the stage-two transformation or the
            // domain-opening animation. The model has already switched at 0.2 seconds,
            // but the opening sequence must finish before the transformed skill set is usable.
            if (entityData.get(TRANSFORM_ANIMATION_PLAYING) || entityData.get(DOMAIN_INTRO_PLAYING)) {
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (player.isCrouching()) {
                // Crouch + right-click: Idle Transfiguration burst.
                if (idleSkillCooldown == 0 && level() instanceof ServerLevel serverLevel) {
                    AABB area = getBoundingBox().inflate(6.0, 3.0, 6.0);
                    for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                            candidate -> candidate.isAlive() && candidate != this
                                    && candidate != player && !candidate.isAlliedTo(player))) {
                        target.hurt(damageSources().indirectMagic(this, player), 14.0F);
                        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                        serverLevel.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 0.8,
                                target.getZ(), 16, 0.4, 0.5, 0.4, 0.08);
                        serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0,
                                target.getZ(), 12, 0.35, 0.45, 0.35, 0.12);
                    }
                    serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(), 48, 1.5, 1.0, 1.5, 0.12);
                    serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 64, 1.8, 1.1, 1.8, 0.2);
                    serverLevel.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                            net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.35F);
                    idleSkillCooldown = 160;
                    playSkillAnimation(2, 60);
                }
            } else if (soulStrikeCooldown == 0 && level() instanceof ServerLevel serverLevel) {
                // Right-click: Soul Strike.
                AABB area = getBoundingBox().inflate(4.0, 2.0, 4.0);
                for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                        candidate -> candidate.isAlive() && candidate != this
                                && candidate != player && !candidate.isAlliedTo(player))) {
                    target.hurt(damageSources().indirectMagic(this, player), 8.0F);
                    serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.55,
                            target.getZ(), 10, 0.35, 0.45, 0.35, 0.12);
                    serverLevel.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 0.8,
                            target.getZ(), 8, 0.25, 0.35, 0.25, 0.04);
                }
                serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(), 24, 1.0, 0.8, 1.0, 0.08);
                serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 36, 1.2, 0.9, 1.2, 0.16);
                serverLevel.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.65F);
                soulStrikeCooldown = 30;
                playSkillAnimation(1, 20);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide && !entityData.get(TRANSFORMING) && !entityData.get(TRANSFORMED)
                && player.isCrouching()) {
            // Crouch + right-click is the prototype's domain-expansion trigger.
            // 4 server ticks = 0.2 seconds at 20 TPS.
            transformationOwner = player.getUUID();
            entityData.set(TRANSFORMING, true);
            entityData.set(TRANSFORM_ANIMATION_PLAYING, false);
            entityData.set(DOMAIN_INTRO_PLAYING, false);
            transformAnimationTicks = 0;
            domainIntroTicks = 0;
            transformTicks = 0;
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 90,
                        1.1, 1.2, 1.1, 0.2);
                serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(), 45,
                        0.8, 0.9, 0.8, 0.07);
                serverLevel.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN,
                        net.minecraft.sounds.SoundSource.HOSTILE, 0.9F, 0.8F);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Starts the domain pulse and visual entities when the stage-two animation finishes. */
    private void expandTransfiguredDomain() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        spawnDomainHands(serverLevel, 0);
        spawnDomainBarrier(serverLevel, 0);
        final double radius = 10.0;
        AABB area = getBoundingBox().inflate(radius, 4.0, radius);
        Entity foundOwner = transformationOwner == null ? null : serverLevel.getEntity(transformationOwner);
        final Player owner = foundOwner instanceof Player p ? p : null;
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate.isAlive() && candidate != this
                        && (owner == null || (candidate != owner && !candidate.isAlliedTo(owner))))) {
            if (target.distanceToSqr(this) > radius * radius) continue;
            target.hurt(damageSources().indirectMagic(this, owner), 10.0F);
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            serverLevel.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 0.7,
                    target.getZ(), 20, 0.45, 0.65, 0.45, 0.07);
        }
        // Concentric portal/soul rings communicate the domain opening without requiring a block structure.
        for (int ring = 1; ring <= 4; ring++) {
            double r = radius * ring / 4.0;
            for (int i = 0; i < 48; i++) {
                double angle = (Math.PI * 2.0 * i) / 48.0;
                double x = getX() + Math.cos(angle) * r;
                double z = getZ() + Math.sin(angle) * r;
                serverLevel.sendParticles(ParticleTypes.PORTAL, x, getY() + 0.15 + ring * 0.12, z,
                        1, 0.08, 0.15, 0.08, 0.02);
            }
        }
        serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(), 100,
                2.2, 1.2, 2.2, 0.1);
        serverLevel.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.8F);
    }

    /** Restore missing visuals for an active intro without replaying its damage pulse. */
    private boolean ensureDomainVisuals(ServerLevel serverLevel) {
        int elapsedTicks = Math.max(0, Math.min(60, 60 - domainIntroTicks));
        Entity hands = domainHandsUuid == null ? null : serverLevel.getEntity(domainHandsUuid);
        if (!(hands instanceof MahitoDomainHandsEntity)) spawnDomainHands(serverLevel, elapsedTicks);
        Entity barrier = domainBarrierUuid == null ? null : serverLevel.getEntity(domainBarrierUuid);
        if (!(barrier instanceof MahitoDomainBarrierEntity)) spawnDomainBarrier(serverLevel, elapsedTicks);
        Entity restoredHands = domainHandsUuid == null ? null : serverLevel.getEntity(domainHandsUuid);
        Entity restoredBarrier = domainBarrierUuid == null ? null : serverLevel.getEntity(domainBarrierUuid);
        return restoredHands instanceof MahitoDomainHandsEntity
                && restoredBarrier instanceof MahitoDomainBarrierEntity;
    }

    private void spawnDomainHands(ServerLevel serverLevel, int age) {
        var hands = com.jujutsuenhancement.registry.ModEntities.MAHITO_DOMAIN_HANDS.get().create(serverLevel);
        if (hands == null) return;
        hands.moveTo(getX(), getY() + 0.2D, getZ(), getYRot(), 0.0F);
        hands.restoreAge(age);
        if (serverLevel.addFreshEntity(hands)) domainHandsUuid = hands.getUUID();
    }

    private void spawnDomainBarrier(ServerLevel serverLevel, int age) {
        var barrier = com.jujutsuenhancement.registry.ModEntities.MAHITO_DOMAIN_BARRIER.get().create(serverLevel);
        if (barrier == null) return;
        barrier.moveTo(getX(), getY(), getZ(), getYRot(), 0.0F);
        barrier.restoreAge(age);
        if (serverLevel.addFreshEntity(barrier)) domainBarrierUuid = barrier.getUUID();
    }

    private void playSkillAnimation(int animation, int durationTicks) {
        skillAnimationTicks = durationTicks;
        entityData.set(SKILL_ANIMATION, animation);
    }

    public boolean isTransformed() { return entityData.get(TRANSFORMED); }

    @Override protected void defineSynchedData() {
        entityData.define(TRANSFORMING, false);
        entityData.define(TRANSFORMED, false);
        entityData.define(TRANSFORM_ANIMATION_PLAYING, false);
        entityData.define(DOMAIN_INTRO_PLAYING, false);
        entityData.define(SKILL_ANIMATION, 0);
    }

    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        entityData.set(TRANSFORMING, tag.getBoolean("Transforming"));
        entityData.set(TRANSFORMED, tag.getBoolean("Transformed"));
        transformTicks = tag.getInt("TransformTicks");
        transformAnimationTicks = tag.getInt("TransformAnimationTicks");
        domainIntroTicks = tag.getInt("DomainIntroTicks");
        entityData.set(TRANSFORM_ANIMATION_PLAYING, tag.getBoolean("TransformAnimationPlaying"));
        entityData.set(DOMAIN_INTRO_PLAYING, tag.getBoolean("DomainIntroPlaying"));
        soulStrikeCooldown = tag.getInt("SoulStrikeCooldown");
        idleSkillCooldown = tag.getInt("IdleSkillCooldown");
        skillAnimationTicks = Math.max(0, tag.getInt("SkillAnimationTicks"));
        entityData.set(SKILL_ANIMATION, tag.getInt("SkillAnimation"));
        if (tag.hasUUID("TransformationOwner")) transformationOwner = tag.getUUID("TransformationOwner");
        if (tag.hasUUID("DomainHandsUuid")) domainHandsUuid = tag.getUUID("DomainHandsUuid");
        if (tag.hasUUID("DomainBarrierUuid")) domainBarrierUuid = tag.getUUID("DomainBarrierUuid");

        // Recover safely from saves captured at a timer/flag boundary. Without this
        // normalization, an animation flag with a zero timer could leave skills locked
        // indefinitely after reloading the world.
        transformTicks = Math.max(0, transformTicks);
        transformAnimationTicks = Math.max(0, transformAnimationTicks);
        domainIntroTicks = Math.max(0, domainIntroTicks);
        soulStrikeCooldown = Math.max(0, soulStrikeCooldown);
        idleSkillCooldown = Math.max(0, idleSkillCooldown);
        // Normalize mutually-exclusive phases after loading. A partially written or
        // older save must not leave two animation phases active at once.
        if (entityData.get(TRANSFORMING)) {
            entityData.set(TRANSFORMED, false);
            entityData.set(TRANSFORM_ANIMATION_PLAYING, false);
            entityData.set(DOMAIN_INTRO_PLAYING, false);
            transformAnimationTicks = 0;
            domainIntroTicks = 0;
            if (transformTicks == 0) transformTicks = 1;
        } else if (entityData.get(TRANSFORM_ANIMATION_PLAYING)) {
            entityData.set(TRANSFORMED, true);
            entityData.set(DOMAIN_INTRO_PLAYING, false);
            domainIntroTicks = 0;
            if (transformAnimationTicks == 0) {
                // Let the normal tick transition run so it also spawns the domain visuals
                // and applies the opening pulse. Skipping directly to the intro flag after
                // loading would leave the animation visible without its corresponding effects.
                transformAnimationTicks = 1;
            }
        } else if (entityData.get(DOMAIN_INTRO_PLAYING)) {
            entityData.set(TRANSFORMED, true);
            transformAnimationTicks = 0;
            if (domainIntroTicks == 0) entityData.set(DOMAIN_INTRO_PLAYING, false);
        } else {
            transformAnimationTicks = 0;
            domainIntroTicks = 0;
        }
        int skillAnimation = entityData.get(SKILL_ANIMATION);
        if (skillAnimation < 0 || skillAnimation > 2) entityData.set(SKILL_ANIMATION, 0);
        if (entityData.get(TRANSFORM_ANIMATION_PLAYING) && transformAnimationTicks == 0) {
            transformAnimationTicks = 1;
            entityData.set(DOMAIN_INTRO_PLAYING, false);
            domainIntroTicks = 0;
        }
        if (entityData.get(DOMAIN_INTRO_PLAYING) && domainIntroTicks == 0) {
            entityData.set(DOMAIN_INTRO_PLAYING, false);
        }
        if (skillAnimationTicks == 0) entityData.set(SKILL_ANIMATION, 0);
        // Normalize stale visual references left by older saves or interrupted phase transitions.
        // Do this on the next server tick rather than during NBT loading, because the visual
        // entities may not yet be registered in ServerLevel.getEntity(UUID) during deserialization.
        if (entityData.get(DOMAIN_INTRO_PLAYING) && domainIntroTicks > 0) {
            domainVisualRepairPending = true;
        } else if (domainHandsUuid != null || domainBarrierUuid != null) {
            domainVisualCleanupPending = true;
            domainVisualRepairPending = false;
        }
    }

    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        tag.putBoolean("Transforming", entityData.get(TRANSFORMING));
        tag.putBoolean("Transformed", entityData.get(TRANSFORMED));
        tag.putInt("TransformTicks", transformTicks);
        tag.putInt("TransformAnimationTicks", transformAnimationTicks);
        tag.putInt("DomainIntroTicks", domainIntroTicks);
        tag.putBoolean("TransformAnimationPlaying", entityData.get(TRANSFORM_ANIMATION_PLAYING));
        tag.putBoolean("DomainIntroPlaying", entityData.get(DOMAIN_INTRO_PLAYING));
        tag.putInt("SoulStrikeCooldown", soulStrikeCooldown);
        tag.putInt("IdleSkillCooldown", idleSkillCooldown);
        tag.putInt("SkillAnimationTicks", skillAnimationTicks);
        tag.putInt("SkillAnimation", entityData.get(SKILL_ANIMATION));
        if (transformationOwner != null) tag.putUUID("TransformationOwner", transformationOwner);
        if (domainHandsUuid != null) tag.putUUID("DomainHandsUuid", domainHandsUuid);
        if (domainBarrierUuid != null) tag.putUUID("DomainBarrierUuid", domainBarrierUuid);
    }

    @Override public void remove(Entity.RemovalReason reason) {
        // Domain visuals are separate entities, so remove them with the prototype
        // when it is killed/discarded. Do not do this for chunk/player unloads:
        // those are normal world lifecycle events, not an actual domain cancellation.
        if (reason != Entity.RemovalReason.UNLOADED_TO_CHUNK
                && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER
                && level() instanceof ServerLevel serverLevel) {
            discardDomainVisual(serverLevel, domainHandsUuid);
            discardDomainVisual(serverLevel, domainBarrierUuid);
            domainHandsUuid = null;
            domainBarrierUuid = null;
        }
        super.remove(reason);
    }

    private static void discardDomainVisual(ServerLevel level, UUID uuid) {
        if (uuid == null) return;
        Entity visual = level.getEntity(uuid);
        if (visual instanceof MahitoDomainHandsEntity || visual instanceof MahitoDomainBarrierEntity) {
            visual.discard();
        }
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "mahito", 0, state -> {
            if (entityData.get(TRANSFORM_ANIMATION_PLAYING)) return state.setAndContinue(TRANSFORM);
            if (entityData.get(TRANSFORMING)) return state.setAndContinue(TRANSFORM);
            if (entityData.get(DOMAIN_INTRO_PLAYING)) return state.setAndContinue(DOMAIN_INTRO);
            if (entityData.get(SKILL_ANIMATION) == 1) {
                return state.setAndContinue(RawAnimation.begin().thenPlay("animation.mahito.body_ab1"));
            }
            if (entityData.get(SKILL_ANIMATION) == 2) {
                return state.setAndContinue(RawAnimation.begin().thenPlay("animation.mahito.2s_domain"));
            }
            if (entityData.get(TRANSFORMED)) return state.setAndContinue(STAGE2_IDLE);
            return state.setAndContinue(IDLE);
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
