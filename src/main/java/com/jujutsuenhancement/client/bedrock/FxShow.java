package com.jujutsuenhancement.client.bedrock;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.BedrockFxEntity;
import com.jujutsuenhancement.entity.DismantleSlashEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
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
import java.util.Random;

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
            }
        }
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
        SEEN.clear();
        SIM.clear();
        shakeUntil = 0;
    }
}
