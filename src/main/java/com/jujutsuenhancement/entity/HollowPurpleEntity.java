package com.jujutsuenhancement.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/** Server-authoritative first-pass Hollow Purple projectile prototype. */
public final class HollowPurpleEntity extends Entity {
    private UUID ownerUuid;
    private int age;
    private final Set<UUID> hitEntities = new HashSet<>();
    private double speed = 1.35D;
    private float damage = 40.0F;
    private double hitRadius = 2.0D;
    private static final DustParticleOptions PURPLE_CORE = new DustParticleOptions(new Vector3f(0.42F, 0.08F, 0.95F), 1.8F);
    private static final DustParticleOptions BLUE_EDGE = new DustParticleOptions(new Vector3f(0.12F, 0.38F, 1.0F), 1.15F);
    private static final DustParticleOptions WHITE_CORE = new DustParticleOptions(new Vector3f(0.92F, 0.88F, 1.0F), 0.8F);

    public HollowPurpleEntity(EntityType<? extends HollowPurpleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Unlimited (imaginary) Purple: stationary charge that detonates in a large sphere. */
    private boolean unlimited = false;
    private static final double UNLIMITED_RADIUS = 24.0D;
    private static final int UNLIMITED_CHARGE_TICKS = 24;

    public void setUnlimited() {
        this.unlimited = true;
        this.damage = 300.0F;
    }

    public void setCharged200Percent() {
        this.speed = 1.8D;
        this.damage = 80.0F;
        this.hitRadius = 3.0D;
    }

    public void launch(Entity owner, Vec3 direction) {
        this.ownerUuid = owner == null ? null : owner.getUUID();
        Vec3 velocity = direction.normalize().scale(speed);
        this.setDeltaMovement(velocity);
        this.setYRot((float)(Math.atan2(velocity.z, velocity.x) * 180.0D / Math.PI) - 90.0F);
        this.setXRot((float)(-Math.atan2(velocity.y, Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z)) * 180.0D / Math.PI));
    }

