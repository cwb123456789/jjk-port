package com.jujutsuenhancement.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Hollow Purple projectile, ported from the Bedrock "Jujutsu Awakening" functions
 * (limitless/hollow_purple, 200hollow_purple, imaginary_purple and the script-driven
 * ds:shootprojectile motion).
 *
 * Normal   : 4 blocks/tick for 98 ticks. Every tick 40 damage within 7 blocks and 5 damage within 15.
 * 200%     : 1.65 blocks/tick for 12 ticks, then 5 blocks/tick up to 58 ticks total.
 *            Every tick 300 damage within 17 blocks (+10 bypassing invulnerability).
 * Unlimited: 4 blocks/tick for 40 ticks. Every tick 200 damage within 4 blocks, and every living
 *            thing within 64 blocks is frozen (heavy slowness) while it exists.
 * All three erase terrain along their path when mob griefing is on (the "griefable" tag).
 */
public final class HollowPurpleEntity extends Entity {
    private enum Mode { NORMAL, CHARGED, UNLIMITED }

    private static final DustParticleOptions PURPLE_CORE = new DustParticleOptions(new Vector3f(0.42F, 0.08F, 0.95F), 1.8F);
    private static final DustParticleOptions BLUE_EDGE = new DustParticleOptions(new Vector3f(0.12F, 0.38F, 1.0F), 1.15F);
    private static final DustParticleOptions WHITE_CORE = new DustParticleOptions(new Vector3f(0.92F, 0.88F, 1.0F), 0.8F);

    private UUID ownerUuid;
    private int age;
    private Mode mode = Mode.NORMAL;

    public HollowPurpleEntity(EntityType<? extends HollowPurpleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setUnlimited() { mode = Mode.UNLIMITED; }

    public void setCharged200Percent() { mode = Mode.CHARGED; }

    public void launch(Entity owner, Vec3 direction) {
        this.ownerUuid = owner == null ? null : owner.getUUID();
        Vec3 dir = direction.normalize();
        this.setDeltaMovement(dir);
        this.setYRot((float) (Math.atan2(dir.z, dir.x) * 180.0D / Math.PI) - 90.0F);
        this.setXRot((float) (-Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * 180.0D / Math.PI));
    }

    private int lifetime() {
        return switch (mode) { case NORMAL -> 98; case CHARGED -> 58; case UNLIMITED -> 40; };
    }

    private double speedNow() {
        return switch (mode) {
            case NORMAL, UNLIMITED -> 4.0D;
            case CHARGED -> age <= 12 ? 1.65D : 5.0D;
        };
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        Vec3 dir = getDeltaMovement();
        if (dir.lengthSqr() < 1.0E-6D) dir = getLookAngle();
        dir = dir.normalize();
        setDeltaMovement(dir);

        if (level().isClientSide) {
            clientParticles();
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        if (age > lifetime() || !server.hasChunkAt(blockPosition())) {
            finish(server);
            return;
        }
        Vec3 from = position();
        Vec3 to = from.add(dir.scale(speedNow()));
        setPos(to.x, to.y, to.z);

        Entity owner = ownerUuid == null ? null : server.getEntity(ownerUuid);
        switch (mode) {
            case NORMAL -> {
                hurtWithin(server, owner, 15.0D, 5.0F);
                hurtWithin(server, owner, 7.0D, 40.0F);
                destroyTerrain(server, 3);
            }
            case CHARGED -> {
                hurtWithin(server, owner, 17.0D, 300.0F);
                destroyTerrain(server, 7);
            }
            case UNLIMITED -> {
                freezeEverything(server, owner);
                hurtWithin(server, owner, 4.0D, 200.0F);
                destroyTerrain(server, 2);
            }
        }
        serverParticles(server, from, to);
    }

    private void hurtWithin(ServerLevel server, Entity owner, double radius, float amount) {
        AABB box = getBoundingBox().inflate(radius);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != owner
                && (owner == null || !owner.isAlliedTo(e))
                && !(e instanceof GojoAwakenedEntity) && e.distanceToSqr(this) <= radius * radius)) {
            // Bedrock uses "damage ... override", which ignores invulnerability frames.
            target.invulnerableTime = 0;
            DamageSource source = owner != null ? damageSources().indirectMagic(this, owner) : damageSources().magic();
            target.hurt(source, amount);
        }
    }

