package com.jujutsuenhancement.entity;

import com.jujutsuenhancement.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Sukuna's Dismantle (解) slashes, ported from the Bedrock addon's {@code sukuna/attacks/dismantle*.mcfunction},
 * {@code dissect*.mcfunction}, {@code wide_dismantle.mcfunction} and {@code dismantledown.mcfunction}.
 * <p>
 * The addon's {@code Deleter} score counts down from 25 (20 for the downward slashes). Numbers taken from the functions:
 * <ul>
 *   <li>QUICK  (ds:dismantle): shot at 4.5 blocks/tick, hits everything within 4 blocks (feet position), 25 damage, Stun 25, Hit 5, ends on the first hit;</li>
 *   <li>DISSECT (解·切): moves 4 blocks/tick while Deleter &gt;= 11, hits within 5 blocks, 30 damage, Stun 35, Hit 5; after
 *       meeting a block in its first 8 ticks it keeps going for 5 more;</li>
 *   <li>WIDE (后撤宽斩): moves 7 blocks/tick while Deleter &gt;= 11, five hit spheres of radius 3.99 at 0/+-4/+-8 to the side, 30 damage, Stun 35;</li>
 *   <li>DOWN (空中解): falls 6 blocks/tick until it is inside a block, then everything within 10 blocks takes 32 damage, Stun 35, Hit 10.</li>
 * </ul>
 * Java's invulnerability frames make a target take one hit per slash, which is also what the addon's Frames tag does.
 */
public final class DismantleSlashEntity extends Entity {
    public static final int QUICK = 0, DISSECT = 1, WIDE = 2, DOWN = 3;

    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(DismantleSlashEntity.class, EntityDataSerializers.INT);

    private UUID ownerUuid;
    private Vec3 dir = new Vec3(0, 0, 1);
    private int age;
    /** Deleter at which the movement stops (the addon sets it to 16 when the slash meets a block early). */
    private int deleter = 25;
    private final Set<UUID> hit = new HashSet<>();

