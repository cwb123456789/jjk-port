package com.jujutsuenhancement.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The look of JujutsuCraft's Unlimited Void, rebuilt next to the original mod instead of inside it.
 *
 * Everything here mirrors what the original does for domain number 2
 * ({@code UnlimitedVoidProcedure}, {@code DomainExpansionBattleProcedure}, {@code UnlimitedVoidActiveProcedure},
 * {@code AIGravestoneProcedure}), using only the original's registry ids:
 *
 * <ul>
 *   <li>a sphere of radius 22 whose centre is 11 blocks in front of the caster, swept outwards from the caster
 *       one block per tick: outer layer {@code jujutsucraft:jujutsu_barrier}, inner layer
 *       {@code jujutsucraft:block_universe}, floor {@code block_universe} (the surface layer and up to four
 *       exposed layers below), and everything solid inside the sphere above the floor turned into
 *       {@code jujutsucraft:in_barrier} (the invisible block);</li>
 *   <li>at tick 34 the black hole {@code jujutsucraft:entity_unlimited_void} is spawned 14 blocks behind the centre,
 *       three blocks up, no AI and invulnerable. The entity runs its own AI (hole ring + sparkles every six
 *       ticks) and removes itself when the owner's domain ends;</li>
 *   <li>from tick 45 on: ink-blot particles, white sparkles on a half sphere of radius 14 and the white ground wave
 *       ({@code end_rod}) are emitted every tick like {@code UnlimitedVoidActiveProcedure} does.</li>
 * </ul>
 *
 * The world is never touched: the blocks are sent to the clients as fake block updates and the real block states
 * are sent back when the domain ends, so nothing has to be restored on the server and a crash cannot leave a
 * black sphere in the save.
 */
public final class UnlimitedVoidDomain {
    public static final int RADIUS = 22;
    /** cnt1 == 34: the original spawns the black hole here. */
    private static final int VOID_TICK = 34;
    /** 2 * radius + 1: the original applies the domain effect and starts {@code UnlimitedVoidActive} here. */
    private static final int ACTIVE_TICK = 45;
    private static final int WATCH_RANGE = RADIUS + 96;
    private static final int RESTORE_PER_TICK = 6000;

    private static final byte OUTSIDE = 0;
    private static final byte INSIDE = 1;
    private static final byte FLOOR = 2;
    private static final byte BARRIER = 3;

    private static final String MOD = "jujutsucraft";
    private static final ParticleOptions SPARKLE = originalParticle("f_123810_", ParticleTypes.END_ROD);

    private final ServerLevel level;
    private final LivingEntity caster;
    private final double cx;
    private final double cy;
    private final double cz;
    private final double nameRanged;
    private final BlockState[] states = new BlockState[4];
    private final List<List<long[]>> buckets = new ArrayList<>();
    private final List<long[]> shown = new ArrayList<>();
    private final Set<UUID> watchers = new HashSet<>();
    private final ParticleOptions inkBlot;
    private UUID voidEntity;
    private int age;
    private boolean finished;
    private int restoreIndex = -1;

    public UnlimitedVoidDomain(ServerLevel level, LivingEntity caster) {
        this.level = level;
        this.caster = caster;
        double yaw = Math.toRadians(caster.getYRot() + 90.0F);
        this.cx = caster.getX() + Math.cos(yaw) * RADIUS * 0.5D;
        this.cy = Math.round(caster.getY());
        this.cz = caster.getZ() + Math.sin(yaw) * RADIUS * 0.5D;
        this.nameRanged = 0.5D + Math.random() * 0.5D;
        this.inkBlot = particle("particle_unlimited_void");
        this.states[OUTSIDE] = blockState("jujutsu_barrier");
        this.states[INSIDE] = blockState("block_universe");
        this.states[FLOOR] = this.states[INSIDE];
        this.states[BARRIER] = blockState("in_barrier");
        // The original keeps the black hole alive while the owner has "select" set and a matching NameRanged.
        caster.getPersistentData().putDouble("NameRanged", nameRanged);
        caster.getPersistentData().putDouble("select", 2.0D);
        if (states[OUTSIDE] != null && states[INSIDE] != null && states[BARRIER] != null) {
            plan(caster);
        }
    }

