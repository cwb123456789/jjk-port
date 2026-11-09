package com.jujutsuenhancement.client.bedrock;

/**
 * The cinematic script of the Bedrock addon's Gojo domain ("无量空处"), transcribed from
 * {@code gojo_domain.mcfunction}, {@code gojo_domain_quick.mcfunction}, {@code unlimited_start.mcfunction},
 * {@code unlimited_void.mcfunction} and the {@code unlimited_void_inside} animation.
 * <p>
 * All times are ticks since the cast started ("e"). Positions are world coordinates; the scene origin
 * {@code O} sits 2.25 blocks below Gojo's feet, exactly like the addon's arena (y 298.85 against Gojo's 301.1).
 * No Minecraft classes are used so that the script can be run offline.
 */
public final class VoidScript {
    public enum Kind { FULL, QUICK }

    public interface Spawner {
        void spawn(String particle, double x, double y, double z);
    }

    /** Camera pose in world space: position plus Minecraft yaw / pitch in degrees. */
    public static final class Pose {
        public double x, y, z, pitch, yaw;

        public Pose copy() {
            Pose p = new Pose();
            p.x = x;
            p.y = y;
            p.z = z;
            p.pitch = pitch;
            p.yaw = yaw;
            return p;
        }
    }

    private static final class Key {
        final int t;
        final double ease; // ticks
        final boolean circ;
        final boolean arenaFrame;
        final boolean lookAt;
        final double x, y, z, pitch, yaw;

        Key(int t, double easeSeconds, boolean circ, boolean arenaFrame, boolean lookAt,
            double x, double y, double z, double pitch, double yaw) {
            this.t = t;
            this.ease = easeSeconds * 20.0;
            this.circ = circ;
            this.arenaFrame = arenaFrame;
            this.lookAt = lookAt;
            this.x = x;
            this.y = y;
            this.z = z;
            this.pitch = pitch;
            this.yaw = yaw;
        }
    }

    private static final class Fade {
        final int t;
        final double in, hold, out; // ticks
        final float r, g, b;

        Fade(int t, double in, double hold, double out, int r, int g, int b) {
            this.t = t;
            this.in = in * 20;
            this.hold = hold * 20;
            this.out = out * 20;
            this.r = r / 255f;
            this.g = g / 255f;
            this.b = b / 255f;
        }
    }

    public final Kind kind;
    /** Gojo's feet when the cast started. */
    public final double fx, fy, fz;
    public final double eyeHeight;
    /** Gojo's rotation when the cast started (degrees). */
    public final double yaw0, pitch0;

    private final Key[] keys;
    private final Fade[] fades;
    /** Tick at which the world behind the scene is replaced by black, and at which Gojo is turned to face +z. */
    public final int wipeTick;
    public final int turnTick;
    /** First tick of the free-roaming (player controlled) view. */
    public final int cinematicEnd;
    /** Tick at which the arena entity (and its particles) starts. */
    public final int arenaTick;

