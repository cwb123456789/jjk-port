package com.jujutsuenhancement.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public final class HeianSukunaEntity extends Entity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final EntityDataAccessor<Integer> SKILL_ANIMATION =
            SynchedEntityData.defineId(HeianSukunaEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.heian_sukuna.idle");
    private static final RawAnimation[] ATTACKS = new RawAnimation[] {
            RawAnimation.begin().thenPlay("animation.heian_sukuna.attack1"),
            RawAnimation.begin().thenPlay("animation.heian_sukuna.attack2"),
            RawAnimation.begin().thenPlay("animation.heian_sukuna.attack3"),
            RawAnimation.begin().thenPlay("animation.heian_sukuna.attack4"),
            RawAnimation.begin().thenPlay("animation.heian_sukuna.attack5")
    };
    // Imported attack clips are 1.25 seconds long at 20 game ticks per second.
    private static final int ATTACK_ANIMATION_TICKS = 25;
    private static final int FUGA_CHARGE_TICKS = 55;
    private static final int FUGA_RELEASE_TICKS = 30;
    // Prevent an abandoned charged state from locking the entity indefinitely.
    private static final int FUGA_READY_WINDOW_TICKS = 100;
    private static final RawAnimation FUGA_RELEASE = RawAnimation.begin().thenPlay("animation.fuga_release");
    private static final RawAnimation FUGA_ACTIVATE = RawAnimation.begin().thenPlay("animation.fuga_activate");
    private static final RawAnimation FUGA_HOLD = RawAnimation.begin().thenLoop("animation.fuga_activate_hold");
    private int skillAnimationTicks;
    private int fugaChargeTicksRemaining;
    private int fugaReadyTicksRemaining;
    private UUID fugaChargerUuid;
    // Cooldowns and the combo sequence belong to this entity, not to the interacting player.
    private long nextBasicAttackGameTime;
    private long nextDomainGameTime;
    private int attackSequence;

    public boolean canUseBasicAttack(long gameTime) {
        return gameTime >= nextBasicAttackGameTime;
    }

    public boolean canUseDomain(long gameTime) {
        return gameTime >= nextDomainGameTime;
    }

    public boolean isFugaCharging() {
        return fugaChargeTicksRemaining > 0 || entityData.get(SKILL_ANIMATION) == 6 || entityData.get(SKILL_ANIMATION) == 7;
    }

    public boolean isFugaReady() {
        return fugaChargeTicksRemaining == 0 && entityData.get(SKILL_ANIMATION) == 7;
    }

    public boolean isFugaCharger(UUID playerUuid) {
        return fugaChargerUuid != null && fugaChargerUuid.equals(playerUuid);
    }

    public void startFugaCharge(UUID playerUuid) {
        if (level().isClientSide || playerUuid == null || isFugaCharging()) return;
        fugaChargerUuid = playerUuid;
        fugaChargeTicksRemaining = FUGA_CHARGE_TICKS;
        fugaReadyTicksRemaining = 0;
        skillAnimationTicks = 0;
        entityData.set(SKILL_ANIMATION, 6);
    }

    public void cancelFugaCharge() {
        fugaChargerUuid = null;
        fugaChargeTicksRemaining = 0;
        fugaReadyTicksRemaining = 0;
        entityData.set(SKILL_ANIMATION, 0);
    }

    /** Commit the Fuga release only after the server accepted the projectile. */
    public void commitFugaRelease(long gameTime, int cooldownTicks) {
        cancelFugaCharge();
        nextBasicAttackGameTime = gameTime + Math.max(0, cooldownTicks);
        entityData.set(SKILL_ANIMATION, 8);
        skillAnimationTicks = FUGA_RELEASE_TICKS;
    }

    /** Commit a basic attack only after the server accepted at least one projectile. */
    public int commitBasicAttack(long gameTime, int cooldownTicks) {
        cancelFugaCharge();
        int sequence = attackSequence;
        attackSequence = (attackSequence + 1) % ATTACKS.length;
        nextBasicAttackGameTime = gameTime + Math.max(0, cooldownTicks);
        playAttackAnimation(sequence);
        return sequence;
    }

    public void commitDomain(long gameTime, int cooldownTicks) {
        nextDomainGameTime = gameTime + Math.max(0, cooldownTicks);
    }

    public HeianSukunaEntity(EntityType<? extends HeianSukunaEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }
    @Override public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide) {
            // A charge belongs to the player who initiated it. Do not let a disconnected,
            // dead, or dimension-switched charger leave a stale charge/ready state behind.
            if ((fugaChargeTicksRemaining > 0 || fugaReadyTicksRemaining > 0)
                    && fugaChargerUuid != null && level() instanceof ServerLevel serverLevel) {
                ServerPlayer charger = serverLevel.getServer().getPlayerList().getPlayer(fugaChargerUuid);
                if (charger == null || !charger.isAlive() || charger.level() != level()) {
                    cancelFugaCharge();
                }
            }
            if (fugaChargeTicksRemaining > 0 && --fugaChargeTicksRemaining == 0) {
                entityData.set(SKILL_ANIMATION, 7);
                fugaReadyTicksRemaining = FUGA_READY_WINDOW_TICKS;
            } else if (fugaReadyTicksRemaining > 0 && --fugaReadyTicksRemaining == 0) {
                // Expire abandoned readiness so another player can start a new charge.
                cancelFugaCharge();
            } else if (skillAnimationTicks > 0 && --skillAnimationTicks == 0
                    && (entityData.get(SKILL_ANIMATION) == 8
                    || (entityData.get(SKILL_ANIMATION) >= 1 && entityData.get(SKILL_ANIMATION) <= ATTACKS.length))) {
                entityData.set(SKILL_ANIMATION, 0);
            }
        }
    }
    @Override protected void defineSynchedData() {
        entityData.define(SKILL_ANIMATION, 0);
    }
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        // Animation is transient, but cooldowns and combo progress are gameplay state.
        // Persisting them prevents unloading/reloading the entity from resetting its cooldowns.
        entityData.set(SKILL_ANIMATION, 0);
        skillAnimationTicks = 0;
        fugaChargeTicksRemaining = 0;
        fugaReadyTicksRemaining = 0;
        fugaChargerUuid = null;
        nextBasicAttackGameTime = Math.max(0L, tag.getLong("NextBasicAttackGameTime"));
        nextDomainGameTime = Math.max(0L, tag.getLong("NextDomainGameTime"));
        attackSequence = Math.floorMod(tag.getInt("AttackSequence"), ATTACKS.length);
    }
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        tag.putLong("NextBasicAttackGameTime", nextBasicAttackGameTime);
        tag.putLong("NextDomainGameTime", nextDomainGameTime);
        tag.putInt("AttackSequence", Math.floorMod(attackSequence, ATTACKS.length));
    }

    /** Starts one of the imported Bedrock-derived attack clips on both client and server. */
    public void playAttackAnimation(int attackIndex) {
        if (level().isClientSide) return;
        int normalized = Math.floorMod(attackIndex, ATTACKS.length) + 1;
        entityData.set(SKILL_ANIMATION, normalized);
        skillAnimationTicks = ATTACK_ANIMATION_TICKS;
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "heian_sukuna", 0, state -> {
            int attack = entityData.get(SKILL_ANIMATION);
            if (attack == 6) return state.setAndContinue(FUGA_ACTIVATE);
            if (attack == 7) return state.setAndContinue(FUGA_HOLD);
            if (attack == 8) return state.setAndContinue(FUGA_RELEASE);
            if (attack >= 1 && attack <= ATTACKS.length) {
                return state.setAndContinue(ATTACKS[attack - 1]);
            }
            return state.setAndContinue(IDLE);
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
