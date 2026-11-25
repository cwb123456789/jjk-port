package com.jujutsuenhancement.client.bedrock;

import com.jujutsuenhancement.JujutsuEnhancement;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The addon's free camera ({@code camera @s set minecraft:free [ease t type] pos .. facing|rot ..} and {@code camera @s clear}),
 * shared by every cutscene: a camera marker is placed at the position (the addon's ^left ^up ^forward frame of the caster, taken the
 * tick the command runs) and eased to each new shot with the named easing curve.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CgCamera {
    private static Marker marker;
    private static boolean owned;
    private static CameraType savedType;
    private static double fx, fy, fz, fyaw, fpitch;   // pose the current ease starts from
    private static double tx, ty, tz, tyaw, tpitch;   // the shot being eased to
    private static int easeStart, easeTicks;
    private static String easeName = "linear";
    private static int tick;
    private static int lastShot;
    private static Entity owner;

    private CgCamera() {}

    public static boolean active() {
        return owned;
    }

    /** The addon's local frame of {@code e}: ^left ^up ^forward from {@code origin}. */
    private static Vec3 local(Entity e, Vec3 origin, double l, double u, double f) {
        double y = Math.toRadians(e.getYRot()), p = Math.toRadians(e.getXRot());
        Vec3 fwd = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        Vec3 left = new Vec3(Math.cos(y), 0.0D, Math.sin(y));
        Vec3 up = new Vec3(-Math.sin(y) * Math.sin(p), Math.cos(p), Math.cos(y) * Math.sin(p));
        return origin.add(left.scale(l)).add(up.scale(u)).add(fwd.scale(f));
    }

    /**
     * One {@code camera set} row of a move timeline: [eyes, ease seconds, ease name, pos kind (1 = ^), px, py, pz,
     * aim kind (0 none, 1 facing ^, 2 facing ~, 3 rot: a pitch, b yaw, c pitch is relative, d yaw is relative), a, b, c, d].
     */
    public static void set(Entity e, boolean eyes, double easeSec, String ease, boolean posLocal, double px, double py, double pz,
            int aim, double a, double b, double c, double d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 origin = e.position();
        Vec3 anchor = eyes ? e.getEyePosition() : origin;
        Vec3 pos = posLocal ? local(e, anchor, px, py, pz) : origin.add(px, py, pz);
        double yaw, pitch;
        switch (aim) {
            case 1, 2 -> {
                Vec3 look = aim == 1 ? local(e, anchor, a, b, c) : origin.add(a, b, c);
                Vec3 dv = look.subtract(pos);
                double h = Math.sqrt(dv.x * dv.x + dv.z * dv.z);
                yaw = Math.toDegrees(Math.atan2(-dv.x, dv.z));
                pitch = -Math.toDegrees(Math.atan2(dv.y, h));
            }
            case 3 -> {
                // Bedrock's `rot <x rotation (pitch)> <y rotation (yaw)>`, each absolute or relative (~) to the caster
                pitch = c > 0.5 ? e.getXRot() + a : a;
                yaw = d > 0.5 ? e.getYRot() + b : b;
            }
            default -> {
                yaw = owned ? tyaw : e.getYRot();
                pitch = owned ? tpitch : e.getXRot();
            }
        }
        acquire(mc, e);
        // the ease starts from the pose the camera has now
        double[] now = pose(0.0F);
        fx = now[0]; fy = now[1]; fz = now[2]; fyaw = now[3]; fpitch = now[4];
        tx = pos.x; ty = pos.y; tz = pos.z; tyaw = yaw; tpitch = Mth.clamp(pitch, -90, 90);
        easeStart = tick;
        easeTicks = (int) Math.round(easeSec * 20.0D);
        easeName = ease == null ? "linear" : ease;
        if (easeTicks <= 0) {
            fx = tx; fy = ty; fz = tz; fyaw = tyaw; fpitch = tpitch;
        }
        lastShot = tick;
        place(0.0F);
    }

    public static void clear() {
        if (!owned) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (savedType != null) mc.options.setCameraType(savedType);
        owned = false;
        owner = null;
    }

    private static void acquire(Minecraft mc, Entity e) {
        if (owned) return;
        if (marker == null || marker.level() != mc.level) marker = new Marker(EntityType.MARKER, mc.level);
        Vec3 cp = mc.gameRenderer.getMainCamera().getPosition();
        marker.setPos(cp.x, cp.y, cp.z);
        fx = cp.x; fy = cp.y; fz = cp.z;
        fyaw = mc.gameRenderer.getMainCamera().getYRot();
        fpitch = mc.gameRenderer.getMainCamera().getXRot();
        tx = fx; ty = fy; tz = fz; tyaw = fyaw; tpitch = fpitch;
        savedType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(marker);
        owned = true;
        owner = e;
    }

    private static double[] pose(float partial) {
        if (easeTicks <= 0) return new double[] {tx, ty, tz, tyaw, tpitch};
        double u = Mth.clamp((tick + partial - easeStart) / (double) easeTicks, 0.0D, 1.0D);
        double k = ease(easeName, u);
        double dyaw = Mth.wrapDegrees(tyaw - fyaw);
        return new double[] {fx + (tx - fx) * k, fy + (ty - fy) * k, fz + (tz - fz) * k, fyaw + dyaw * k, fpitch + (tpitch - fpitch) * k};
    }

    private static void place(float partial) {
        if (marker == null) return;
        double[] p = pose(partial);
        marker.setPos(p[0], p[1], p[2]);
        marker.xo = marker.xOld = p[0];
        marker.yo = marker.yOld = p[1];
        marker.zo = marker.zOld = p[2];
        marker.setXRot((float) Mth.clamp(p[4], -90, 90));
        marker.setYRot((float) p[3]);
        marker.xRotO = marker.getXRot();
        marker.yRotO = marker.getYRot();
    }

    /** The Bedrock easing curves (easing type of {@code camera ... ease <time> <type>}). */
    static double ease(String name, double t) {
        String n = name == null ? "linear" : name;
        if (n.equals("linear") || n.equals("spring")) return t;
        boolean in = n.startsWith("in_") && !n.startsWith("in_out_");
        boolean out = n.startsWith("out_") && !n.startsWith("out_in_");
        boolean inOut = n.startsWith("in_out_");
        String f = n.replaceFirst("^(in_out_|out_in_|in_|out_)", "");
        if (inOut) return t < 0.5 ? 0.5 * fIn(f, t * 2) : 1 - 0.5 * fIn(f, (1 - t) * 2);
        if (out) return 1 - fIn(f, 1 - t);
        if (in) return fIn(f, t);
        return t;
    }

    private static double fIn(String f, double t) {
        switch (f) {
            case "sine": return 1 - Math.cos(t * Math.PI / 2);
            case "quad": return t * t;
            case "cubic": return t * t * t;
            case "quart": return t * t * t * t;
            case "quint": return t * t * t * t * t;
            case "expo": return t == 0 ? 0 : Math.pow(2, 10 * t - 10);
            case "circ": return 1 - Math.sqrt(1 - t * t);
            case "back": return 2.70158 * t * t * t - 1.70158 * t * t;
            case "elastic": return t == 0 || t == 1 ? t : -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * (2 * Math.PI / 3));
            case "bounce": {
                double u = 1 - t, v;
                if (u < 1 / 2.75) v = 7.5625 * u * u;
                else if (u < 2 / 2.75) { u -= 1.5 / 2.75; v = 7.5625 * u * u + 0.75; }
                else if (u < 2.5 / 2.75) { u -= 2.25 / 2.75; v = 7.5625 * u * u + 0.9375; }
                else { u -= 2.625 / 2.75; v = 7.5625 * u * u + 0.984375; }
                return 1 - v;
            }
            default: return t;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            owned = false;
            return;
        }
        if (mc.isPaused()) return;
        tick++;
        if (!owned) return;
        // a shot that nothing replaces for 40 s, or a caster that is gone, ends the cutscene
        if (owner != null && (owner.isRemoved() || !owner.isAlive()) && tick - lastShot > 40) clear();
        else if (tick - lastShot > 800) clear();
        else if (mc.getCameraEntity() != marker) owned = false;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START && owned) place(event.renderTickTime);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (owned) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        owned = false;
        marker = null;
    }
}