    public VoidScript(Kind kind, double fx, double fy, double fz, double eyeHeight, double yaw0, double pitch0) {
        this.kind = kind;
        this.fx = fx;
        this.fy = fy;
        this.fz = fz;
        this.eyeHeight = eyeHeight;
        this.yaw0 = yaw0;
        this.pitch0 = pitch0;
        if (kind == Kind.FULL) {
            wipeTick = 205;
            turnTick = 201;
            cinematicEnd = 755;
            arenaTick = 454;
            keys = new Key[] {
                    new Key(0, 5, true, false, false, 0.11, -0.18, 0.85, -21, 180),
                    new Key(211, 0, false, true, false, 0, 0, -4, 0, 180),
                    new Key(216, 1.5, false, true, false, 0, 0.25, -1, 5, 180),
                    new Key(248, 3, false, true, false, 0, 2, 8, 15, 0),
                    new Key(309, 0, false, true, false, 0, -0.4, 5, -15, 180),
                    new Key(330, 6.65, false, true, false, 0, 0, 16, 0, 180),
                    new Key(454, 0, false, true, false, 0, 0, 15, 0, 0),
                    new Key(455, 10, false, true, false, 0, 0, 10, 0, 0),
                    new Key(656, 0, false, true, false, 7, 15, -15, -15, -35),
                    new Key(657, 2.5, true, true, false, 6, 16, -14, 30, 65),
                    new Key(706, 0, false, true, false, -7, 3, 15, 15, 135),
                    new Key(707, 2.5, true, true, false, -6, 1, 14, 0, 225),
            };
            fades = new Fade[] {
                    new Fade(0, 0.25, 0.5, 0.5, 0, 0, 0),
                    new Fade(135, 2.75, 1.15, 0.1, 255, 255, 255),
                    new Fade(369, 4.5, 3.5, 1, 252, 252, 255),
                    new Fade(740, 0.5, 0.5, 0.25, 0, 0, 5),
            };
        } else {
            wipeTick = 30;
            turnTick = 34;
            cinematicEnd = 96;
            arenaTick = 80;
            keys = new Key[] {
                    new Key(0, 0.35, true, false, true, 0, -0.25, 3, 0, 0),
                    new Key(20, 0, false, false, true, 0, 0, 2, 0, 0),
                    new Key(41, 1.5, true, false, true, 0, 0, 12, 0, 0),
            };
            fades = new Fade[] {
                    new Fade(10, 0.5, 1, 0.5, 255, 255, 255),
                    new Fade(80, 0.5, 0.5, 0.5, 255, 255, 255),
            };
        }
    }

    public boolean isWiped(int e) {
        return e >= wipeTick;
    }

    // ------------------------------------------------------------------ camera

    private double[] frame(boolean arena) {
        double yaw = arena ? 0 : yaw0;
        double pitch = arena ? 0 : pitch0;
        double cy = Math.cos(Math.toRadians(yaw)), sy = Math.sin(Math.toRadians(yaw));
        double cp = Math.cos(Math.toRadians(pitch)), sp = Math.sin(Math.toRadians(pitch));
        // left, up, forward (Minecraft's ^ axes)
        return new double[] {
                cy, 0, sy,
                sy * sp, cp, -cy * sp,
                -sy * cp, -sp, cy * cp};
    }

    private Pose target(Key k) {
        double[] f = frame(k.arenaFrame);
        Pose p = new Pose();
        double ex = fx, ey = fy + eyeHeight, ez = fz;
        p.x = ex + f[0] * k.x + f[3] * k.y + f[6] * k.z;
        p.y = ey + f[1] * k.x + f[4] * k.y + f[7] * k.z;
        p.z = ez + f[2] * k.x + f[5] * k.y + f[8] * k.z;
        if (k.lookAt) {
            double dx = ex - p.x, dy = ey - p.y, dz = ez - p.z;
            p.yaw = Math.toDegrees(Math.atan2(-dx, dz));
            p.pitch = Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        } else {
            p.yaw = (k.arenaFrame ? 0 : yaw0) + k.yaw;
            p.pitch = (k.arenaFrame ? 0 : pitch0) + k.pitch;
        }
        return p;
    }

    private static double ease(double t, boolean circ) {
        t = Math.max(0, Math.min(1, t));
        if (!circ) return t;
        return t < 0.5 ? (1 - Math.sqrt(1 - 4 * t * t)) / 2 : (Math.sqrt(1 - (-2 * t + 2) * (-2 * t + 2)) + 1) / 2;
    }

    private static double wrapNear(double v, double ref) {
        while (v - ref > 180) v -= 360;
        while (v - ref < -180) v += 360;
        return v;
    }

    private static Pose lerp(Pose a, Pose b, double t, boolean shortest) {
        Pose p = new Pose();
        p.x = a.x + (b.x - a.x) * t;
        p.y = a.y + (b.y - a.y) * t;
        p.z = a.z + (b.z - a.z) * t;
        double by = shortest ? wrapNear(b.yaw, a.yaw) : b.yaw;
        p.yaw = a.yaw + (by - a.yaw) * t;
        p.pitch = a.pitch + (b.pitch - a.pitch) * t;
        return p;
    }