    // ------------------------------------------------------------------ planning

    /** Works out which block goes where, using the world as it is right now (same tests as the original). */
    private void plan(LivingEntity caster) {
        final int icx = (int) Math.round(cx);
        final int icy = (int) cy;
        final int icz = (int) Math.round(cz);
        final int ox = (int) Math.round(caster.getX());
        final int oy = (int) Math.round(caster.getY());
        final int oz = (int) Math.round(caster.getZ());
        final int yFloor = icy - 1;
        final int r2 = RADIUS * RADIUS;
        final int r1 = (RADIUS - 1) * (RADIUS - 1);
        final int r0 = (RADIUS - 2) * (RADIUS - 2);
        for (int i = 0; i < 64; i++) buckets.add(new ArrayList<>());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    int dis = dx * dx + dy * dy + dz * dz;
                    if (dis >= r2) continue;
                    int y = icy + dy;
                    if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
                    pos.set(icx + dx, y, icz + dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    boolean outer = dis >= r1;
                    boolean replace;
                    if (y <= yFloor - 1 && !outer) {
                        // Below the floor: only what is exposed to the air gets covered.
                        replace = !state.canOcclude() || exposed(pos);
                    } else {
                        replace = true;
                    }
                    if (!replace) continue;
                    byte type;
                    if (outer) {
                        type = OUTSIDE;
                    } else if (dis >= r0) {
                        type = INSIDE;
                    } else if (y <= yFloor) {
                        if (y < yFloor - 4) continue;
                        type = FLOOR;
                    } else if (!state.isAir()) {
                        type = BARRIER;
                    } else {
                        continue;
                    }
                    int ex = icx + dx - ox;
                    int ey = y - oy;
                    int ez = icz + dz - oz;
                    int t = (int) Math.ceil(Math.sqrt(ex * ex + ey * ey + ez * ez));
                    if (t < 1) t = 1;
                    if (t >= buckets.size()) t = buckets.size() - 1;
                    buckets.get(t).add(new long[] {pos.asLong(), type});
                }
            }
        }
    }

    private boolean exposed(BlockPos pos) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (!level.getBlockState(pos.relative(d)).canOcclude()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ ticking

    /** Called once per server tick while the domain lasts. {@code remaining} = ticks until it ends. */
    public void tick(long remaining) {
        if (finished) return;
        age++;
        refreshWatchers();
        if (age < buckets.size()) {
            for (long[] entry : buckets.get(age)) {
                shown.add(entry);
                BlockState state = states[(int) entry[1]];
                ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(BlockPos.of(entry[0]), state);
                sendToWatchers(packet);
            }
        }
        if (age == VOID_TICK) spawnBlackHole();
        if (age >= ACTIVE_TICK) tickActive(remaining);
    }

    private void refreshWatchers() {
        Vec3 centre = new Vec3(cx, cy, cz);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(centre) > (double) WATCH_RANGE * WATCH_RANGE) continue;
            if (!watchers.add(player.getUUID())) continue;
            // A player who arrives late receives everything that has been built so far.
            for (long[] entry : shown) {
                player.connection.send(new ClientboundBlockUpdatePacket(BlockPos.of(entry[0]), states[(int) entry[1]]));
            }
        }
    }

    private void sendToWatchers(net.minecraft.network.protocol.Packet<?> packet) {
        for (UUID id : watchers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level) player.connection.send(packet);
        }
    }

    /** {@code UnlimitedVoidProcedure} at cnt1 == 34. */
    private void spawnBlackHole() {
        ResourceLocation id = new ResourceLocation(MOD, "entity_unlimited_void");
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) return;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) return;
        // The caster is turned to face the target (or the centre) and a ray (radius - 8) long is cast along it;
        // the black hole sits behind the centre by the same vector, three blocks up.
        Vec3 eye = caster.getEyePosition();
        Vec3 aim;
        LivingEntity target = caster instanceof Mob mob ? mob.getTarget() : null;
        if (target != null) {
            aim = new Vec3(target.getX() - caster.getX(), 0.0D, target.getZ() - caster.getZ());
        } else {
            aim = new Vec3(cx - caster.getX(), 0.0D, cz - caster.getZ());
        }
        if (aim.lengthSqr() < 1.0E-6D) aim = Vec3.directionFromRotation(0.0F, caster.getYRot());
        aim = aim.normalize();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(aim.scale(RADIUS - 8)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
        BlockPos near = BlockPos.containing(eye);
        BlockPos far = hit.getBlockPos();
        BlockPos spawn = BlockPos.containing(cx + (near.getX() - far.getX()),
                cy + (near.getY() - far.getY()) + 3.0D, cz + (near.getZ() - far.getZ()));
        Entity hole = type.spawn(level, (ItemStack) null, null, spawn, MobSpawnType.MOB_SUMMONED, false, false);
        if (hole == null) return;
        hole.setYRot(caster.getYRot());
        hole.setXRot(0.0F);
        hole.setInvulnerable(true);
        if (hole instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setYBodyRot(caster.getYRot());
            mob.setYHeadRot(caster.getYRot());
        }
        hole.getPersistentData().putDouble("NameRanged_ranged", nameRanged);
        hole.getPersistentData().putString("OWNER_UUID", caster.getStringUUID());
        voidEntity = hole.getUUID();
    }

    /** {@code UnlimitedVoidActiveProcedure}: particles only (the lock itself is done by the caster). */
    private void tickActive(long remaining) {
        double range = RADIUS * 2.0D;
        double ticks = remaining;
        if (ticks % 5.0D == 0.0D && Math.random() < 0.1D && inkBlot != null) {
            double a = Math.toRadians(Math.random() * 360.0D);
            double d = range * 0.5D - 6.0D;
            emit(inkBlot, cx + Math.sin(a) * d, cy + 2.0D, cz + Math.cos(a) * d, 10, 1.0D, 1.0D, 1.0D, 0.0D);
        }
        double dis = range * 0.5D - 8.0D;
        double yaw = Math.toRadians(Math.random() * 360.0D);
        double radNow = Math.toRadians((Math.random() - 0.5D) * 180.0D);
        boolean variant = Math.random() < 0.5D;
        double rad = 0.0D;
        for (int i = 0; i < 72; i++) {
            if (Math.random() < 0.25D) {
                double x;
                double y;
                double z;
                if (variant) {
                    x = cx + Math.cos(rad) * (Math.cos(radNow) + Math.abs(Math.sin(yaw) * Math.sin(radNow))) * dis;
                    y = cy + Math.abs(Math.sin(rad) * Math.sin(radNow) * -1.0D * dis);
                    z = cz + Math.sin(rad) * (Math.cos(radNow) + Math.abs(Math.cos(yaw) * Math.sin(radNow))) * dis;
                } else {
                    x = cx + Math.cos(rad) * Math.cos(radNow) * dis;
                    y = cy + Math.abs(Math.sin(rad) * dis);
                    z = cz + Math.cos(rad) * Math.sin(radNow) * dis;
                }
                if (ticks % 40.0D == 0.0D && inkBlot != null) emit(inkBlot, x, y, z, 1, 1.0D, 1.0D, 1.0D, 0.0D);
                if (Math.random() < 0.5D) emit(SPARKLE, x, y, z, 1, 1.0D, 1.0D, 1.0D, 0.0D);
            }
            rad += Math.toRadians(10.0D * Math.random());
        }
        // White wave running over the floor.
        double rad1 = Math.toRadians(90.0D * (ticks % 4.0D));
        double wave = range * 0.5D - 5.0D;
        double distance = 4.0D * ticks % wave;
        for (int g = 0; g < 5; g++) {
            for (int i = 0; i < 72; i++) {
                rad1 += Math.toRadians(10.0D * Math.random());
                if (Math.random() < 0.025D) {
                    double x1 = cx + Math.cos(rad1) * distance;
                    double y1 = cy + 0.25D;
                    double z1 = cz + Math.sin(rad1) * distance;
                    double dx = cx + Math.cos(rad1) * (distance + 1.0D) - x1;
                    double dy = cy + 0.25D + Math.random() * 0.25D * (distance / wave) - y1;
                    double dz = cz + Math.sin(rad1) * (distance + 1.0D) - z1;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len != 0.0D) {
                        dx /= len;
                        dy /= len;
                        dz /= len;
                    }
                    double speed = 1.0D * (1.0D - distance / wave);
                    emit(ParticleTypes.END_ROD, x1 + 0.5D * (Math.random() - 0.5D), y1 + 0.5D * (Math.random() - 0.5D),
                            z1 + 0.5D * (Math.random() - 0.5D), 0, dx, dy, dz, speed);
                }
            }
            distance++;
        }
    }

    private void emit(ParticleOptions options, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        for (UUID id : watchers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level) {
                level.sendParticles(player, options, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    // ------------------------------------------------------------------ ending

    /** Starts giving the real blocks back to the clients and removes the black hole. Call {@link #tickEnd()} until it returns true. */
    public void beginEnd() {
        if (restoreIndex >= 0) return;
        finished = true;
        restoreIndex = 0;
        caster.getPersistentData().putDouble("select", 0.0D);
        if (voidEntity != null) {
            Entity hole = level.getEntity(voidEntity);
            if (hole != null) hole.discard();
            voidEntity = null;
        }
    }

    /** @return true once every block has been restored. */
    public boolean tickEnd() {
        if (restoreIndex < 0) beginEnd();
        int end = Math.min(shown.size(), restoreIndex + RESTORE_PER_TICK);
        for (; restoreIndex < end; restoreIndex++) {
            BlockPos pos = BlockPos.of(shown.get(restoreIndex)[0]);
            sendToWatchers(new ClientboundBlockUpdatePacket(level, pos));
        }
        return restoreIndex >= shown.size();
    }

    /** Immediate clean-up (the caster was removed). */
    public void endNow() {
        beginEnd();
        restoreIndex = Math.max(restoreIndex, 0);
        while (!tickEnd()) {
            // keep going; the loop is bounded by the number of shown blocks
        }
    }

    public boolean isDone() {
        return restoreIndex >= 0 && restoreIndex >= shown.size();
    }

    // ------------------------------------------------------------------ helpers

    private static BlockState blockState(String name) {
        ResourceLocation id = new ResourceLocation(MOD, name);
        if (!ForgeRegistries.BLOCKS.containsKey(id)) return null;
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        return block == null ? null : block.defaultBlockState();
    }

    private static ParticleOptions particle(String name) {
        ResourceLocation id = new ResourceLocation(MOD, name);
        if (!ForgeRegistries.PARTICLE_TYPES.containsKey(id)) return null;
        Object type = ForgeRegistries.PARTICLE_TYPES.getValue(id);
        return type instanceof ParticleOptions options ? options : null;
    }

    /**
     * The original refers to a few vanilla particles by their obfuscated field name; in a normal game these
     * are the runtime names, so they can be looked up reflectively. In a development environment (named
     * fields) the fallback is used instead.
     */
    private static ParticleOptions originalParticle(String srgName, ParticleOptions fallback) {
        try {
            Object value = ParticleTypes.class.getField(srgName).get(null);
            if (value instanceof ParticleOptions options) return options;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // fall through
        }
        return fallback;
    }
}