    @Override public void tick() {
        super.tick();
        age++;
        if (unlimited) {
            tickUnlimited();
            return;
        }
        Vec3 from = this.position();
        Vec3 movement = this.getDeltaMovement();
        Vec3 to = from.add(movement);
        if (this.level().isClientSide) {
            // Client-side layered core and orbiting motes; these are a Forge approximation,
            // not a direct execution of Bedrock's particle controller format.
            double phase = age * 0.42D;
            for (int i = 0; i < 3; i++) {
                double angle = phase + (Math.PI * 2.0D * i / 3.0D);
                double radius = 0.48D + 0.08D * Math.sin(phase * 0.7D + i);
                this.level().addParticle(PURPLE_CORE,
                    getX() + Math.cos(angle) * radius,
                    getY() + Math.sin(angle * 1.35D) * radius * 0.7D,
                    getZ() + Math.sin(angle) * radius,
                    0.0D, 0.015D, 0.0D);
                this.level().addParticle(BLUE_EDGE,
                    getX() + (this.random.nextDouble() - 0.5D) * 0.72D,
                    getY() + (this.random.nextDouble() - 0.5D) * 0.72D,
                    getZ() + (this.random.nextDouble() - 0.5D) * 0.72D,
                    0.0D, 0.01D, 0.0D);
            }
            this.level().addParticle(WHITE_CORE, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            for (int i = 0; i < 2; i++) {
                this.level().addParticle(ParticleTypes.PORTAL,
                    this.getX() + (this.random.nextDouble() - 0.5D) * 1.0D,
                    this.getY() + (this.random.nextDouble() - 0.5D) * 1.0D,
                    this.getZ() + (this.random.nextDouble() - 0.5D) * 1.0D,
                    (this.random.nextDouble() - 0.5D) * 0.08D, 0.015D,
                    (this.random.nextDouble() - 0.5D) * 0.08D);
            }
        } else if (this.level() instanceof ServerLevel server) {
            // Recreate the Bedrock pack's layered Hollow Purple silhouette with vanilla
            // particle primitives: a bright core, a blue/purple shell, and a segmented trail.
            // The 200% variant gets a wider and denser trail; this is an approximation,
            // because Bedrock's custom particle JSON cannot run directly on Forge.
            boolean charged = damage >= 80.0F;
            int trailSegments = charged ? 6 : 4;
            for (int i = 0; i < trailSegments; i++) {
                double t = (i + 1.0D) / (trailSegments + 1.0D);
                double px = from.x + (to.x - from.x) * t;
                double py = from.y + (to.y - from.y) * t;
                double pz = from.z + (to.z - from.z) * t;
                server.sendParticles(PURPLE_CORE, px, py, pz, charged ? 3 : 2,
                        charged ? 0.34D : 0.20D, charged ? 0.34D : 0.20D,
                        charged ? 0.34D : 0.20D, 0.008D);
                server.sendParticles(BLUE_EDGE, px, py, pz, 1,
                        charged ? 0.40D : 0.24D, charged ? 0.40D : 0.24D,
                        charged ? 0.40D : 0.24D, 0.012D);
            }
            server.sendParticles(PURPLE_CORE, getX(), getY(), getZ(), charged ? 10 : 6,
                    charged ? 0.36D : 0.24D, charged ? 0.36D : 0.24D,
                    charged ? 0.36D : 0.24D, 0.012D);
            server.sendParticles(BLUE_EDGE, getX(), getY(), getZ(), charged ? 8 : 4,
                    charged ? 0.48D : 0.30D, charged ? 0.48D : 0.30D,
                    charged ? 0.48D : 0.30D, 0.018D);
            server.sendParticles(WHITE_CORE, getX(), getY(), getZ(), charged ? 3 : 1,
                    0.10D, 0.10D, 0.10D, 0.0D);
            HitResult hit = this.level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            // Sweep the projectile's path rather than damaging every entity in a large box.
            // This prevents the purple from hitting targets far to the side or through a wall.
            AABB area = this.getBoundingBox().expandTowards(movement).inflate(hitRadius);
            Entity owner = ownerUuid == null ? null : server.getEntity(ownerUuid);
            double segmentLengthSqr = movement.lengthSqr();
            double blockDistanceSqr = hit.getType() == HitResult.Type.BLOCK
                    ? from.distanceToSqr(hit.getLocation()) : Double.POSITIVE_INFINITY;
            for (Entity target : this.level().getEntities(this, area, e -> e.isAlive() && e.isPickable()
                    && (ownerUuid == null || !ownerUuid.equals(e.getUUID()))
                    && !hitEntities.contains(e.getUUID()))) {
                // A projectile should not damage the shooter's allies while sweeping through a crowd.
                // Keep the UUID check above as well, because not every entity reports itself as allied.
                if (owner != null && owner.isAlliedTo(target)) continue;
                Vec3 toTarget = target.getBoundingBox().getCenter().subtract(from);
                double t = segmentLengthSqr < 1.0E-9D ? 0.0D
                        : Math.max(0.0D, Math.min(1.0D, toTarget.dot(movement) / segmentLengthSqr));
                Vec3 closest = from.add(movement.scale(t));
                // Do not apply the hit to an entity beyond the first blocking block.
                if (closest.distanceToSqr(target.getBoundingBox().getCenter()) > hitRadius * hitRadius) continue;
                if (closest.distanceToSqr(from) > blockDistanceSqr) continue;
                hitEntities.add(target.getUUID());
                // Preserve both projectile and living owner in the damage source so kill
                // attribution, combat logs, and owner-based damage hooks can identify Gojo.
                // If the owner is temporarily unloaded, keep a safe generic magic fallback.
                if (owner != null) {
                    target.hurt(this.damageSources().indirectMagic(this, owner), damage);
                } else {
                    target.hurt(this.damageSources().magic(), damage);
                }
            }
            if (hit.getType() == HitResult.Type.BLOCK || age >= 100) {
                // Put the impact burst at the actual ray-traced block contact, not at
                // the projectile's previous tick position. This keeps the visual burst
                // on the near side of walls and avoids appearing to pass through them.
                Vec3 impact = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to;
                this.setPos(impact.x, impact.y, impact.z);
                server.sendParticles(PURPLE_CORE, impact.x, impact.y, impact.z, 36, 1.0D, 1.0D, 1.0D, 0.12D);
                server.sendParticles(BLUE_EDGE, impact.x, impact.y, impact.z, 24, 0.9D, 0.9D, 0.9D, 0.08D);
                server.sendParticles(WHITE_CORE, impact.x, impact.y, impact.z, 12, 0.6D, 0.6D, 0.6D, 0.04D);
                server.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 3, 0.5D, 0.5D, 0.5D, 0.02D);
                server.sendParticles(ParticleTypes.PORTAL, impact.x, impact.y, impact.z, 24, 0.8D, 0.8D, 0.8D, 0.18D);
                discard();
                return;
            }
        }
        this.setPos(to.x, to.y, to.z);
        if (age >= 100) discard();
    }