    /** imaginary_purple: Stun/Camera 20, slowness 30 and slow falling on everything within 64 blocks. */
    private void freezeEverything(ServerLevel server, Entity owner) {
        double r = 64.0D;
        AABB box = getBoundingBox().inflate(r);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != owner
                && !(e instanceof GojoAwakenedEntity) && e.distanceToSqr(this) <= r * r)) {
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 10, false, false));
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOW_FALLING, 20, 0, false, false));
        }
    }

    /** The "griefable" spheres: erase blocks around the projectile when mob griefing is allowed. */
    private void destroyTerrain(ServerLevel server, int radius) {
        if (!server.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        BlockPos center = blockPosition().above(2);
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (pos.getY() < server.getMinBuildHeight() || pos.getY() >= server.getMaxBuildHeight()) continue;
                    BlockState state = server.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(server, pos) < 0.0F) continue;
                    server.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
    }

    private void finish(ServerLevel server) {
        server.sendParticles(PURPLE_CORE, getX(), getY(), getZ(), 40, 1.5D, 1.5D, 1.5D, 0.12D);
        server.sendParticles(WHITE_CORE, getX(), getY(), getZ(), 12, 0.6D, 0.6D, 0.6D, 0.04D);
        discard();
    }

    private void serverParticles(ServerLevel server, Vec3 from, Vec3 to) {
        double size = switch (mode) { case NORMAL -> 1.2D; case CHARGED -> 3.0D; case UNLIMITED -> 1.0D; };
        int segments = 4;
        for (int i = 0; i < segments; i++) {
            double t = (i + 1.0D) / (segments + 1.0D);
            double px = from.x + (to.x - from.x) * t;
            double py = from.y + (to.y - from.y) * t;
            double pz = from.z + (to.z - from.z) * t;
            server.sendParticles(PURPLE_CORE, px, py, pz, 5, size * 0.5D, size * 0.5D, size * 0.5D, 0.01D);
            server.sendParticles(BLUE_EDGE, px, py, pz, 3, size * 0.6D, size * 0.6D, size * 0.6D, 0.02D);
        }
        server.sendParticles(WHITE_CORE, to.x, to.y, to.z, 3, size * 0.2D, size * 0.2D, size * 0.2D, 0.0D);
    }

    private void clientParticles() {
        double size = switch (mode) { case NORMAL -> 0.9D; case CHARGED -> 2.5D; case UNLIMITED -> 0.8D; };
        double phase = age * 0.42D;
        for (int i = 0; i < 3; i++) {
            double angle = phase + (Math.PI * 2.0D * i / 3.0D);
            double radius = size * (0.5D + 0.08D * Math.sin(phase * 0.7D + i));
            level().addParticle(PURPLE_CORE, getX() + Math.cos(angle) * radius, getY() + Math.sin(angle * 1.35D) * radius * 0.7D,
                    getZ() + Math.sin(angle) * radius, 0.0D, 0.015D, 0.0D);
            level().addParticle(BLUE_EDGE, getX() + (random.nextDouble() - 0.5D) * size, getY() + (random.nextDouble() - 0.5D) * size,
                    getZ() + (random.nextDouble() - 0.5D) * size, 0.0D, 0.01D, 0.0D);
        }
        level().addParticle(WHITE_CORE, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
    }

    @Override protected void defineSynchedData() {}

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerUuid = tag.getUUID("Owner");
        age = tag.getInt("Age");
        mode = Mode.values()[Math.floorMod(tag.getInt("Mode"), Mode.values().length)];
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        tag.putInt("Age", age);
        tag.putInt("Mode", mode.ordinal());
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