    public DismantleSlashEntity(EntityType<? extends DismantleSlashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Creates a slash at {@code pos} flying along {@code dir} (unit vector). */
    public static DismantleSlashEntity launch(ServerLevel level, Entity owner, int kind, Vec3 pos, Vec3 dir) {
        DismantleSlashEntity e = ModEntities.DISMANTLE_SLASH.get().create(level);
        if (e == null) return null;
        e.ownerUuid = owner == null ? null : owner.getUUID();
        e.dir = dir.normalize();
        e.entityData.set(KIND, kind);
        e.deleter = kind == DOWN ? 20 : 25;
        float yaw = (float) Math.toDegrees(Math.atan2(-e.dir.x, e.dir.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(e.dir.y, Math.sqrt(e.dir.x * e.dir.x + e.dir.z * e.dir.z)));
        e.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
        level.addFreshEntity(e);
        return e;
    }

    public int getKind() {
        return entityData.get(KIND);
    }

    /** Ticks since it was created, as counted by the server; the client uses its own tickCount. */
    public int getAge() {
        return age;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) return;
        Entity owner = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        int kind = getKind();
        int left = deleter - age; // the addon's Deleter score
        switch (kind) {
            case QUICK -> tickQuick(sl, owner, left);
            case DISSECT -> tickLine(sl, owner, left, 4.0D, 5.0D, 30.0F, 35, false);
            case WIDE -> tickLine(sl, owner, left, 7.0D, 3.99D, 30.0F, 35, true);
            default -> tickDown(sl, owner);
        }
        age++;
    }

    // ------------------------------------------------------------------ QUICK

    private void tickQuick(ServerLevel sl, Entity owner, int left) {
        if (left <= 0) {
            discard();
            return;
        }
        Vec3 from = position();
        Vec3 to = from.add(dir.scale(4.5D));
        HitResult block = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = block.getType() == HitResult.Type.BLOCK;
        Vec3 end = blocked ? block.getLocation() : to;
        // everything within 4 blocks (feet position) of the slash's path this tick
        boolean landed = false;
        Vec3 landAt = end;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(4.5D),
                e -> isTarget(sl, e))) {
            double d = distToSegment(t.position(), from, end);
            if (d > 4.0D) continue;
            landed = true;
        }
        if (landed) {
            // the addon applies the damage to everything within 4 blocks of where the slash is when it lands
            Vec3 at = end;
            double best = Double.MAX_VALUE;
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(4.5D),
                    e -> isTarget(sl, e))) {
                double d = distToSegment(t.position(), from, end);
                if (d <= 4.0D && d < best) {
                    best = d;
                    Vec3 p = t.position();
                    double s = clamp01(p.subtract(from).dot(end.subtract(from)) / Math.max(1.0E-6D, end.subtract(from).lengthSqr()));
                    at = from.add(end.subtract(from).scale(s));
                }
            }
            landAt = at;
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(landAt, landAt).inflate(4.0D),
                    e -> isTarget(sl, e))) {
                if (t.position().distanceTo(landAt) <= 4.0D) strike(sl, owner, t, 25.0F, 25, 5, dir);
            }
            BedrockFxEntity.play(sl, "dismantle_land", landAt.x, landAt.y, landAt.z, getYRot());
            discard();
            return;
        }
        setPos(end);
        if (blocked) discard();
    }

    // ------------------------------------------------------------------ DISSECT / WIDE

    private void tickLine(ServerLevel sl, Entity owner, int left, double speed, double radius, float damage, int stun, boolean wide) {
        if (left < 11) {
            discard();
            return;
        }
        // Deleter >= 17 and a block two blocks ahead: Deleter becomes 16 (the slash then flies on for five more ticks)
        if (left >= 17 && !sl.getBlockState(BlockPos.containing(position().add(dir.scale(2.0D)))).isAir()) {
            deleter = age + 16;
            left = 16;
        }
        setPos(position().add(dir.scale(speed)));
        Vec3 side = new Vec3(dir.z, 0.0D, -dir.x);
        side = side.lengthSqr() < 1.0E-6D ? new Vec3(1, 0, 0) : side.normalize();
        double[] lateral = wide ? new double[] {0, 4, -4, 8, -8} : new double[] {0};
        for (double l : lateral) {
            Vec3 c = position().add(side.scale(l));
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius),
                    e -> isTarget(sl, e))) {
                if (t.position().distanceTo(c) > radius || !hit.add(t.getUUID())) continue;
                strike(sl, owner, t, damage, stun, 5, dir);
                BedrockFxEntity.play(sl, "dissect_land", t.getX(), t.getY(), t.getZ(), getYRot());
            }
        }
    }

    // ------------------------------------------------------------------ DOWN

    private void tickDown(ServerLevel sl, Entity owner) {
        if (age >= deleter) {
            discard();
            return;
        }
        // three steps of two blocks per tick while the block here is air
        for (int i = 0; i < 3; i++) {
            if (!sl.getBlockState(blockPosition()).isAir()) break;
            setPos(getX(), getY() - 2.0D, getZ());
        }
        if (sl.getBlockState(blockPosition()).isAir()) return;
        double top = Math.floor(getY()) + 1.0D;
        Vec3 at = new Vec3(getX(), top, getZ());
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(10.0D, 12.0D, 10.0D),
                e -> isTarget(sl, e))) {
            if (t.position().distanceTo(at) > 10.0D) continue;
            strike(sl, owner, t, 32.0F, 35, 10, t.position().subtract(at).multiply(1, 0, 1).normalize());
        }
        BedrockFxEntity.play(sl, "dismantle_down_land", at.x, at.y, at.z, getYRot());
        discard();
    }

    // ------------------------------------------------------------------ helpers

    private boolean isTarget(ServerLevel sl, Entity e) {
        if (!e.isAlive() || e instanceof BedrockFxEntity) return false;
        Entity owner = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        if (e == owner) return false;
        if (owner instanceof DismantleCaster c) return c.isDismantleTarget(e);
        if (e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) return false;
        return owner == null || !e.isAlliedTo(owner);
    }

    private void strike(ServerLevel sl, Entity owner, LivingEntity t, float damage, int stunTicks, int hitStun, Vec3 push) {
        DamageSource src = sl.damageSources().indirectMagic(this, owner);
        t.invulnerableTime = 0;
        t.hurt(src, damage);
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stunTicks, 6, false, false));
        Vec3 p = push.lengthSqr() < 1.0E-6D ? Vec3.ZERO : push.normalize();
        t.push(p.x * 0.08D * hitStun, hitStun >= 10 ? 0.0D : 0.1D, p.z * 0.08D * hitStun);
        t.hurtMarked = true;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        double s = l2 < 1.0E-9D ? 0 : clamp01(p.subtract(a).dot(ab) / l2);
        return p.distanceTo(a.add(ab.scale(s)));
    }

    @Override protected void defineSynchedData() {
        entityData.define(KIND, QUICK);
    }

    @Override public boolean shouldBeSaved() { return false; }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
