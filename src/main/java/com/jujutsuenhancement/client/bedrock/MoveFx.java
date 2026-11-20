package com.jujutsuenhancement.client.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jujutsuenhancement.JujutsuEnhancement;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Plays the effects of a limitless move exactly as the Bedrock addon's move function does: the timeline
 * {@code bedrock/gojo/moves.json} (built by tools/build_gojo_moves.py from functions/limitless/*.mcfunction) lists, per tick
 * since the click, the particles ({@code particle ds:x ^l^u^f} / {@code ~x~y~z} at the caster), sounds, camera shakes,
 * screen fades and impact-frame titles.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MoveFx {
    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(JujutsuEnhancement.MODID, "move_fx");
    public static final IGuiOverlay OVERLAY = MoveFx::renderOverlay;

    private record Ev(int tick, Object[] row) {}

    private static final class Run {
        final int entityId;
        final List<Ev> events;
        final int total;
        int t;
        int next;

        Run(int entityId, List<Ev> events, int total) {
            this.entityId = entityId;
            this.events = events;
            this.total = total;
        }
    }

    private static Map<String, List<Ev>> moves;
    private static final Map<String, Integer> TOTALS = new HashMap<>();
    private static final List<Run> RUNS = new ArrayList<>();

    // screen fade: ticks (fractional) of the fade-in / hold / fade-out and the colour
    private static float fadeAge = -1, fadeIn, fadeHold, fadeOut;
    private static int fadeR, fadeG, fadeB;

    private MoveFx() {}

    private static void load(Minecraft mc) {
        if (moves != null) return;
        moves = new HashMap<>();
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(new ResourceLocation(JujutsuEnhancement.MODID, "bedrock/gojo/moves.json"));
            if (res.isPresent()) {
                try (java.io.Reader r = res.get().openAsReader()) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> en : root.entrySet()) {
                        JsonObject o = en.getValue().getAsJsonObject();
                        TOTALS.put(en.getKey(), o.get("n").getAsInt());
                        List<Ev> list = new ArrayList<>();
                        for (JsonElement el : o.getAsJsonArray("events")) {
                            JsonArray a = el.getAsJsonArray();
                            Object[] row = new Object[a.size() - 1];
                            for (int i = 1; i < a.size(); i++) {
                                var pr = a.get(i).getAsJsonPrimitive();
                                row[i - 1] = pr.isString() ? pr.getAsString() : (Object) pr.getAsDouble();
                            }
                            list.add(new Ev(a.get(0).getAsInt(), row));
                        }
                        moves.put(en.getKey(), list);
                    }
                }
            }
        } catch (Exception ignored) {
            // no effects then
        }
    }

    /** The server tells that {@code entityId} began the move {@code key} (the click tick). */
    public static void start(int entityId, String key) {
        Minecraft mc = Minecraft.getInstance();
        load(mc);
        if (key.startsWith("loop:")) {
            // "loop:<particle>:<ticks>": that particle at the entity every tick (the addon's per-tick "particle ds:x" of a running state)
            String[] a = key.split(":");
            if (a.length < 3) return;
            int n = Math.min(Integer.parseInt(a[2]), 6000);
            List<Ev> list = new ArrayList<>();
            for (int i = 1; i <= n; i++) list.add(new Ev(i, new Object[]{"p", a[1], 1.0D, 0.0D, 0.0D, 0.0D}));
            RUNS.removeIf(r -> r.entityId == entityId && r.events.size() > 0 && r.events.get(0).row()[1].equals(a[1]));
            RUNS.add(new Run(entityId, list, n));
            return;
        }
        List<Ev> list = moves.get(key);
        if (list == null) return;
        RUNS.removeIf(r -> r.entityId == entityId);
        RUNS.add(new Run(entityId, list, TOTALS.getOrDefault(key, 100)));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            RUNS.clear();
            fadeAge = -1;
            return;
        }
        if (mc.isPaused()) return;
        if (fadeAge >= 0) {
            fadeAge++;
            if (fadeAge > (fadeIn + fadeHold + fadeOut)) fadeAge = -1;
        }
        Iterator<Run> it = RUNS.iterator();
        while (it.hasNext()) {
            Run run = it.next();
            Entity e = mc.level.getEntity(run.entityId);
            if (e == null || !e.isAlive()) {
                it.remove();
                continue;
            }
            run.t++;
            while (run.next < run.events.size() && run.events.get(run.next).tick() <= run.t) {
                Ev ev = run.events.get(run.next++);
                if (ev.tick() == run.t) fire(mc, e, ev.row());
            }
            if (run.t > run.total + 2) it.remove();
        }
    }

    private static double d(Object o) {
        return ((Number) o).doubleValue();
    }

    private static boolean inRange(Minecraft mc, Entity e, double radius) {
        if (radius < 0) return e == mc.player;
        return radius == 0 || mc.player.distanceToSqr(e) <= radius * radius;
    }

    /** The addon's ^left ^up ^forward frame of the entity (its yaw and pitch). */
    private static Vec3 local(Entity e, double l, double u, double f) {
        double y = Math.toRadians(e.getYRot()), p = Math.toRadians(e.getXRot());
        Vec3 fwd = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        Vec3 left = new Vec3(Math.cos(y), 0.0D, Math.sin(y));
        Vec3 up = new Vec3(-Math.sin(y) * Math.sin(p), Math.cos(p), Math.cos(y) * Math.sin(p));
        return e.position().add(left.scale(l)).add(up.scale(u)).add(fwd.scale(f));
    }

    private static void fire(Minecraft mc, Entity e, Object[] r) {
        String kind = (String) r[0];
        switch (kind) {
            case "p" -> {
                String name = (String) r[1];
                boolean loc = d(r[2]) > 0.5D;
                double a = d(r[3]), b = d(r[4]), c = d(r[5]);
                Vec3 at = loc ? local(e, a, b, c) : e.position().add(a, b, c);
                FxShow.particleAt(name, at.x, at.y, at.z, e.getYRot());
            }
            case "r" -> {
                String name = (String) r[1];
                double step = d(r[2]), max = d(r[3]);
                // "anchored eyes positioned ^^-0.4^1": the line starts 1 ahead of the eyes, 0.4 lower, and walks along the view while it is air
                double y = Math.toRadians(e.getYRot()), p = Math.toRadians(e.getXRot());
                Vec3 fwd = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
                Vec3 up = new Vec3(-Math.sin(y) * Math.sin(p), Math.cos(p), Math.cos(y) * Math.sin(p));
                Vec3 start = e.getEyePosition().add(fwd).add(up.scale(-0.4D));
                Vec3 end = start.add(fwd.scale(max));
                var hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
                double len = hit.getType() == HitResult.Type.MISS ? max : hit.getLocation().distanceTo(start);
                for (double s = 0.0D; s <= len; s += step) {
                    Vec3 q = start.add(fwd.scale(s));
                    FxShow.particleAt(name, q.x, q.y, q.z, e.getYRot());
                }
            }
            case "s" -> {
                String name = (String) r[1];
                double vol = d(r[2]);
                if (!inRange(mc, e, d(r[4]))) return;
                ResourceLocation id = new ResourceLocation(name);
                boolean global = vol >= 100.0D;
                mc.getSoundManager().play(new SimpleSoundInstance(id, SoundSource.HOSTILE, global ? 1.0F : (float) vol, (float) d(r[3]),
                        RandomSource.create(), false, 0, global ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR,
                        e.getX(), e.getY(), e.getZ(), global));
            }
            case "k" -> {
                if (inRange(mc, e, d(r[3]))) FxShow.shakeFor(d(r[1]), (int) d(r[2]));
            }
            case "f" -> {
                if (!inRange(mc, e, d(r[7]))) return;
                fadeIn = (float) d(r[1]) * 20.0F;
                fadeHold = (float) d(r[2]) * 20.0F;
                fadeOut = (float) d(r[3]) * 20.0F;
                fadeR = (int) d(r[4]);
                fadeG = (int) d(r[5]);
                fadeB = (int) d(r[6]);
                fadeAge = 0;
            }
            case "i" -> {
                if (e != mc.player) return;
                int cp = (int) d(r[1]);
                if (cp == 0) ImpactFrames.clear();
                else ImpactFrames.show(cp);
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RUNS.clear();
        fadeAge = -1;
    }

    private static void renderOverlay(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        if (fadeAge < 0) return;
        float a = fadeAge + partialTick;
        float alpha;
        if (a < fadeIn) alpha = fadeIn <= 0 ? 1.0F : a / fadeIn;
        else if (a < fadeIn + fadeHold) alpha = 1.0F;
        else alpha = fadeOut <= 0 ? 0.0F : 1.0F - (a - fadeIn - fadeHold) / fadeOut;
        alpha = Math.max(0.0F, Math.min(1.0F, alpha));
        if (alpha <= 0.0F) return;
        RenderSystem.enableBlend();
        g.fill(0, 0, w, h, ((int) (alpha * 255.0F) << 24) | (fadeR << 16) | (fadeG << 8) | fadeB);
        RenderSystem.disableBlend();
    }
}
