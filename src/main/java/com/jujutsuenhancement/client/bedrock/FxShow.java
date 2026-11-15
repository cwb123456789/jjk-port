package com.jujutsuenhancement.client.bedrock;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.BedrockFxEntity;
import com.jujutsuenhancement.entity.DismantleSlashEntity;
import com.jujutsuenhancement.entity.GojoAwakenedEntity;
import com.jujutsuenhancement.entity.GojoTechEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Plays the particle and sound effects of the Bedrock skills that are shown by entities: {@link BedrockFxEntity}
 * (a named effect set, played once when the entity appears) and {@link DismantleSlashEntity} (the animations
 * {@code dismantle.horizontal1}, {@code .dissect}, {@code .wide} and {@code .down} of the addon, which only fire particle lists).
 * Offsets are in the addon's {@code ^left ^up ^forward} frame of the entity.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FxShow {
    private record P(String name, double left, double up, double fwd) {}

    private record S(String name, float volume, float pitch) {}

    private record Fx(List<P> particles, List<S> sounds, double shake) {}

    private static final Map<String, Fx> FX = new HashMap<>();
    private static final ParticleSim SIM = new ParticleSim();
    private static final Random RNG = new Random();
    private static final Map<Integer, Integer> SEEN = new HashMap<>();
    /** Gojo entity id -> {animation id, ticks since that animation started}. */
    private static final Map<Integer, int[]> GOJO = new HashMap<>();
    private static final Set<Integer> gojoSeen = new java.util.HashSet<>();
    private record GE(int tick, String name, double left, double up, double fwd) {}
    private static Map<String, List<GE>> gojoAnim;
    private static long lastNanos;
    private static int tickCount;
    private static int shakeUntil;
    private static double shakeAmp;

    private static P p(String n, double l, double u, double f) {
        return new P(n, l, u, f);
    }

    private static P p(String n) {
        return new P(n, 0, 0, 0);
    }

    private static S s(String n, float v, float pitch) {
        return new S(n, v, pitch);
    }

    private static void def(String id, double shake, List<P> particles, List<S> sounds) {
        FX.put(id, new Fx(particles, sounds, shake));
    }

    static {
        // dismantle_land.mcfunction / dismantle_vfx: animation dismantle.horizontal2 fires 1b..1e, sound mob.slice1
        def("dismantle_land", 0, List.of(p("dismantle_attack1b"), p("dismantle_attack1c"), p("dismantle_attack1d"), p("dismantle_attack1e")),
                List.of(s("bedrock.sukuna.slice1", 3.0F, 1.5F)));
        // dissect_land.mcfunction
        def("dissect_land", 0, List.of(p("heavy_impact2", 0, 1, 0), p("dismantle_slashed", 0, 1, 0), p("dismantle_attack1b"),
                p("dismantle_attack1c"), p("dismantle_attack1d"), p("dismantle_attack1e")),
                List.of(s("bedrock.sukuna.slice1", 3.0F, 1.5F)));
        // dismantledown.mcfunction
        def("dismantle_down_land", 2.0, List.of(p("ground_slam2"), p("ground_slam3"), p("slam_dust1"), p("slam_dust2"), p("dirt2"),
                p("dirt3"), p("dirt4"), p("large_slam1"), p("large_slam2"), p("large_slam3")),
                List.of(s("bedrock.sukuna.shock1", 3.0F, 1.0F), s("minecraft:entity.generic.explode", 3.0F, 0.5F),
                        s("minecraft:entity.generic.explode", 3.0F, 0.85F)));
        // cast-side effects of the move functions
        def("quick_cast", 0.6, List.of(p("ground_slam2")), List.of(s("bedrock.sukuna.slash", 3.0F, 1.0F)));
        def("flashstep", 0, List.of(p("flashstep1", 0, 1, 0)), List.of());
        def("leap", 0.5, List.of(p("leap"), p("rubble3"), p("ground_slam2"), p("bounce")), List.of());
        def("wide_cast", 1.5, List.of(p("ground_slam2"), p("ground_slam3")),
                List.of(s("bedrock.sukuna.dismantle", 3.0F, 1.0F), s("bedrock.sukuna.slash", 3.0F, 1.0F)));
        def("dissect_cast", 0.8, List.of(p("ground_slam2")), List.of(s("bedrock.sukuna.slash", 3.0F, 1.0F)));
        def("voice_kai", 0, List.of(), List.of(s("bedrock.sukuna.kai", 3.0F, 1.0F)));
        def("voice_done", 0, List.of(), List.of(s("bedrock.sukuna.youvedoneitnow", 3.0F, 1.0F)));
        // limitless2c.mcfunction: the targets of the Blue pull ("particle ds:impact" at move 15, lapse_pull2/3/4 at move 11)
        def("gojo_pull_hit", 0, List.of(p("impact")), List.of());
        def("gojo_pull_mark", 0, List.of(p("lapse_pull2"), p("lapse_pull3"), p("lapse_pull4")), List.of());
    }

    private FxShow() {}

    // ------------------------------------------------------------------ helpers

    private static void spawn(String name, Entity e, double left, double up, double fwd, double yaw) {
        if (SIM.particleCount() > 20000) return;
        ParticleDef def = BedrockAssets.get(name);
        if (def == null) return;
        double r = Math.toRadians(yaw);
        double cy = Math.cos(r), sy = Math.sin(r);
        // ^left ^up ^forward turned by the yaw (forward = (-sin, cos), left = (cos, sin))
        SIM.spawn(def, e.getX() + cy * left - sy * fwd, e.getY() + up, e.getZ() + sy * left + cy * fwd, yaw);
    }

    private static void spawnWorld(String name, double x, double y, double z, double yaw) {
        if (SIM.particleCount() > 20000) return;
        ParticleDef def = BedrockAssets.get(name);
        if (def != null) SIM.spawn(def, x, y, z, yaw);
    }

    private static void sound(Minecraft mc, S s, double x, double y, double z) {
        ResourceLocation id = s.name().contains(":") ? new ResourceLocation(s.name()) : new ResourceLocation(JujutsuEnhancement.MODID, s.name());
        mc.getSoundManager().play(new SimpleSoundInstance(id, SoundSource.HOSTILE, s.volume(), s.pitch(), RandomSource.create(),
                false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false));
    }

    private static void shake(double amp, int ticks) {
        shakeAmp = Math.max(amp, tickCount < shakeUntil ? shakeAmp : 0);
        shakeUntil = Math.max(shakeUntil, tickCount + ticks);
    }

    private static void playSet(Minecraft mc, BedrockFxEntity e) {
        Fx fx = FX.get(e.getFx());
        if (fx == null) return;
        double yaw = e.getYRot();
        for (P q : fx.particles()) spawn(q.name(), e, q.left(), q.up(), q.fwd(), yaw);
        for (S s : fx.sounds()) sound(mc, s, e.getX(), e.getY(), e.getZ());
        if (fx.shake() > 0 && mc.player != null && mc.player.distanceToSqr(e) < 40.0D * 40.0D) shake(fx.shake(), 5);
    }

    /** The addon's entity animations for the slash kinds, once per tick of the entity's life. */
    private static void slashTick(DismantleSlashEntity e, int a) {
        double yaw = e.getYRot();
        switch (e.getKind()) {
            case DismantleSlashEntity.QUICK -> {
                // animation.dismantle.horizontal1 is played while Deleter >= 23 and once more at Deleter 17
                if (a <= 2 || a == 8) spawn("dismantle_attack1", e, 0, 0, 0, yaw);
            }
            case DismantleSlashEntity.DISSECT -> {
                if (a <= 14) {
                    spawn("dismantle_attack_big1", e, 0, 0, 0, yaw);
                    spawn("dismantle_attack1", e, 0, 0, 0, yaw);
                    spawn("dismantle_attack1", e, 0, 0, 0, yaw);
                }
            }
            case DismantleSlashEntity.WIDE -> {
                if (a <= 4) spawn("dismantle_attack_wide", e, 0, 0, 0, yaw);
                if (a <= 14) spawnWorld("dismantle_stars", e.getX(), e.getY() - 5, e.getZ(), 0);
            }
            default -> {
                spawnWorld("dismantle_stars", e.getX(), e.getY(), e.getZ(), 0);
                if (a <= 2) {
                    spawn("dismantle_down1", e, 0, 0, 0, yaw);
                    spawn("dismantle_down2", e, 0, 0, 0, yaw);
                    spawn("dismantle_down3", e, 0, 0, 0, yaw);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Gojo: Blue and Red

    private static boolean ground(Minecraft mc, double x, double y, double z) {
        // "unless block ~~-1~ air" (and not barrier)
        var st = mc.level.getBlockState(BlockPos.containing(x, y - 1.0D, z));
        return !st.isAir() && !st.is(net.minecraft.world.level.block.Blocks.BARRIER);
    }

    private static Map<String, List<GE>> gojoAnim(Minecraft mc) {
        if (gojoAnim != null) return gojoAnim;
        Map<String, List<GE>> out = new HashMap<>();
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(new ResourceLocation(JujutsuEnhancement.MODID, "bedrock/gojo/anim_fx.json"));
            if (res.isPresent()) {
                try (java.io.Reader r = res.get().openAsReader()) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> en : root.entrySet()) {
                        List<GE> list = new ArrayList<>();
                        for (JsonElement ev : en.getValue().getAsJsonObject().getAsJsonArray("events")) {
                            JsonArray a = ev.getAsJsonArray();
                            list.add(new GE(a.get(0).getAsInt(), a.get(1).getAsString(), a.get(2).getAsDouble(), a.get(3).getAsDouble(), a.get(4).getAsDouble()));
                        }
                        out.put(en.getKey(), list);
                    }
                }
            }
        } catch (Exception ignored) {
            // no animation effects then
        }
        gojoAnim = out;
        return out;
    }

    private static String gojoAnimName(int anim) {
        return switch (anim) {
            case GojoAwakenedEntity.ANIM_BLUE -> "lapse_blue1";
            case GojoAwakenedEntity.ANIM_BLUE_PULL -> "lapse_pull";
            case GojoAwakenedEntity.ANIM_BLUE_MAX -> "lapse_blue2";
            case GojoAwakenedEntity.ANIM_RED -> "reversal_red1";
            case GojoAwakenedEntity.ANIM_RED_REVERSE -> "reversal_red2";
            default -> null;
        };
    }

    /** Server tick of the cast that the animation's first tick corresponds to, minus one (see GojoAwakenedEntity.animDelay). */
    private static int gojoDelay(int anim) {
        return anim == GojoAwakenedEntity.ANIM_BLUE_PULL ? 5 : anim == GojoAwakenedEntity.ANIM_BLUE_MAX ? 2 : 0;
    }

    private static void near(Minecraft mc, Entity e, double radius, double amp, int ticks) {
        if (mc.player != null && mc.player.distanceToSqr(e) < radius * radius) shake(amp, ticks);
    }

    /**
     * The effects of the Blue / Red casts: the particle events of the Bedrock animation (fired at the right hand, offsets computed
     * by tools/build_gojo_fx_assets.py) and what the cast functions in functions/limitless/limitless2*.mcfunction and
     * limitless3*.mcfunction play. {@code elapsed} is the number of ticks since the animation began, {@code sk} the matching tick of the cast
     * (the addon's move score is N - sk).
     */
    private static void gojoTick(Minecraft mc, GojoAwakenedEntity g, int anim, int elapsed) {
        String an = gojoAnimName(anim);
        if (an == null) return;
        double yaw = g.yBodyRot;
        List<GE> events = gojoAnim(mc).get(an);
        if (events != null) {
            for (GE ev : events) {
                if (ev.tick() == elapsed) spawn(ev.name(), g, ev.left(), ev.up(), ev.fwd(), yaw);
            }
        }
        int sk = elapsed + 1 + gojoDelay(anim);
        double x = g.getX(), y = g.getY(), z = g.getZ();
        switch (anim) {
            case GojoAwakenedEntity.ANIM_BLUE -> {
                if (sk >= 1 && sk <= 9 && sk % 3 == 1) sound(mc, s("minecraft:block.beacon.ambient", 0.6F, 2.0F), x, y + 1, z);
                if (sk == 11) near(mc, g, 15, 2.0, 2);
            }
            case GojoAwakenedEntity.ANIM_BLUE_PULL -> {
                if (sk >= 12 && sk <= 24 && sk % 3 == 0) sound(mc, s("minecraft:block.beacon.ambient", 0.6F, 2.0F), x, y + 1, z);
                if (sk == 11) {
                    near(mc, g, 15, 2.0, 2);
                    sound(mc, s("minecraft:block.beacon.deactivate", 1.0F, 1.75F), x, y + 1, z);
                }
                if (sk == 15) {
                    double r = Math.toRadians(yaw);
                    spawnWorld("lapse_pull2", x - Math.sin(r) * 1.5, y + 0.5, z + Math.cos(r) * 1.5, 0);
                    spawnWorld("lapse_pull4", x - Math.sin(r) * 1.5, y + 0.5, z + Math.cos(r) * 1.5, 0);
                }
            }
            case GojoAwakenedEntity.ANIM_BLUE_MAX -> {
                if (sk == 4) {
                    sound(mc, s("minecraft:entity.wither.shoot", 4.0F, 0.7F), x, y, z);
                    sound(mc, s("minecraft:block.beacon.activate", 4.0F, 0.74F), x, y, z);
                }
            }
            case GojoAwakenedEntity.ANIM_RED -> {
                // limitless3.mcfunction, move = 41 - sk
                if (sk == 6) sound(mc, s("bedrock.gojo.red1", 4.0F, 0.5F), x, y, z);
                if (sk >= 23) near(mc, g, 15, 0.1, 2);
                if (sk >= 19 && sk <= 26) spawnWorld("red_ex1", x, y + 1, z, 0);
                if (sk == 25 || sk == 26) {
                    for (String n : new String[] {"red_ex2", "red_ex3", "red_ex4", "red_ex5", "red_ex6"}) spawnWorld(n, x, y + 1, z, 0);
                    near(mc, g, 40, 2.0, 2);
                    if (ground(mc, x, y, z)) {
                        spawnWorld("crater3", x, y, z, 0);
                        for (double[] o : new double[][] {{0, 2}, {0, -2}, {2, 0}, {-2, 0}, {0, 0}}) spawnWorld("rubble3", x + o[0], y, z + o[1], 0);
                        sound(mc, s("minecraft:entity.generic.explode", 4.0F, 0.5F), x, y, z);
                        sound(mc, s("minecraft:entity.wither.break_block", 4.0F, 0.5F), x, y, z);
                    }
                }
                if (sk == 25) {
                    sound(mc, s("bedrock.gojo.shock2", 4.0F, 0.5F), x, y, z);
                    sound(mc, s("bedrock.gojo.shock1", 4.0F, 1.0F), x, y, z);
                }
            }
            case GojoAwakenedEntity.ANIM_RED_REVERSE -> {
                // limitless3c.mcfunction, move = 51 - sk
                if (sk == 6) sound(mc, s("bedrock.gojo.red1", 4.0F, 0.5F), x, y, z);
                if (sk == 45) {
                    near(mc, g, 50, 4.0, 3);
                    sound(mc, s("bedrock.gojo.shock2", 4.0F, 0.5F), x, y, z);
                    sound(mc, s("bedrock.gojo.shock1", 4.0F, 0.5F), x, y, z);
                }
            }
            default -> { }
        }
    }

    /** The particle lists the addon runs each tick for Lapse, Lapse2, MaxBlue and Reversal (lapseblue*.mcfunction, maxblue.mcfunction, reversal.mcfunction). */
    private static void techTick(Minecraft mc, GojoTechEntity t, int a) {
        double x = t.getX(), y = t.getY(), z = t.getZ();
        switch (t.getKind()) {
            case GojoTechEntity.BLUE_FLY -> {
                spawnWorld("lapse", x, y, z, 0);
                spawnWorld("lapseblue0a", x, y, z, 0);
                spawnWorld("lapseblue0b", x, y, z, 0);
            }
            case GojoTechEntity.BLUE_PULL -> {
                int left = 40 - a; // Deleter
                near(mc, t, 15, 0.5, 3);
                spawnWorld("lapseblue3", x, y, z, 0);
                spawnWorld("lapseblue5", x, y, z, 0);
                if (left >= 1 && left <= 30) spawnWorld("lapseblue2", x, y, z, 0);
                if (left >= 1 && left <= 22 || left == 30 || left == 28 || left == 27 || left == 25) spawnWorld("lapseblue1", x, y, z, 0);
                if (left >= 21) spawnWorld("blue_gas", x, y, z, 0);
                if (left >= 1 && left <= 35 && ground(mc, x, y, z)) {
                    spawnWorld("rubble_sucked1", x, y, z, 0);
                    spawnWorld("rubble_sucked2", x, y, z, 0);
                }
            }
            case GojoTechEntity.BLUE_MAX -> {
                int left = GojoTechEntity.MAX_LIFE - a;
                near(mc, t, 15, 0.5, 3);
                spawnWorld("rubble3", x, y, z, 0);
                spawnWorld("blue_gas", x, y, z, 0);
                // animation.max_lapse (loops on the projectile)
                for (String n : new String[] {"lapseblue1", "lapseblue2", "lapseblue3", "lapseblue5", "lapseblue6", "lapseblue4",
                        "rubble_sucked_big", "lapse_energy"}) spawnWorld(n, x, y, z, 0);
                if (left <= 2) {
                    for (String n : new String[] {"lapseblue_ex1", "lapseblue_ex2", "lapseblue_ex3", "lapseblue_ex4"}) spawnWorld(n, x, y, z, 0);
                }
                if (ground(mc, x, y, z)) {
                    spawnWorld("rubble_sucked1", x, y, z, 0);
                    spawnWorld("rubble_sucked2", x, y, z, 0);
                }
            }
            default -> {
                // animation.phase_red and the particles reversal.mcfunction fires
                for (String n : new String[] {"red1", "red2", "red3", "red7", "red_ex6", "red_ex4", "red_ex", "air_effect1"}) spawnWorld(n, x, y, z, 0);
                near(mc, t, 40, 1.0, 2);
            }
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!SEEN.isEmpty()) {
                SEEN.clear();
                SIM.clear();
            }
            return;
        }
        if (mc.isPaused()) return;
        tickCount++;
        Map<Integer, Integer> now = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof BedrockFxEntity fx) {
                now.put(fx.getId(), 0);
                if (!SEEN.containsKey(fx.getId())) playSet(mc, fx);
            } else if (e instanceof DismantleSlashEntity sl) {
                int a = Math.max(0, sl.tickCount - 1);
                int prev = SEEN.getOrDefault(sl.getId(), -1);
                now.put(sl.getId(), a);
                if (a > prev) slashTick(sl, a);
            } else if (e instanceof GojoTechEntity gt) {
                int a = Math.max(0, gt.tickCount - 1);
                int prev = SEEN.getOrDefault(gt.getId(), -1);
                now.put(gt.getId(), a);
                if (a > prev) techTick(mc, gt, a);
            } else if (e instanceof GojoAwakenedEntity g) {
                int anim = g.getAnimState();
                int[] st = GOJO.get(g.getId());
                if (st == null || st[0] != anim) st = new int[] {anim, -1};
                st[1]++;
                gojoSeen.add(g.getId());
                GOJO.put(g.getId(), st);
                if (gojoAnimName(anim) != null) gojoTick(mc, g, anim, st[1]);
            }
        }
        GOJO.keySet().retainAll(gojoSeen);
        gojoSeen.clear();
        SEEN.clear();
        SEEN.putAll(now);
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long t = System.nanoTime();
        double dt = Math.min(0.1D, (t - lastNanos) / 1.0e9D);
        lastNanos = t;
        if (SIM.particleCount() > 0 && !Minecraft.getInstance().isPaused()) SIM.step(dt);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || SIM.particleCount() == 0) return;
        Camera cam = event.getCamera();
        VoidShow.drawParticles(SIM, cam, cam.getPosition(), event.getPoseStack().last().pose());
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (tickCount >= shakeUntil || shakeAmp <= 0.001D) return;
        event.setYaw((float) (event.getYaw() + (RNG.nextDouble() - 0.5D) * 3.2D * shakeAmp));
        event.setPitch((float) (event.getPitch() + (RNG.nextDouble() - 0.5D) * 3.2D * shakeAmp));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        GOJO.clear();
        gojoAnim = null;
        SEEN.clear();
        SIM.clear();
        shakeUntil = 0;
    }
}