    private Pose segment(Key k, Pose from, double e, boolean shortest) {
        Pose to = target(k);
        if (k.ease <= 0 || e >= k.t + k.ease) return to;
        return lerp(from, to, ease((e - k.t) / k.ease, k.circ), shortest);
    }

    /** Camera pose at time {@code e} (ticks, fractional) starting from the player's own view {@code start}. */
    public Pose camera(double e, Pose start) {
        Pose cur = start;
        for (int i = 0; i < keys.length; i++) {
            Key k = keys[i];
            if (e < k.t) break;
            boolean first = i == 0;
            Pose from = cur;
            boolean last = i == keys.length - 1 || e < keys[i + 1].t;
            if (last) return segment(k, from, e, first);
            cur = segment(k, from, keys[i + 1].t, first);
        }
        return cur;
    }

    // ------------------------------------------------------------------ overlay

    /** Fade overlay colour and alpha at time {@code e}; the newest fade that has started wins. */
    public float[] fade(double e) {
        float[] out = {0, 0, 0, 0};
        for (int i = fades.length - 1; i >= 0; i--) {
            Fade f = fades[i];
            if (e < f.t) continue;
            double t = e - f.t;
            double a;
            if (t < f.in) a = f.in <= 0 ? 1 : t / f.in;
            else if (t < f.in + f.hold) a = 1;
            else if (t < f.in + f.hold + f.out) a = 1 - (t - f.in - f.hold) / f.out;
            else a = 0;
            out[0] = f.r;
            out[1] = f.g;
            out[2] = f.b;
            out[3] = (float) a;
            return out;
        }
        return out;
    }

    /** Rotational camera shake intensity added at tick {@code e}. */
    public double shake(int e) {
        int d; // value of UnlimitedVoidStart's Deleter counter
        if (kind == Kind.FULL) {
            if (e < 212) return 0;
            d = 551 - (e - 212);
        } else {
            if (e < 33) return 0;
            d = 561 - (e - 33);
        }
        if (d >= 448 && d <= 545) return 0.05;
        if (d >= 408 && d <= 447) return 0.1;
        if (d >= 358 && d <= 407) return 0.15;
        if (d >= 308 && d <= 357) return 0.2;
        if (d >= 258 && d <= 307) return 0.3;
        return 0;
    }

    // ------------------------------------------------------------------ particles

    private double ox() {
        return fx;
    }

    private double oy() {
        return fy - 2.25;
    }

    private double oz() {
        return fz;
    }

    /** Particles spawned on tick {@code e} (called once per game tick, in order). */
    public void onTick(int e, Spawner s) {
        double ox = ox(), oy = oy(), oz = oz();
        if (kind == Kind.FULL) {
            if (e >= 135 && e <= 160) {
                s.spawn("infinite_void_whitev1", fx, fy, fz);
                s.spawn("infinite_void_whitev1", fx, fy, fz);
            }
            if (e >= 160 && e <= 190) s.spawn("infinite_void_whitev2", fx, fy, fz);
            if (e >= 212 && e <= 216) s.spawn("infinite_void_whitev2", ox, oy + 1, oz);
            if (e >= 218 && e <= 222) s.spawn("infinite_void_whitev3", ox, oy + 1, oz);
            if (e >= 218 && e <= 505) enter(s, ox, oy, oz);
        } else {
            if (e >= 10 && e <= 30) {
                s.spawn("infinite_void_whitev1_small", fx, fy, fz);
                s.spawn("infinite_void_whitev2", fx, fy, fz);
            }
            if (e >= 32 && e <= 47) s.spawn("infinite_void_whitev2", ox, oy + 1, oz);
            if (e >= 49 && e <= 53) s.spawn("infinite_void_whitev3", ox, oy + 1, oz);
            if (e >= 49) enter(s, ox, oy, oz);
        }
        if (e == arenaTick) {
            // one-off burst when the arena entity is summoned, relative to Gojo himself
            s.spawn("unlimited_eye1", fx, fy + 1.5, fz + 26);
            s.spawn("unlimited_eye2", fx, fy + 1.5, fz + 26);
            s.spawn("unlimited_eye3", fx, fy + 1.5, fz + 26);
        }
        if (e > arenaTick) arena(e - arenaTick, s);
    }

