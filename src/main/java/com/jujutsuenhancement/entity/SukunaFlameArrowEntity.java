package com.jujutsuenhancement.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/** Independent, server-authoritative flame-arrow approximation; not JujutsuCraft's native skill. */
public final class SukunaFlameArrowEntity extends Entity implements ItemSupplier {
    private static final int MAX_AGE_TICKS = 80;
    private static final double SPEED = 1.35D;
    private static final double HIT_RADIUS = 0.8D;
    private static final float DAMAGE = 28.0F;

    private UUID ownerUuid;
    private UUID ignoredEntityUuid;
    private int age;

    public SukunaFlameArrowEntity(EntityType<? extends SukunaFlameArrowEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void launch(Entity owner, Entity ignoredEntity, Vec3 direction) {
        if (owner != null) ownerUuid = owner.getUUID();
        if (ignoredEntity != null) ignoredEntityUuid = ignoredEntity.getUUID();
        Vec3 velocity = direction.normalize().scale(SPEED);
        setDeltaMovement(velocity);
        setYRot((float) (Math.atan2(velocity.z, velocity.x) * 180.0D / Math.PI) - 90.0F);
        setXRot((float) (-Math.atan2(velocity.y,
                Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z)) * 180.0D / Math.PI));
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        Vec3 from = position();
        Vec3 movement = getDeltaMovement();
        Vec3 to = from.add(movement);

        if (level().isClientSide) {
            spawnTrail(level(), from, to, false);
        } else if (level() instanceof ServerLevel serverLevel) {
            spawnTrail(serverLevel, from, to, true);
            HitResult blockHit = level().clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            double maxDistanceSqr = blockHit.getType() == HitResult.Type.BLOCK
                    ? from.distanceToSqr(blockHit.getLocation()) : Double.POSITIVE_INFINITY;
            AABB sweep = getBoundingBox().expandTowards(movement).inflate(HIT_RADIUS);
            Entity owner = ownerUuid == null ? null : serverLevel.getEntity(ownerUuid);
            Entity hitEntity = null;
            double nearestDistanceSqr = maxDistanceSqr;

            for (Entity candidate : level().getEntities(this, sweep,
                    entity -> entity.isAlive() && entity.isPickable()
                            && (ownerUuid == null || !ownerUuid.equals(entity.getUUID()))
                            && (ignoredEntityUuid == null || !ignoredEntityUuid.equals(entity.getUUID())))) {
                if (owner != null && (candidate == owner || owner.isAlliedTo(candidate))) continue;
                AABB expanded = candidate.getBoundingBox().inflate(HIT_RADIUS);
                var intercept = expanded.clip(from, to);
                if (intercept.isEmpty()) continue;
                double distanceSqr = from.distanceToSqr(intercept.get());
                if (distanceSqr < nearestDistanceSqr) {
                    nearestDistanceSqr = distanceSqr;
                    hitEntity = candidate;
                }
            }

            if (hitEntity != null) {
                // Keep the projectile as the direct source and its owner as the attacker
                // so death messages and damage attribution are not anonymous.
                Entity damageOwner = owner != null ? owner : this;
                hitEntity.hurt(damageSources().indirectMagic(this, damageOwner), DAMAGE);
                if (hitEntity instanceof LivingEntity livingTarget) livingTarget.setSecondsOnFire(4);
                impact(serverLevel, hitEntity.position(), owner, hitEntity);
                discard();
                return;
            }
            if (blockHit.getType() == HitResult.Type.BLOCK || age >= MAX_AGE_TICKS) {
                impact(serverLevel, blockHit.getType() == HitResult.Type.BLOCK
                        ? blockHit.getLocation() : position(), owner, null);
                discard();
                return;
            }
        }

        setPos(to.x, to.y, to.z);
        if (age >= MAX_AGE_TICKS) discard();
    }

    private void spawnTrail(Level level, Vec3 from, Vec3 to, boolean server) {
        Vec3 middle = from.add(to).scale(0.5D);
        if (server && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    middle.x, middle.y, middle.z, 8, 0.18D, 0.18D, 0.18D, 0.025D);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    getX(), getY(), getZ(), 5, 0.12D, 0.12D, 0.12D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY(), getZ(), 2, 0.10D, 0.10D, 0.10D, 0.01D);
        } else if (!server) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    middle.x, middle.y, middle.z, 0.0D, 0.0D, 0.0D);
            level.addParticle(ParticleTypes.FLAME,
                    getX(), getY(), getZ(), 0.0D, 0.01D, 0.0D);
        }
    }

    /** Server-authoritative blast around the impact; direct-hit damage is applied separately. */
    private void impact(ServerLevel level, Vec3 at, Entity owner, Entity directHit) {
        final double radius = 3.0D;
        final double radiusSqr = radius * radius;
        final float maxSplashDamage = 12.0F;
        Entity damageOwner = owner != null ? owner : this;
        AABB blastArea = new AABB(at.x, at.y, at.z, at.x, at.y, at.z).inflate(radius);
        for (Entity candidate : level.getEntities(this, blastArea,
                entity -> entity.isAlive() && entity.isPickable())) {
            if (candidate == directHit) continue;
            if (ownerUuid != null && ownerUuid.equals(candidate.getUUID())) continue;
            if (ignoredEntityUuid != null && ignoredEntityUuid.equals(candidate.getUUID())) continue;
            if (owner != null && (candidate == owner || owner.isAlliedTo(candidate))) continue;
            Vec3 targetCenter = candidate.getBoundingBox().getCenter();
            double distanceSqr = targetCenter.distanceToSqr(at);
            if (distanceSqr > radiusSqr) continue;
            // The blast is not a wall-penetrating attack: skip splash targets whose
            // center is occluded by a solid block between the impact and the target.
            HitResult visibility = level.clip(new ClipContext(at, targetCenter,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (visibility.getType() == HitResult.Type.BLOCK
                    && at.distanceToSqr(visibility.getLocation()) + 1.0E-4D < distanceSqr) {
                continue;
            }
            double distance = Math.sqrt(distanceSqr);
            float damage = (float) (maxSplashDamage * Math.max(0.25D, 1.0D - distance / radius));
            if (candidate.hurt(damageSources().indirectMagic(this, damageOwner), damage)) {
                if (candidate instanceof LivingEntity livingTarget) {
                    livingTarget.setSecondsOnFire(3);
                }
                // Radial impulse is applied only after damage is accepted by the target.
                Vec3 impulse = targetCenter.subtract(at);
                Vec3 horizontal = new Vec3(impulse.x, 0.0D, impulse.z);
                if (horizontal.lengthSqr() > 1.0E-6D) {
                    horizontal = horizontal.normalize().scale(0.72D);
                    candidate.push(horizontal.x, Math.max(0.12D, Math.min(0.38D, impulse.y * 0.12D + 0.18D)), horizontal.z);
                    candidate.hurtMarked = true;
                }
            }
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 1.25F, 0.82F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE,
                SoundSource.HOSTILE, 0.85F, 0.72F);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z,
                24, 0.35D, 0.35D, 0.35D, 0.06D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z,
                12, 0.25D, 0.25D, 0.25D, 0.035D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z,
                10, 0.30D, 0.30D, 0.30D, 0.02D);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.FIRE_CHARGE);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        if (tag.hasUUID("Owner")) ownerUuid = tag.getUUID("Owner");
        if (tag.hasUUID("IgnoredEntity")) ignoredEntityUuid = tag.getUUID("IgnoredEntity");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        if (ignoredEntityUuid != null) tag.putUUID("IgnoredEntity", ignoredEntityUuid);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
