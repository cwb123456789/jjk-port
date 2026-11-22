package com.jujutsuenhancement.entity;

import com.jujutsuenhancement.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * The projectile-like entities of Gojo's Blue (苍) and Red (赫), ported from the Bedrock addon's
 * {@code limitless/lapseblue.mcfunction}, {@code lapseblue2.mcfunction}, {@code maxblue.mcfunction} and
 * {@code reversal.mcfunction}. The addon makes them {@code ds:projectile} entities called Lapse, Lapse2, MaxBlue and Reversal,
 * whose {@code Deleter} score counts down every tick; here {@code age} counts up and the numbers are converted.
 * <ul>
 *   <li>BLUE_FLY (Lapse): shot from Gojo's feet at 4 blocks/tick for at most 45 ticks; on meeting a block or an enemy within
 *       4 blocks it turns (a tick later) into BLUE_PULL;</li>
 *   <li>BLUE_PULL (Lapse2): 40 ticks, appears 1.5 blocks above where Lapse ended; every tick it moves everything within 13 blocks
 *       (large mobs within 22) about 0.38 blocks towards itself, every second tick it hurts everything within 3.85 blocks for 1,
 *       and keeps them stunned for 15 ticks;</li>
 *   <li>BLUE_MAX (MaxBlue): kept 14 blocks in front of Gojo for 89 ticks, pulls everything within 15 blocks by up to 1.44 blocks/tick
 *       (three layers at 15, 13.75 and 12.5 blocks), hurts everything within 5 blocks for 2 every tick;</li>
 *   <li>RED_BEAM (Reversal): shot at 15 blocks/tick for 10 ticks; every tick everything within 20 blocks takes 45 damage (1.84.5, was 26) and the
 *       knockback roars of {@code ds:red_reverse} (9 blocks behind it) throw everything within 7 blocks away.</li>
 * </ul>
 * The addon's {@code lapse_explode} / {@code red_reversal} entities are explosions of which the victims are immune to
 * (the function gives them Resistance around the blast), so here they are only the explosion particles and sounds; nothing
 * breaks blocks (the addon only does that when a player has the "griefable" tag).
 */
public final class GojoTechEntity extends Entity {
    public static final int BLUE_FLY = 0, BLUE_PULL = 1, BLUE_MAX = 2, RED_BEAM = 3;

    /** Ticks BLUE_MAX lives (Deleter is held at 10 until move 15 of the cast, then counts down). */
    public static final int MAX_LIFE = 89;

    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(GojoTechEntity.class, EntityDataSerializers.INT);

    private UUID ownerUuid;
    private Vec3 dir = new Vec3(0, 0, 1);
    private int age;
    private int hitDelay = -1;