    private static void enter(Spawner s, double ox, double oy, double oz) {
        s.spawn("unlimited_enter", ox, oy, oz);
        s.spawn("unlimited_enter2", ox, oy, oz);
        s.spawn("unlimited_enter3", ox, oy, oz);
        s.spawn("unlimited_enter_backgroundv1", ox, oy, oz);
        s.spawn("unlimited_enter_backgroundv2", ox, oy, oz);
        s.spawn("unlimited_enter_backgroundv3", ox, oy, oz);
        s.spawn("unlimited_enter_backgroundv4", ox, oy, oz);
    }

    /** Values of the arena entity's {@code Energy} / {@code Evade} counters as seen by its function on tick n. */
    static int energy(int n) {
        return n < 2 ? -1 : 14 - ((n - 2) % 14);
    }

    static int evade(int n) {
        return n < 2 ? -1 : 16 - ((n - 2) % 16);
    }

    private void arena(int n, Spawner s) {
        double ox = ox(), oy = oy(), oz = oz();
        s.spawn("unlimited_eye1", ox, oy + 1, oz + 35);
        s.spawn("unlimited_eye1c", ox, oy + 1, oz + 35.5);
        s.spawn("unlimited_eye1b", ox, oy + 1, oz + 36);
        s.spawn("unlimited_eye2", ox, oy + 1, oz + 35);
        s.spawn("unlimited_eye3", ox, oy + 1, oz + 36);
        int en = energy(n), ev = evade(n);
        if (en >= 7) s.spawn("unlimited_eye4", ox, oy + 1, oz + 32);
        if (en == 14) s.spawn("unlimited_eye5", ox, oy + 1, oz + 32);
        s.spawn("unlimited_void6", ox, oy + 1, oz + 32);
        if (en >= 7 && en <= 10) s.spawn("unlimited_void1b", ox, oy + 1, oz);
        if (ev == 16) s.spawn("unlimited_void_paint1", ox, oy + 1, oz);
        if (ev == 11) s.spawn("unlimited_void_paint2", ox, oy + 1, oz);
        if (ev == 6) s.spawn("unlimited_void_paint3", ox, oy + 1, oz);
        if (ev == 1) {
            s.spawn("unlimited_void_paint4", ox, oy + 1, oz);
            s.spawn("unlimited_void_paint5", ox, oy + 1, oz);
        }
        s.spawn("unlimited_void_floor", ox, oy + 1, oz);
        // the looping unlimited_void_inside animation
        for (String name : new String[] {"unlimited_void1", "unlimited_void2", "unlimited_void3", "unlimited_void4",
                "unlimited_void5", "unlimited_void7", "unlimited_void8", "unlimited_void9"}) {
            s.spawn(name, ox, oy, oz);
        }
    }

    // ------------------------------------------------------------------ ending

    /** Fade used when the domain ends (Deleter == 25 in the addon). */
    public static float[] breakFade(double k) {
        double in = 5, hold = 19, out = 30;
        double a;
        if (k < 0) a = 0;
        else if (k < in) a = k / in;
        else if (k < in + hold) a = 1;
        else if (k < in + hold + out) a = 1 - (k - in - hold) / out;
        else a = 0;
        return new float[] {1, 1, 1, (float) a};
    }

    /** Shards thrown out when the barrier shatters (k = ticks since the break started, 10..24). */
    public void onBreakTick(int k, Spawner s) {
        if (k >= 10 && k <= 24) {
            s.spawn("domain_smash1", fx, fy - 2.25, fz);
            s.spawn("domain_smash2", fx, fy - 2.25, fz);
            s.spawn("domain_smash3", fx, fy - 2.25, fz);
        }
    }
}