    private void tickUnlimited() {
        this.setDeltaMovement(Vec3.ZERO);
        double progress = Math.min(1.0D, age / (double) UNLIMITED_CHARGE_TICKS);
        if (this.level().isClientSide) {
            double r = 0.6D + progress * 2.4D;
            for (int i = 0; i < 6; i++) {
                double a = this.random.nextDouble() * Math.PI * 2.0D;
                double b = (this.random.nextDouble() - 0.5D) * Math.PI;
                this.level().addParticle(PURPLE_CORE,
                        getX() + Math.cos(a) * Math.cos(b) * r,
                        getY() + Math.sin(b) * r,
                        getZ() + Math.sin(a) * Math.cos(b) * r, 0.0D, 0.0D, 0.0D);
            }
            this.level().addParticle(WHITE_CORE, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            return;
        }
        if (!(this.level() instanceof ServerLevel server)) return;
        if (age < UNLIMITED_CHARGE_TICKS) {
            double r = 0.6D + progress * 2.4D;
            server.sendParticles(PURPLE_CORE, getX(), getY(), getZ(), 14, r, r, r, 0.02D);
            server.sendParticles(BLUE_EDGE, getX(), getY(), getZ(), 8, r, r, r, 0.03D);
            if (age % 6 == 0) {
                server.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.5F + (float) progress);
            }
            return;
        }
        Entity owner = ownerUuid == null ? null : server.getEntity(ownerUuid);
        AABB area = new AABB(getX() - UNLIMITED_RADIUS, getY() - UNLIMITED_RADIUS, getZ() - UNLIMITED_RADIUS,
                getX() + UNLIMITED_RADIUS, getY() + UNLIMITED_RADIUS, getZ() + UNLIMITED_RADIUS);
        for (Entity target : server.getEntities(this, area, e -> e.isAlive() && e != this)) {
            if (!(target instanceof net.minecraft.world.entity.LivingEntity)) continue;
            if (owner != null && (target == owner || owner.isAlliedTo(target))) continue;
            if (target.distanceToSqr(this) > UNLIMITED_RADIUS * UNLIMITED_RADIUS) continue;
            if (owner != null) {
                target.hurt(this.damageSources().indirectMagic(this, owner), damage);
            } else {
                target.hurt(this.damageSources().magic(), damage);
            }
        }
        // Block damage follows the mobGriefing rule via ExplosionInteraction.MOB.
        server.explode(owner, getX(), getY(), getZ(), 8.0F, Level.ExplosionInteraction.MOB);
        server.sendParticles(PURPLE_CORE, getX(), getY(), getZ(), 220, 8.0D, 8.0D, 8.0D, 0.25D);
        server.sendParticles(BLUE_EDGE, getX(), getY(), getZ(), 160, 8.0D, 8.0D, 8.0D, 0.2D);
        server.sendParticles(WHITE_CORE, getX(), getY(), getZ(), 80, 4.0D, 4.0D, 4.0D, 0.1D);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 3, 3.0D, 3.0D, 3.0D, 0.0D);
        server.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.5F);
        discard();
    }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerUuid = tag.getUUID("Owner");
        age = tag.getInt("Age");
        speed = tag.contains("Speed") ? tag.getDouble("Speed") : 1.35D;
        damage = tag.contains("Damage") ? tag.getFloat("Damage") : 40.0F;
        hitRadius = tag.contains("HitRadius") ? tag.getDouble("HitRadius") : 2.0D;
        unlimited = tag.getBoolean("Unlimited");
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        tag.putInt("Age", age);
        tag.putDouble("Speed", speed);
        tag.putFloat("Damage", damage);
        tag.putDouble("HitRadius", hitRadius);
        tag.putBoolean("Unlimited", unlimited);
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