    public GojoTechEntity(EntityType<? extends GojoTechEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static GojoTechEntity launch(ServerLevel level, Entity owner, int kind, Vec3 pos, Vec3 dir) {
        GojoTechEntity e = ModEntities.GOJO_TECH.get().create(level);
        if (e == null) return null;
        e.ownerUuid = owner == null ? null : owner.getUUID();
        e.dir = dir.lengthSqr() < 1.0E-6D ? new Vec3(0, 0, 1) : dir.normalize();
        e.entityData.set(KIND, kind);
        float yaw = (float) Math.toDegrees(Math.atan2(-e.dir.x, e.dir.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(e.dir.y, Math.sqrt(e.dir.x * e.dir.x + e.dir.z * e.dir.z)));
        e.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
        level.addFreshEntity(e);
        return e;
    }

    public int getKind() {
        return entityData.get(KIND);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) return;
        Entity owner = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        switch (getKind()) {
            case BLUE_FLY -> tickFly(sl, owner);
            case BLUE_PULL -> tickPull(sl, owner);
            case BLUE_MAX -> tickMax(sl, owner);
            default -> tickBeam(sl, owner);
        }
        age++;
    }

    // ------------------------------------------------------------------ BLUE_FLY (Lapse)

    private void tickFly(ServerLevel sl, Entity owner) {
        if (hitDelay >= 0) {
            // Deleter was set to 3 on contact; Lapse2 is summoned when it reaches 2
            if (hitDelay-- == 0) {
                turnIntoPull(sl, owner);
            }
            return;
        }
        if (age >= 43) {
            turnIntoPull(sl, owner);
            return;
        }
        Vec3 from = position();
        Vec3 to = from.add(dir.scale(4.0D));
        HitResult block = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = block.getType() == HitResult.Type.BLOCK;
        Vec3 end = blocked ? block.getLocation().subtract(dir.scale(0.3D)) : to;
        setPos(end);
        boolean hit = blocked;
        if (!hit) {
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(end, end).inflate(4.0D), e -> isTarget(sl, owner, e))) {
                if (t.position().distanceTo(end) <= 4.0D) {
                    hit = true;
                    break;
                }
            }
        }
        if (hit) hitDelay = 0;
    }

    private void turnIntoPull(ServerLevel sl, Entity owner) {
        Vec3 p = position();
        launch(sl, owner, BLUE_PULL, new Vec3(p.x, p.y + 1.5D, p.z), dir);
        discard();
    }

    // ------------------------------------------------------------------ BLUE_PULL (Lapse2)

    private void tickPull(ServerLevel sl, Entity owner) {
        int left = 40 - age; // Deleter
        if (left < 1) {
            discard();
            return;
        }
        Vec3 c = position();
        if (age == 0) {
            // portal.trigger at pitch 2.5, 2.85 and 2 in the addon (Java caps the pitch at 2)
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 4.0F, 2.0F);
        }
        if (left >= 11 && age % 4 == 0) {
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 4.0F, 0.3F);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 4.0F, 0.45F);
        }
        // the explosions the addon summons (2 at Deleter 35, 6 per tick at 3..5); their victims are immune, so only the show remains
        if (left == 35 || (left >= 3 && left <= 5)) {
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.9F);
        }
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(22.0D), e -> isTarget(sl, owner, e))) {
            double d = t.position().distanceTo(c);
            double step = d <= 13.0D ? 0.382D : (isBig(t) && d <= 22.0D ? 0.36D : 0.0D);
            if (step > 0.0D) pullToward(t, c, step);
            if (d <= 3.85D) {
                if (age % 2 == 0) hurt(sl, owner, t, 1.0F);
                stun(t, 15);
            }
        }
    }

    // ------------------------------------------------------------------ BLUE_MAX (MaxBlue)

    private void tickMax(ServerLevel sl, Entity owner) {
        if (age >= MAX_LIFE || owner == null || !owner.isAlive()
                || owner instanceof GojoAwakenedEntity g && g.getSkillId() != GojoAwakenedEntity.ANIM_BLUE_MAX
                || owner instanceof net.minecraft.server.level.ServerPlayer sp
                        && !com.jujutsuenhancement.combat.GojoSkills.isCasting(sp, com.jujutsuenhancement.combat.GojoSkills.MAX)) {
            discard();
            return;
        }
        // "tp @e[name=MaxBlue] ^^^14 facing @s" every tick: 14 blocks along Gojo's view from his feet
        Vec3 look = owner.getLookAngle().normalize();
        setPos(owner.position().add(look.scale(14.0D)));
        Vec3 c = position();
        int left = MAX_LIFE - age;
        if (age == 0) {
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 5.0F, 0.5F);
        }
        if (age % 3 == 0) {
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 5.0F, 0.3F);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 5.0F, 0.45F);
        }
        if (age % 2 == 0) {
            sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 1.5D, 1.5D, 1.5D, 0.0D);
        }
        if (age % 6 == 0) {
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5F, 0.8F);
        }
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(24.0D), e -> isTarget(sl, owner, e))) {
            double d = t.position().distanceTo(c);
            double step = 0.0D;
            if (d <= 15.0D) step += 0.33D + 0.131D + 0.06D;
            if (d <= 13.75D) step += 0.33D + 0.131D;
            if (d <= 12.5D) step += 0.33D + 0.131D;
            if (isBig(t)) {
                if (d <= 24.0D) step += 0.33D + 0.131D + 0.06D;
                if (d <= 22.0D) step += 0.33D;
            }
            if (step > 0.0D) pullToward(t, c, step);
            if (d <= 5.0D) {
                hurt(sl, owner, t, 4.0F);
                stun(t, 15);
            }
        }
        if (left <= 2) {
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ RED_BEAM (Reversal)

    private void tickBeam(ServerLevel sl, Entity owner) {
        if (age >= 10) {
            discard();
            return;
        }
        setPos(position().add(dir.scale(15.0D)));
        Vec3 c = position();
        for (Entity e : sl.getEntitiesOfClass(Entity.class, new AABB(c, c).inflate(40.0D),
                x -> x instanceof net.minecraft.world.entity.projectile.AbstractArrow
                        || x instanceof net.minecraft.world.entity.item.ItemEntity)) {
            e.discard();
        }
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(20.0D), e -> isTarget(sl, owner, e))) {
            if (t.position().distanceTo(c) <= 20.0D) hurt(sl, owner, t, 45.0F);
        }
        // the three red_reverse knockback roars (range 7, strength 9) and red_reversal explosions 9 blocks behind and 1 below
        Vec3 horiz = new Vec3(dir.x, 0.0D, dir.z);
        Vec3 fwd = horiz.lengthSqr() < 1.0E-6D ? new Vec3(0, 0, 1) : horiz.normalize();
        Vec3 left = new Vec3(fwd.z, 0.0D, -fwd.x);
        Vec3 back = c.add(dir.scale(-9.0D)).add(0.0D, -1.0D, 0.0D);
        for (double side : new double[] {0.0D, 3.0D, -3.0D}) {
            Vec3 at = back.add(left.scale(side));
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            roar(sl, owner, at, 7.0D, 1.5D);
        }
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.6F);
    }

    // ------------------------------------------------------------------ helpers

    /** The addon's knockback roar: everything within {@code range} is thrown away from {@code at}. */
    public static void roar(ServerLevel sl, Entity owner, Vec3 at, double range, double speed) {
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(range), e -> isTarget(sl, owner, e))) {
            if (t.position().distanceTo(at) > range) continue;
            Vec3 away = t.position().subtract(at).multiply(1.0D, 0.0D, 1.0D);
            away = away.lengthSqr() < 1.0E-6D ? new Vec3(1, 0, 0) : away.normalize();
            t.setDeltaMovement(away.x * speed, 0.4D, away.z * speed);
            t.hurtMarked = true;
        }
    }

    /** Hurts without armour and without the invulnerability frames, as the addon's {@code damage ... override}. */
    public static void hurt(ServerLevel sl, Entity owner, LivingEntity t, float amount) {
        DamageSource src = sl.damageSources().indirectMagic(owner, owner);
        t.invulnerableTime = 0;
        t.hurt(src, amount);
    }

    public static void stun(LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
    }

    /**
     * {@code function obliterated} (1.84.5): after a heavy hit a target whose health is at most {@code threshold} is blown apart
     * (blood mist / explosion, camera shake, the blood sounds) and dies.
     */
    public static boolean obliterateIfLow(ServerLevel sl, Entity owner, LivingEntity t, float threshold) {
        if (!t.isAlive() || t.getHealth() > threshold) return false;
        BedrockFxEntity.timeline(sl, "obliterated", t.getX(), t.getY(), t.getZ(), 0.0F, 0.0F, 6);
        t.hurt(sl.damageSources().generic(), Float.MAX_VALUE);
        if (t.isAlive()) t.kill();
        return true;
    }

    public static boolean isBig(Entity e) {
        return e.getBbHeight() >= 3.0F || e.getBbWidth() >= 2.5F;
    }

    private static void pullToward(LivingEntity t, Vec3 c, double step) {
        Vec3 to = c.subtract(t.position());
        double d = to.length();
        if (d < 1.0E-4D) return;
        Vec3 delta = to.scale(Math.min(step, d) / d);
        if (t instanceof Player) {
            t.setDeltaMovement(delta);
            t.hurtMarked = true;
        } else {
            t.move(MoverType.SELF, delta);
            t.setDeltaMovement(t.getDeltaMovement().multiply(0.5D, 1.0D, 0.5D));
        }
        t.fallDistance = 0.0F;
    }

    private static boolean isTarget(ServerLevel sl, Entity owner, Entity e) {
        if (!e.isAlive() || e == owner || e instanceof BedrockFxEntity || e instanceof GojoTechEntity) return false;
        if (owner instanceof GojoAwakenedEntity g) return g.isEnemy(e);
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return owner == null || !e.isAlliedTo(owner);
    }

    @Override protected void defineSynchedData() {
        entityData.define(KIND, BLUE_FLY);
    }

    @Override public boolean shouldBeSaved() { return false; }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
