package com.jujutsuenhancement.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/** Server-authoritative first playable version of Malevolent Shrine. */
public final class DomainShrineEntity extends Entity implements GeoEntity {
    private static final int LIFETIME_TICKS = 400;
    private static final int SLASH_INTERVAL_TICKS = 10;
    private static final double RADIUS = 25.0D;
    private static final float SLASH_DAMAGE = 12.0F;
    private static final RawAnimation FORM = RawAnimation.begin().thenLoop("animation.malevolent_shrine.form");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID ownerUuid;
    private int age;
    /** Used by the domain clash: only the geometry is shown, nothing is damaged and it never expires on its own. */
    private boolean visualOnly;

    public DomainShrineEntity(EntityType<? extends DomainShrineEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setVisualOnly() {
        this.visualOnly = true;
    }

    public void setOwner(Entity owner) {
        this.ownerUuid = owner == null ? null : owner.getUUID();
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level().isClientSide || visualOnly) return;

        age++;
        if (age >= LIFETIME_TICKS) {
            discard();
            return;
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            if (age % 5 == 0) {
                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        getX() + (random.nextDouble() - 0.5D) * RADIUS * 1.6D,
                        getY() + 0.5D + random.nextDouble() * 2.0D,
                        getZ() + (random.nextDouble() - 0.5D) * RADIUS * 1.6D,
                        1, 0.1D, 0.1D, 0.1D, 0.0D);
            }
            if (age % SLASH_INTERVAL_TICKS == 0) applySlashes(serverLevel);
        }
    }

    private void applySlashes(ServerLevel serverLevel) {
        Entity owner = ownerUuid == null ? null : serverLevel.getEntity(ownerUuid);
        // Fail closed if the owner cannot be resolved. Treating a missing owner as hostile to
        // everyone would turn an unloaded/disconnected owner's domain into global friendly fire.
        if (owner == null || !owner.isAlive() || owner.level() != serverLevel) return;
        AABB area = new AABB(getX() - RADIUS, getY() - 12.0D, getZ() - RADIUS,
                getX() + RADIUS, getY() + 12.0D, getZ() + RADIUS);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate.isAlive() && candidate != owner && !candidate.isAlliedTo(owner))) {
            // Circular radius check avoids damaging targets in the square's corners.
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
            target.hurt(serverLevel.damageSources().magic(), SLASH_DAMAGE);
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
                    8, target.getBbWidth() * 0.3D, target.getBbHeight() * 0.25D,
                    target.getBbWidth() * 0.3D, 0.05D);
        }
    }

    @Override protected void defineSynchedData() {}

    @Override
    public boolean shouldBeSaved() {
        return !visualOnly && super.shouldBeSaved();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("DomainAge");
        if (tag.hasUUID("DomainOwner")) ownerUuid = tag.getUUID("DomainOwner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("DomainAge", age);
        if (ownerUuid != null) tag.putUUID("DomainOwner", ownerUuid);
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "shrine_form", 0,
                state -> state.setAndContinue(FORM)));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
