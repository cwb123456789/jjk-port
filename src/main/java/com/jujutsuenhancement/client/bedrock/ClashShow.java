package com.jujutsuenhancement.client.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.ClashKind;
import com.jujutsuenhancement.entity.DomainClashEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Plays the Bedrock addon's domain clash (领域对抗) on the client. The server side is {@link DomainClashEntity}; this class
 * reads its tick counter and the per-clasher state from the entity's synched data and shows what the addon's
 * {@code domain_clash_starting} / {@code domain_clash_main} functions and the {@code *_clashing} entity animations show:
 * shockwaves and camera shake, the fade to black, the camera shots of the arena and of every clasher, each domain's
 * particle animation behind its caster, the smash of a domain that breaks and the white flash at the end.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClashShow {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(JujutsuEnhancement.MODID, "clash_show");
    public static final IGuiOverlay OVERLAY = ClashShow::renderOverlay;

    private static final ParticleSim SIM = new ParticleSim();
    private static final Random RNG = new Random();
    private static Map<String, List<String>> effects;

    // ---- show state
    private static int clashId = -1;
    private static int lastT;
    private static int lastPhase;
    private static boolean involved;
    private static final boolean[] FAILED = new boolean[4];
    private static int winner = -1;
    private static int tickCount;
    private static long lastNanos;
    private static float partial;

    // ---- camera
    private static Marker marker;
    private static boolean cameraOwned;
    private static CameraType savedCamera;
    private static VoidScript.Pose startPose = new VoidScript.Pose();
    private static final List<CKey> KEYS = new ArrayList<>();
    private static double shakeAmp;
    private static final List<double[]> SHAKES = new ArrayList<>();

    // ---- fades
    private static final class Fade {
        double start, in, hold, out;
        float r, g, b;
    }

    private static final List<Fade> FADES = new ArrayList<>();

    private static final class CKey {
        final double t;
        final VoidScript.Pose pose;
        final double ease;
        final boolean circ;

        CKey(double t, VoidScript.Pose pose, double ease, boolean circ) {
            this.t = t;
            this.pose = pose;
            this.ease = ease;
            this.circ = circ;
        }
    }

    private static final class SlotView {
        ClashKind kind;
        int res, cap;
        boolean failed;
        UUID id;
    }

    private ClashShow() {}

    // ------------------------------------------------------------------ helpers used by VoidShow

    /** The clash arena closest to {@code g} (horizontally), or null. */
    public static DomainClashEntity nearestClash(Minecraft mc, Entity g) {
        if (mc.level == null) return null;
        DomainClashEntity best = null;
        double bd = 70.0D * 70.0D;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof DomainClashEntity c)) continue;
            double dx = c.getX() - g.getX(), dz = c.getZ() - g.getZ();
            double d = dx * dx + dz * dz;
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    /** Top face of the clash's barrier platform. */
    public static double floorTop(DomainClashEntity c) {
        CompoundTag tag = c.getSlotsTag();
        return tag.contains("floor") ? tag.getDouble("floor") : c.getY() + DomainClashEntity.CENTRE_BELOW_FLOOR;
    }

    /** Whether the local player stands in the clash arena that {@code g} is fighting in. */
    public static boolean involved(Minecraft mc, Entity g) {
        DomainClashEntity c = nearestClash(mc, g);
        if (c == null || mc.player == null) return false;
        double dx = c.getX() - mc.player.getX(), dz = c.getZ() - mc.player.getZ();
        return dx * dx + dz * dz <= 80.0D * 80.0D;
    }

    private static List<String> effectsFor(ClashKind kind) {
        if (effects == null) {
            effects = new HashMap<>();
            try {
                ResourceLocation id = new ResourceLocation(JujutsuEnhancement.MODID, "bedrock/clash/effects.json");
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
                if (res.isPresent()) {
                    try (Reader reader = res.get().openAsReader()) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        for (Map.Entry<String, JsonElement> en : json.entrySet()) {
                            List<String> list = new ArrayList<>();
                            JsonArray arr = en.getValue().getAsJsonArray();
                            for (JsonElement el : arr) list.add(el.getAsString());
                            effects.put(en.getKey(), list);
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("Could not read the domain clash effect lists: {}", ex.toString());
            }
        }
        List<String> l = effects.get(kind.id);
        return l == null ? List.of() : l;
    }

    // ------------------------------------------------------------------ small utilities

    private static void spawn(String name, double x, double y, double z, double yaw) {
        if (SIM.particleCount() > 20000) return;
        ParticleDef def = BedrockAssets.get(name);
        if (def != null) SIM.spawn(def, x, y, z, yaw);
    }

    private static void shake(double amp, int ticks) {
        SHAKES.add(new double[] {tickCount + ticks, amp});
    }

    private static void fade(double in, double hold, double out, float r, float g, float b) {
        Fade f = new Fade();
        f.start = tickCount;
        f.in = in;
        f.hold = hold;
        f.out = out;
        f.r = r;
        f.g = g;
        f.b = b;
        FADES.add(f);
    }

    private static void sound(String name, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(new SimpleSoundInstance(new ResourceLocation(JujutsuEnhancement.MODID, "bedrock.clash." + name),
                SoundSource.HOSTILE, volume, pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE,
                0.0D, 0.0D, 0.0D, true));
    }

    private static List<SlotView> readSlots(CompoundTag data) {
        List<SlotView> out = new ArrayList<>();
        ListTag list = data.getList("slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            SlotView s = new SlotView();
            s.kind = ClashKind.byOrdinal(c.getInt("kind"));
            s.res = c.getInt("res");
            s.cap = c.getInt("cap");
            s.failed = c.getBoolean("failed");
            s.id = c.hasUUID("id") ? c.getUUID("id") : null;
            out.add(s);
        }
        return out;
    }

    // ------------------------------------------------------------------ camera

    private static VoidScript.Pose pose(double x, double y, double z, double pitch, double yaw) {
        VoidScript.Pose p = new VoidScript.Pose();
        p.x = x;
        p.y = y;
        p.z = z;
        p.pitch = pitch;
        p.yaw = yaw;
        return p;
    }

    private static VoidScript.Pose look(double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return pose(x, y, z, pitch, yaw);
    }

    /** Offset in a frame turned by {@code yaw}: {@code left}, {@code up}, {@code forward} like the addon's {@code ^ ^ ^}. */
    private static double[] frameOffset(double left, double up, double fwd, double yaw) {
        double cy = Math.cos(Math.toRadians(yaw)), sy = Math.sin(Math.toRadians(yaw));
        return new double[] {cy * left - sy * fwd, up, sy * left + cy * fwd};
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5 ? (1 - Math.sqrt(1 - 4 * t * t)) / 2 : (Math.sqrt(1 - (-2 * t + 2) * (-2 * t + 2)) + 1) / 2;
    }

    private static double wrapNear(double v, double ref) {
        while (v - ref > 180) v -= 360;
        while (v - ref < -180) v += 360;
        return v;
    }

    private static VoidScript.Pose mix(VoidScript.Pose a, VoidScript.Pose b, double t) {
        VoidScript.Pose p = new VoidScript.Pose();
        p.x = a.x + (b.x - a.x) * t;
        p.y = a.y + (b.y - a.y) * t;
        p.z = a.z + (b.z - a.z) * t;
        double by = wrapNear(b.yaw, a.yaw);
        p.yaw = a.yaw + (by - a.yaw) * t;
        p.pitch = a.pitch + (b.pitch - a.pitch) * t;
        return p;
    }

    private static VoidScript.Pose cameraAt(double tt) {
        VoidScript.Pose cur = startPose;
        for (int i = 0; i < KEYS.size(); i++) {
            CKey k = KEYS.get(i);
            if (tt < k.t) break;
            boolean last = i == KEYS.size() - 1 || tt < KEYS.get(i + 1).t;
            double at = last ? tt : KEYS.get(i + 1).t;
            VoidScript.Pose p = k.ease <= 0 || at >= k.t + k.ease ? k.pose
                    : mix(cur, k.pose, k.circ ? ease((at - k.t) / k.ease) : (at - k.t) / k.ease);
            if (last) return p;
            cur = p;
        }
        return cur;
    }

    private static void key(double t, VoidScript.Pose p, double easeTicks) {
        KEYS.add(new CKey(t, p, easeTicks, true));
    }

    private static void acquireCamera(Minecraft mc) {
        if (cameraOwned || mc.level == null || mc.player == null) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        startPose = pose(cam.getPosition().x, cam.getPosition().y, cam.getPosition().z, cam.getXRot(), cam.getYRot());
        if (marker == null || marker.level() != mc.level) marker = new Marker(EntityType.MARKER, mc.level);
        savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(marker);
        cameraOwned = true;
    }

    private static void releaseCamera() {
        if (!cameraOwned) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (savedCamera != null) mc.options.setCameraType(savedCamera);
        cameraOwned = false;
    }

    private static void placeCamera(float pt) {
        if (marker == null) return;
        VoidScript.Pose p = cameraAt(lastT + pt);
        marker.setPos(p.x, p.y, p.z);
        marker.xo = marker.xOld = p.x;
        marker.yo = marker.yOld = p.y;
        marker.zo = marker.zOld = p.z;
        marker.setXRot((float) Mth.clamp(p.pitch, -90, 90));
        marker.setYRot((float) p.yaw);
        marker.xRotO = marker.getXRot();
        marker.yRotO = marker.getYRot();
    }

    // ------------------------------------------------------------------ lifecycle

    private static void stop() {
        releaseCamera();
        SIM.clear();
        KEYS.clear();
        SHAKES.clear();
        shakeAmp = 0;
        clashId = -1;
        lastT = 0;
        lastPhase = 0;
        involved = false;
        winner = -1;
        java.util.Arrays.fill(FAILED, false);
    }

    private static DomainClashEntity find(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        if (clashId >= 0) {
            Entity e = mc.level.getEntity(clashId);
            return e instanceof DomainClashEntity c && c.isAlive() ? c : null;
        }
        DomainClashEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof DomainClashEntity c) || !c.isAlive()) continue;
            double dx = c.getX() - mc.player.getX(), dz = c.getZ() - mc.player.getZ();
            double d = dx * dx + dz * dz;
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (clashId >= 0) stop();
            return;
        }
        if (mc.isPaused()) return;
        tickCount++;
        SHAKES.removeIf(s -> s[0] <= tickCount);
        double amp = 0;
        for (double[] s : SHAKES) amp = Math.max(amp, s[1]);
        shakeAmp = amp;

        DomainClashEntity c = find(mc);
        if (c == null) {
            if (clashId >= 0) stop();
            return;
        }
        CompoundTag data = c.getSlotsTag();
        int t = c.getT();
        if (clashId < 0) {
            // first sight of this clash: is the local player part of it?
            double gx = data.contains("gx") ? data.getDouble("gx") : c.getX();
            double gz = data.contains("gz") ? data.getDouble("gz") : c.getZ();
            double gy = data.contains("gy") ? data.getDouble("gy") : c.getY();
            double dx = mc.player.getX() - gx, dz = mc.player.getZ() - gz, dy = mc.player.getY() - gy;
            boolean onGround = t <= DomainClashEntity.T_TELEPORT + 1 && dx * dx + dz * dz + dy * dy <= 55.0D * 55.0D;
            boolean onPlatform = t > DomainClashEntity.T_TELEPORT + 1 && Math.abs(mc.player.getY() - floorTop(c)) < 8.0D
                    && dx * dx + dz * dz <= 45.0D * 45.0D;
            involved = onGround || onPlatform;
            clashId = c.getId();
            lastT = Math.max(0, t - 1);
            lastPhase = c.getPhase();
        }
        if (!involved) return;
        if (t - lastT > 8) lastT = t - 1;
        while (lastT < t) {
            lastT++;
            processTick(mc, c, lastT, data);
        }
        int phase = c.getPhase();
        if (phase != lastPhase) {
            onPhase(mc, c, lastPhase, phase);
            lastPhase = phase;
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        partial = event.renderTickTime;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNanos) / 1.0e9);
        lastNanos = now;
        if (SIM.particleCount() > 0 && !mc.isPaused()) SIM.step(dt);
        if (cameraOwned) placeCamera(event.renderTickTime);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (cameraOwned) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeAmp <= 0.001) return;
        event.setYaw((float) (event.getYaw() + (RNG.nextDouble() - 0.5) * 2.0 * shakeAmp * 1.6));
        event.setPitch((float) (event.getPitch() + (RNG.nextDouble() - 0.5) * 2.0 * shakeAmp * 1.6));
        event.setRoll((float) (event.getRoll() + (RNG.nextDouble() - 0.5) * 2.0 * shakeAmp * 0.6));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
        FADES.clear();
        marker = null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (SIM.particleCount() == 0) return;
        Camera cam = event.getCamera();
        VoidShow.drawParticles(SIM, cam, cam.getPosition(), event.getPoseStack().last().pose());
    }

    // ------------------------------------------------------------------ the show

    private static Map<UUID, Entity> casters(Minecraft mc) {
        Map<UUID, Entity> map = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof net.minecraft.world.entity.LivingEntity) map.put(e.getUUID(), e);
        }
        return map;
    }

    private static void processTick(Minecraft mc, DomainClashEntity c, int t, CompoundTag data) {
        List<SlotView> slots = readSlots(data);
        Map<UUID, Entity> who = casters(mc);
        double floor = floorTop(c);
        double cx = c.getX(), cz = c.getZ();
        double oy = floor - DomainClashEntity.CENTRE_BELOW_FLOOR;

        // where each clasher is (the mob itself) and where its domain animation stands
        double[][] at = new double[slots.size()][];
        double[][] stand = new double[slots.size()][];
        for (int k = 0; k < slots.size(); k++) {
            SlotView s = slots.get(k);
            Entity e = s.id == null ? null : who.get(s.id);
            stand[k] = DomainClashEntity.standPos(k, cx, floor, cz);
            at[k] = e != null ? new double[] {e.getX(), e.getY(), e.getZ(), e.getYRot()} : new double[] {stand[k][0], stand[k][1], stand[k][2], DomainClashEntity.standYaw(k)};
        }

        // ---- domain_clash_starting
        if (t >= 1 && t <= 70) {
            for (double[] p : at) {
                spawn("domainclash", p[0], p[1], p[2], 0);
                spawn("domainclash1", p[0], p[1], p[2], 0);
                spawn("domainclash2", p[0], p[1], p[2], 0);
                spawn("domainclash3", p[0], p[1], p[2], 0);
            }
            shake(0.4, 2);
        }
        if (t == 3) {
            shake(2.0, 5);
            sound("blasted4", 1.6F, 1.0F);
            for (double[] p : at) spawn("domain_clash_shockwave1", p[0], p[1], p[2], 0);
        }
        if (t == 10) sound("fuga_shoot", 1.2F, 1.0F);
        if (t == 20) {
            shake(2.0, 3);
            sound("blasted3", 1.6F, 1.0F);
            for (double[] p : at) {
                spawn("domain_clash_shockwave1", p[0], p[1], p[2], 0);
                spawn("domain_clash_shockwave2", p[0], p[1], p[2], 0);
                spawn("domain_clash_shockwave3", p[0], p[1], p[2], 0);
            }
        }
        if (t >= 30 && t <= 70) {
            for (double[] p : at) spawn("domain_clash_engulf1", p[0], p[1], p[2], 0);
        }
        if (t == 40) fade(15, 20, 20, 0F, 0F, 0F);

        // ---- domain_clash_main camera work (n = t - 60)
        if (t == 76) {
            acquireCamera(mc);
            key(t, pose(cx + 5, oy + 15, cz - 15, -15, -35), 0);
        }
        if (t == 77) key(t, pose(cx + 6, oy + 16, cz - 14, 30, 65), 20);
        if (t == 94) key(t, pose(cx - 15, oy + 3, cz + 15, 15, 135), 0);
        if (t == 95) key(t, pose(cx - 6, oy + 1, cz + 14, 0, 225), 20);
        for (int k = 0; k < slots.size() && k < 4; k++) {
            int ts = 125 + 10 * k;
            if (t == ts && cameraOwned) {
                double yaw = at[k][3];
                double[] p1 = frameOffset(0, 1.4, 2, yaw), l1 = frameOffset(4, 1.4, 0, yaw);
                key(t, look(at[k][0] + p1[0], at[k][1] + p1[1], at[k][2] + p1[2], at[k][0] + l1[0], at[k][1] + l1[1], at[k][2] + l1[2]), 0);
                sound("whoosh3", 1.0F, 1.0F);
            }
            if (t == ts + 1 && cameraOwned) {
                double yaw = at[k][3];
                double[] p2 = frameOffset(0, 1.4, 1.5, yaw), l2 = frameOffset(0, 1.4, 0, yaw);
                key(t, look(at[k][0] + p2[0], at[k][1] + p2[1], at[k][2] + p2[2], at[k][0] + l2[0], at[k][1] + l2[1], at[k][2] + l2[2]), 5);
            }
        }
        if (t == 175 || t == 176) {
            int gojo = -1, shrine = -1;
            for (int k = 0; k < slots.size(); k++) {
                if (slots.get(k).kind == ClashKind.UNLIMITED_VOID) gojo = k;
                if (slots.get(k).kind == ClashKind.MALEVOLENT_SHRINE) shrine = k;
            }
            if (gojo >= 0 && shrine >= 0 && cameraOwned) {
                double yaw = at[gojo][3];
                double[] pa = t == 175 ? frameOffset(2, 1.4, 4, yaw) : frameOffset(-1.5, 0.5, -3, yaw);
                key(t, look(at[gojo][0] + pa[0], at[gojo][1] + pa[1], at[gojo][2] + pa[2], stand[shrine][0], stand[shrine][1] + 12, stand[shrine][2]),
                        t == 175 ? 0 : 5);
            }
        }
        if (t == 200) fade(9, 2, 2, 0F, 0F, 0F);
        if (t == DomainClashEntity.T_COMMENCE) releaseCamera();

        // ---- the clashers' domain animations
        if (t >= DomainClashEntity.T_STANDS) {
            for (int k = 0; k < slots.size(); k++) {
                SlotView s = slots.get(k);
                if (k < FAILED.length && s.failed && !FAILED[k]) {
                    FAILED[k] = true;
                    spawn("clash_smash", at[k][0], at[k][1] + 3, at[k][2], 0);
                    shake(2.0, 5);
                    sound("blasted3", 1.4F, 1.0F);
                    if (mc.level != null) mc.level.playLocalSound(at[k][0], at[k][1], at[k][2], SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.8F, false);
                }
                int w = data.getInt("winner");
                winner = w;
                boolean emitting = !s.failed && (w < 0 || w == k) && c.getPhase() != DomainClashEntity.PHASE_ARENA;
                if (!emitting) continue;
                for (String name : effectsFor(s.kind)) {
                    spawn(name, stand[k][0], stand[k][1], stand[k][2], DomainClashEntity.standYaw(k));
                    // the animation's particle track fires about 24 times a second
                    if (t % 5 == 0) spawn(name, stand[k][0], stand[k][1], stand[k][2], DomainClashEntity.standYaw(k));
                }
            }
        }
    }

    private static void onPhase(Minecraft mc, DomainClashEntity c, int from, int to) {
        if (to == DomainClashEntity.PHASE_RESOLVE) {
            // the clash is over: white flash (0.25 s in, 1 s hold, 0.5 s out)
            fade(5, 20, 10, 1F, 1F, 1F);
            shake(1.0, 4);
        } else if (to == DomainClashEntity.PHASE_ARENA) {
            releaseCamera();
        }
    }

    // ------------------------------------------------------------------ overlay

    private static void renderOverlay(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        if (FADES.isEmpty()) return;
        double now = tickCount + partialTick;
        float best = 0F;
        float r = 0, gr = 0, b = 0;
        for (int i = FADES.size() - 1; i >= 0; i--) {
            Fade f = FADES.get(i);
            double t = now - f.start;
            double a;
            if (t < 0) continue;
            if (t < f.in) a = f.in <= 0 ? 1 : t / f.in;
            else if (t < f.in + f.hold) a = 1;
            else if (t < f.in + f.hold + f.out) a = 1 - (t - f.in - f.hold) / f.out;
            else {
                if (t > f.in + f.hold + f.out + 5) FADES.remove(i);
                continue;
            }
            if (a > best) {
                best = (float) a;
                r = f.r;
                gr = f.g;
                b = f.b;
            }
        }
        if (best <= 0.002F) return;
        int a = Mth.clamp((int) (best * 255F), 0, 255);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        g.fill(0, 0, w, h, (a << 24) | (Mth.clamp((int) (r * 255F), 0, 255) << 16)
                | (Mth.clamp((int) (gr * 255F), 0, 255) << 8) | Mth.clamp((int) (b * 255F), 0, 255));
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
