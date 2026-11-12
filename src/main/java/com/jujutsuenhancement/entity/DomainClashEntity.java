package com.jujutsuenhancement.entity;

import com.jujutsuenhancement.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Bedrock addon's domain clash (领域对抗), a port of {@code domain_clash_starting.mcfunction} +
 * {@code domain_clash_main.mcfunction} + {@code scripts/domain_clashing.js}.
 * <p>
 * Sequence (t = ticks since the clash began; the addon's "starting" entity counts t, its "main" entity counts n = t - 60):
 * <ul>
 *   <li>t 1..70  - everybody in 50 blocks is frozen, shockwaves, camera shake, fade to black at t 40 (client);</li>
 *   <li>t 60     - a barrier platform (67x67, two layers) appears at y 299..300;</li>
 *   <li>t 61     - everybody is moved onto it, the clashers to the four compass points 33 blocks from the centre;</li>
 *   <li>t 65..   - each clasher's domain is shown as a particle animation placed behind it (client);</li>
 *   <li>t 208    - the clashers get their resistance (700 Unlimited Void, 250 Malevolent Shrine, 600 the others);</li>
 *   <li>t 210    - everybody is released, the fight starts; at most 595 ticks;</li>
 *   <li>fight    - taking damage costs the damage in resistance, a Malevolent Shrine costs everybody else 1 per tick;
 *                  at 0 the domain breaks; the fight ends when one domain is left or after 595 ticks;</li>
 *   <li>end      - white flash, 1 s stun, winner = highest resistance (ties: most health); the winner's domain takes over.</li>
 * </ul>
 * The client side ({@code ClashShow}) reads the counters from the synched data and plays everything that is seen.
 */
public final class DomainClashEntity extends Entity {
    // ---- timeline (see class comment)
    public static final int T_ARENA = 60;
    public static final int T_TELEPORT = 61;
    public static final int T_STANDS = 65;
    public static final int T_RESISTANCE = 208;
    public static final int T_COMMENCE = 210;
    public static final int FIGHT_MAX = 595;
    public static final int RES_STUN_FROM = 5;
    public static final int RES_STUN_TO = 11;
    public static final int RES_WINNER = 6;
    public static final int RES_TAKEOVER = 16;
    /** The arena is a 67 x 67 square (33 blocks each way from the centre). */
    public static final double ARM = 33.0D;
    /** The addon's arena centre sits 2.15 blocks below the top face of the platform. */
    public static final double CENTRE_BELOW_FLOOR = 2.15D;

    public static final int PHASE_SETUP = 0;
    public static final int PHASE_FIGHT = 1;
    public static final int PHASE_RESOLVE = 2;
    public static final int PHASE_ARENA = 3;

    private static final EntityDataAccessor<Integer> DATA_T = SynchedEntityData.defineId(DomainClashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(DomainClashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COUNT = SynchedEntityData.defineId(DomainClashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> DATA_SLOTS = SynchedEntityData.defineId(DomainClashEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private static final class Slot {
        UUID caster;
        ClashKind kind;
        int res;
        int cap;
        float lastHealth;
        boolean failed;
        UUID visual;
    }

    private final List<Slot> slots = new ArrayList<>();
    private final Map<UUID, double[]> homes = new HashMap<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final List<Long> platform = new ArrayList<>();
    private int t;
    private int fightTick;
    private int resTick;
    private int arenaLinger;
    private int winner = -1;
    private int floorY;
    private double groundX, groundY, groundZ;
    private boolean finished;
    private boolean released;
    private boolean announced;

    public DomainClashEntity(EntityType<? extends DomainClashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    // ------------------------------------------------------------------ geometry shared with the client

    /** Position of slot {@code k}'s domain entity (the clasher stands in front of it) for an arena centred on cx/cz. */
    public static double[] standPos(int k, double cx, double floorTop, double cz) {
        double sx = cx + (k == 2 ? ARM : k == 3 ? -ARM : 0.0D);
        double sz = cz + (k == 0 ? ARM : k == 1 ? -ARM : 0.0D);
        return new double[] {sx, floorTop + 0.5D, sz};
    }

    /** Minecraft yaw of slot {@code k}'s domain entity: it faces the centre. */
    public static float standYaw(int k) {
        return switch (k) {
            case 0 -> 180.0F;
            case 1 -> 0.0F;
            case 2 -> 90.0F;
            default -> -90.0F;
        };
    }

    // ------------------------------------------------------------------ start

    /** Called every tick by each domain caster; starts a clash when the addon's conditions are met. */
    public static void check(ClashCaster caster) {
        if (!(caster instanceof LivingEntity self) || !(self.level() instanceof ServerLevel level) || !self.isAlive()) return;
        int cast = caster.clashCastTicks();
        if (cast < 4 || cast > 70) return;
        if (!level.getEntitiesOfClass(DomainClashEntity.class, self.getBoundingBox().inflate(60.0D)).isEmpty()) return;
        if (level.getMaxBuildHeight() - level.getMinBuildHeight() < 160) return;
        List<LivingEntity> others = level.getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(48.0D),
                e -> e != self && e.isAlive() && e instanceof ClashCaster cc && cc.clashCastTicks() >= 0
                        && e.distanceToSqr(self) <= 48.0D * 48.0D && e.distanceToSqr(self) > 0.0225D);
        if (others.isEmpty()) return;
        others.sort(Comparator.comparingDouble(e -> e.distanceToSqr(self)));
        List<LivingEntity> all = new ArrayList<>();
        all.add(self);
        for (LivingEntity e : others) if (all.size() < 4) all.add(e);
        DomainClashEntity clash = new DomainClashEntity(ModEntities.DOMAIN_CLASH.get(), level);
        clash.begin(level, all);
        level.addFreshEntity(clash);
    }

    private void begin(ServerLevel level, List<LivingEntity> clashers) {
        double sx = 0, sy = 0, sz = 0;
        for (LivingEntity e : clashers) {
            sx += e.getX();
            sy += e.getY();
            sz += e.getZ();
            Slot s = new Slot();
            s.caster = e.getUUID();
            s.kind = ((ClashCaster) e).clashKind();
            s.lastHealth = e.getHealth();
            slots.add(s);
        }
        groundX = Math.floor(sx / clashers.size()) + 0.5D;
        groundY = sy / clashers.size();
        groundZ = Math.floor(sz / clashers.size()) + 0.5D;
        floorY = Math.max(level.getMinBuildHeight() + 8, Math.min(300, level.getMaxBuildHeight() - 24));
        this.moveTo(groundX, groundY, groundZ, 0.0F, 0.0F);
        // remember where everybody stood so that they can be put back
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(50.0D),
                x -> x.isAlive() && !x.isSpectator() && x.distanceToSqr(groundX, groundY, groundZ) <= 50.0D * 50.0D)) {
            homes.put(e.getUUID(), new double[] {e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot()});
        }
        for (LivingEntity e : clashers) {
            homes.putIfAbsent(e.getUUID(), new double[] {e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot()});
            ((ClashCaster) e).clashBegin();
        }
        this.entityData.set(DATA_COUNT, slots.size());
        syncSlots();
    }

    // ------------------------------------------------------------------ helpers

    private double floorTop() {
        return floorY + 1.0D;
    }

    private double centreX() {
        return t >= T_TELEPORT ? getX() : groundX;
    }

    private double centreZ() {
        return t >= T_TELEPORT ? getZ() : groundZ;
    }

    private LivingEntity casterOf(ServerLevel level, Slot s) {
        Entity e = level.getEntity(s.caster);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    private void syncSlots() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Slot s : slots) {
            CompoundTag c = new CompoundTag();
            c.putInt("kind", s.kind.ordinal());
            c.putInt("res", s.res);
            c.putInt("cap", s.cap);
            c.putBoolean("failed", s.failed);
            c.putUUID("id", s.caster);
            list.add(c);
        }
        tag.put("slots", list);
        tag.putInt("winner", winner);
        tag.putDouble("gx", groundX);
        tag.putDouble("gy", groundY);
        tag.putDouble("gz", groundZ);
        tag.putDouble("floor", floorTop());
        this.entityData.set(DATA_SLOTS, tag);
    }

    private int aliveCount() {
        int n = 0;
        for (Slot s : slots) if (!s.failed) n++;
        return n;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel level) || finished) return;
        if (slots.isEmpty()) {
            discard();
            return;
        }
        t++;
        this.entityData.set(DATA_T, t);

        // nobody left to clash
        int present = 0;
        for (Slot s : slots) if (casterOf(level, s) != null) present++;
        if (present < 2 && winner < 0 && t < T_COMMENCE + FIGHT_MAX + 40) {
            finish(level);
            return;
        }

        if (t < T_COMMENCE) holdEverybody(level);
        if (t == T_ARENA) placePlatform(level);
        if (t == T_TELEPORT) moveToArena(level);
        if (t == T_STANDS) placeVisuals(level);
        if (t == T_RESISTANCE) {
            for (Slot s : slots) {
                s.cap = s.kind.resistance;
                s.res = s.kind.resistance;
            }
            syncSlots();
        }
        if (t == T_COMMENCE) {
            release(level);
            this.entityData.set(DATA_PHASE, PHASE_FIGHT);
        }
        if (t >= T_COMMENCE) {
            int phase = this.entityData.get(DATA_PHASE);
            if (phase == PHASE_FIGHT) tickFight(level);
            else if (phase == PHASE_RESOLVE) tickResolve(level);
            else if (phase == PHASE_ARENA) tickArena(level);
        }

        // safety: nothing in the addon lasts longer than this
        if (t > T_COMMENCE + FIGHT_MAX + 40 + 1500) finish(level);
    }

    /** Everybody in range is stunned until the fight starts (the addon sets Stun 30 on them every tick). */
    private void holdEverybody(ServerLevel level) {
        double cx = centreX(), cz = centreZ();
        double cy = t >= T_TELEPORT ? getY() + CENTRE_BELOW_FLOOR : groundY;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(60.0D, 400.0D, 60.0D),
                x -> x.isAlive() && !x.isSpectator() && x.distanceToSqr(cx, cy, cz) <= 80.0D * 80.0D)) {
            hold(e);
        }
        for (Slot s : slots) {
            LivingEntity c = casterOf(level, s);
            if (c instanceof ClashCaster cc) cc.clashHold(true);
        }
    }

    private void hold(LivingEntity e) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 9, false, false));
        e.addEffect(new MobEffectInstance(MobEffects.JUMP, 4, 128, false, false));
        if (e instanceof Mob mob) {
            mob.setTarget(null);
            if (!mob.isNoAi()) {
                mob.setNoAi(true);
                frozen.add(mob.getUUID());
            }
        }
    }

    private void release(ServerLevel level) {
        released = true;
        for (UUID id : frozen) {
            Entity e = level.getEntity(id);
            if (e instanceof Mob mob) mob.setNoAi(false);
        }
        frozen.clear();
        for (Slot s : slots) {
            LivingEntity c = casterOf(level, s);
            if (c instanceof ClashCaster cc) cc.clashHold(false);
            if (c != null) {
                c.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                c.removeEffect(MobEffects.JUMP);
                s.lastHealth = c.getHealth();
            }
        }
    }

    // ------------------------------------------------------------------ arena

    private void placePlatform(ServerLevel level) {
        int cx = Mth.floor(groundX), cz = Mth.floor(groundZ);
        for (int x = cx - 33; x <= cx + 33; x++) {
            for (int z = cz - 33; z <= cz + 33; z++) {
                for (int y = floorY - 1; y <= floorY; y++) fillBarrier(level, new BlockPos(x, y, z));
            }
        }
    }

    private void fillBarrier(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return;
        if (!level.getBlockState(pos).isAir()) return;
        level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 2);
        platform.add(pos.asLong());
    }

    private void moveToArena(ServerLevel level) {
        // from now on the entity itself marks the arena centre
        double cx = groundX, cz = groundZ;
        this.setPos(cx, floorTop() - CENTRE_BELOW_FLOOR, cz);
        List<UUID> clasherIds = new ArrayList<>();
        for (Slot s : slots) clasherIds.add(s.caster);
        for (Map.Entry<UUID, double[]> en : homes.entrySet()) {
            Entity e = level.getEntity(en.getKey());
            if (!(e instanceof LivingEntity le) || !le.isAlive() || clasherIds.contains(en.getKey())) continue;
            double dx = Mth.clamp(en.getValue()[0] - groundX, -30.0D, 30.0D);
            double dz = Mth.clamp(en.getValue()[2] - groundZ, -30.0D, 30.0D);
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            // everybody looks at the centre
            teleport(level, le, cx + dx, floorTop() + 0.5D, cz + dz, (float) (yaw + 180.0D));
        }
        for (int k = 0; k < slots.size(); k++) {
            LivingEntity c = casterOf(level, slots.get(k));
            if (c == null) continue;
            double[] v = standPos(k, cx, floorTop(), cz);
            float yaw = standYaw(k);
            double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
            ClashKind kind = slots.get(k).kind;
            teleport(level, c, v[0] + fx * kind.ahead, v[1] + kind.up, v[2] + fz * kind.ahead, yaw);
            if (kind == ClashKind.MALEVOLENT_SHRINE) shrineBox(level, v[0], v[1], v[2]);
        }
    }

    /** The Malevolent Shrine clasher stands on an invisible box ({@code fill ~5 ~6 ~5 ~-5 ~-1 ~-5 barrier}). */
    private void shrineBox(ServerLevel level, double vx, double vy, double vz) {
        int bx = Mth.floor(vx), by = Mth.floor(vy), bz = Mth.floor(vz);
        for (int x = bx - 5; x <= bx + 5; x++)
            for (int z = bz - 5; z <= bz + 5; z++)
                for (int y = by - 1; y <= by + 6; y++) fillBarrier(level, new BlockPos(x, y, z));
    }

    private static void teleport(ServerLevel level, LivingEntity e, double x, double y, double z, float yaw) {
        e.fallDistance = 0.0F;
        e.setDeltaMovement(Vec3.ZERO);
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(level, x, y, z, yaw, sp.getXRot());
        } else {
            e.moveTo(x, y, z, yaw, 0.0F);
            e.setYHeadRot(yaw);
            e.setYBodyRot(yaw);
            if (e instanceof Mob mob) mob.getNavigation().stop();
        }
    }

    /** A domain entity that is only there to look like the domain (the shrine's geometry is a real entity). */
    private void placeVisuals(ServerLevel level) {
        for (int k = 0; k < slots.size(); k++) {
            Slot s = slots.get(k);
            if (s.kind != ClashKind.MALEVOLENT_SHRINE) continue;
            double[] v = standPos(k, getX(), floorTop(), getZ());
            DomainShrineEntity shrine = ModEntities.MALEVOLENT_SHRINE.get().create(level);
            if (shrine == null) continue;
            shrine.moveTo(v[0], v[1], v[2], standYaw(k), 0.0F);
            shrine.setVisualOnly();
            if (level.addFreshEntity(shrine)) s.visual = shrine.getUUID();
        }
    }

    // ------------------------------------------------------------------ fight

    private void tickFight(ServerLevel level) {
        fightTick++;
        if (fightTick == 2 && !announced) {
            announced = true;
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) <= 100.0D * 100.0D) {
                    p.sendSystemMessage(Component.literal("§l§c- 必中效果被抵消，正在与其他领域对抗，尽可能对敌人造成伤害以使对方领域崩坏! "));
                }
            }
        }
        // damage taken costs resistance (domain_clashing.js)
        for (int k = 0; k < slots.size(); k++) {
            Slot s = slots.get(k);
            if (s.failed) continue;
            LivingEntity c = casterOf(level, s);
            if (c == null) {
                fail(level, k);
                continue;
            }
            float drop = s.lastHealth - c.getHealth();
            if (drop > 0.0F && otherAliveWithin(level, k, 50.0D)) s.res = Math.max(0, s.res - (int) Math.floor(drop));
            s.lastHealth = c.getHealth();
        }
        // the Malevolent Shrine eats everybody else's resistance, 1 per tick
        for (int k = 0; k < slots.size(); k++) {
            Slot s = slots.get(k);
            if (s.failed || s.kind != ClashKind.MALEVOLENT_SHRINE) continue;
            for (int j = 0; j < slots.size(); j++) {
                if (j != k && !slots.get(j).failed) slots.get(j).res = Math.max(0, slots.get(j).res - 1);
            }
        }
        for (int k = 0; k < slots.size(); k++) {
            Slot s = slots.get(k);
            if (!s.failed && s.res <= 0) fail(level, k);
        }
        syncSlots();
        if (aliveCount() <= 1 || fightTick >= FIGHT_MAX) {
            resTick = 1;
            this.entityData.set(DATA_PHASE, PHASE_RESOLVE);
        }
    }

    private boolean otherAliveWithin(ServerLevel level, int k, double range) {
        LivingEntity me = casterOf(level, slots.get(k));
        if (me == null) return false;
        for (int j = 0; j < slots.size(); j++) {
            if (j == k || slots.get(j).failed) continue;
            LivingEntity o = casterOf(level, slots.get(j));
            if (o != null && o.distanceToSqr(me) <= range * range) return true;
        }
        return false;
    }

    /** {@code domain_clasher_failed.mcfunction}: the domain entity smashes (the client plays the particles and sounds). */
    private void fail(ServerLevel level, int k) {
        Slot s = slots.get(k);
        s.failed = true;
        s.res = 0;
        if (s.visual != null) {
            Entity v = level.getEntity(s.visual);
            if (v != null) v.discard();
            s.visual = null;
        }
        syncSlots();
    }

    // ------------------------------------------------------------------ resolution

    private void tickResolve(ServerLevel level) {
        resTick++;
        if (resTick >= RES_STUN_FROM && resTick <= RES_STUN_TO) {
            for (Slot s : slots) {
                LivingEntity c = casterOf(level, s);
                if (c != null) hold(c);
            }
        }
        if (resTick == RES_WINNER) pickWinner(level);
        if (resTick == RES_STUN_TO) {
            for (int k = 0; k < slots.size(); k++) if (k != winner) slots.get(k).res = 0;
            syncSlots();
        }
        if (resTick == RES_TAKEOVER) takeOver(level);
    }

    /** {@code findClashWinner} in domain_clashing.js: highest resistance above 0, ties go to the healthier clasher. */
    private void pickWinner(ServerLevel level) {
        int best = -1;
        int bestRes = 0;
        float bestHealth = -1.0F;
        for (int k = 0; k < slots.size(); k++) {
            Slot s = slots.get(k);
            LivingEntity c = casterOf(level, s);
            if (c == null || s.res <= 0) continue;
            if (s.res > bestRes || s.res == bestRes && c.getHealth() > bestHealth) {
                best = k;
                bestRes = s.res;
                bestHealth = c.getHealth();
            }
        }
        if (best < 0) {
            // domain_clash_main.mcfunction ticks 1608..1610: the first clasher that is still there wins
            for (int k = 0; k < slots.size(); k++) {
                if (casterOf(level, slots.get(k)) != null) {
                    best = k;
                    break;
                }
            }
        }
        winner = best;
        syncSlots();
    }

    private void takeOver(ServerLevel level) {
        for (int k = 0; k < slots.size(); k++) {
            LivingEntity c = casterOf(level, slots.get(k));
            if (k != winner && c instanceof ClashCaster cc) cc.clashLose();
        }
        if (winner < 0) {
            finish(level);
            return;
        }
        LivingEntity w = casterOf(level, slots.get(winner));
        if (w == null) {
            finish(level);
            return;
        }
        if (slots.get(winner).kind == ClashKind.UNLIMITED_VOID) {
            // the Unlimited Void goes on up in the sky arena
            this.entityData.set(DATA_PHASE, PHASE_ARENA);
            release(level);
            ((ClashCaster) w).clashWin(this);
        } else {
            ClashCaster cc = (ClashCaster) w;
            finish(level);
            cc.clashWin(this);
        }
    }

    private void tickArena(ServerLevel level) {
        LivingEntity w = winner >= 0 ? casterOf(level, slots.get(winner)) : null;
        if (w == null || !((ClashCaster) w).clashKeepArena()) {
            // keep the sky around a little longer so that the domain's break can be watched
            if (++arenaLinger >= 60) finish(level);
        } else {
            arenaLinger = 0;
        }
    }

    // ------------------------------------------------------------------ end

    /** Takes the platform away, puts everybody back where they were and removes the clash. */
    private void finish(ServerLevel level) {
        if (finished) return;
        finished = true;
        release(level);
        for (Slot s : slots) {
            if (s.visual != null) {
                Entity v = level.getEntity(s.visual);
                if (v != null) v.discard();
            }
        }
        boolean wentUp = t >= T_TELEPORT;
        if (wentUp) {
            for (Map.Entry<UUID, double[]> en : homes.entrySet()) {
                Entity e = level.getEntity(en.getKey());
                if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
                double[] h = en.getValue();
                if (le.getY() > floorY - 5) teleport(level, le, h[0], h[1], h[2], (float) h[3]);
            }
        }
        removePlatform(level);
        for (Slot s : slots) {
            LivingEntity c = casterOf(level, s);
            if (c instanceof ClashCaster cc) cc.clashHold(false);
        }
        this.entityData.set(DATA_PHASE, 4);
        discard();
    }

    private void removePlatform(ServerLevel level) {
        for (long l : platform) {
            BlockPos p = BlockPos.of(l);
            if (level.hasChunkAt(p) && level.getBlockState(p).is(Blocks.BARRIER)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        platform.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel level && !finished
                && reason != RemovalReason.UNLOADED_TO_CHUNK && reason != RemovalReason.UNLOADED_WITH_PLAYER) {
            finished = true;
            release(level);
            for (Slot s : slots) {
                if (s.visual != null) {
                    Entity v = level.getEntity(s.visual);
                    if (v != null) v.discard();
                }
            }
            removePlatform(level);
            for (Slot s : slots) {
                LivingEntity c = casterOf(level, s);
                if (c instanceof ClashCaster cc) cc.clashHold(false);
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ client view

    public int getT() {
        return this.entityData.get(DATA_T);
    }

    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public int getSlotCount() {
        return this.entityData.get(DATA_COUNT);
    }

    public CompoundTag getSlotsTag() {
        return this.entityData.get(DATA_SLOTS);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_T, 0);
        this.entityData.define(DATA_PHASE, PHASE_SETUP);
        this.entityData.define(DATA_COUNT, 0);
        this.entityData.define(DATA_SLOTS, new CompoundTag());
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
