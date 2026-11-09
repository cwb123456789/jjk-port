package com.jujutsuenhancement.client.bedrock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Minecraft-free simulation of Bedrock particle emitters. Everything lives in "scene space"; the renderer
 * decides how scene space maps into the real world. Time is in seconds, like Bedrock's particle variables.
 */
public final class ParticleSim {
    private static final Random RNG = new Random();

    /** Bedrock's billboard size is a half extent, so the quad is twice the written size. */
    public static double SIZE_SCALE = 2.0;

    public static final class Emitter {
        final ParticleDef def;
        final double x, y, z;
        double age;
        final double lifetime;
        final double[] rnd = new double[4];
        boolean burstDone;
        double acc;
        int alive;

        Emitter(ParticleDef def, double x, double y, double z) {
            this.def = def;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lifetime = def.activeTime;
            for (int i = 0; i < 4; i++) rnd[i] = RNG.nextDouble();
        }
    }

    public static final class Particle {
        Emitter em;
        ParticleDef def;
        double x, y, z;
        double vx, vy, vz;
        double dx, dy, dz;
        double age, life;
        double rot, rotRate;
        final double[] rnd = new double[4];
    }

    /** Receives finished quads in scene space. {@code c} holds four corners (12 numbers), counter-clockwise from the viewer. */
    public interface QuadSink {
        void quad(ParticleDef def, double distSq, double[] c, float u0, float v0, float u1, float v1,
                  float r, float g, float b, float a);
    }

    private final List<Emitter> emitters = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final Molang.Ctx ctx = new Molang.Ctx();
    private final double[] tint = new double[4];
    private final double[] corners = new double[12];

    public int particleCount() {
        return particles.size();
    }

    public void clear() {
        emitters.clear();
        particles.clear();
    }

    public void spawn(ParticleDef def, double x, double y, double z) {
        emitters.add(new Emitter(def, x, y, z));
    }

    private void fillEmitterCtx(Emitter e) {
        ctx.emitterAge = e.age;
        ctx.emitterLifetime = e.lifetime;
        System.arraycopy(e.rnd, 0, ctx.emitterRandom, 0, 4);
    }

    private void spawnParticle(Emitter e) {
        ParticleDef d = e.def;
        Particle p = new Particle();
        p.em = e;
        p.def = d;
        for (int i = 0; i < 4; i++) p.rnd[i] = RNG.nextDouble();
        fillEmitterCtx(e);
        System.arraycopy(p.rnd, 0, ctx.random, 0, 4);
        ctx.age = 0;
        ctx.lifetime = 1;
        p.life = Math.max(0.0001, d.lifetime.eval(ctx));
        ctx.lifetime = p.life;

        double ox = d.offset[0].eval(ctx), oy = d.offset[1].eval(ctx), oz = d.offset[2].eval(ctx);
        double px = 0, py = 0, pz = 0;
        double nx = 0, ny = 0, nz = 0;
        switch (d.shape) {
            case SPHERE: {
                double r = d.radius.eval(ctx);
                double[] u = randomUnit();
                double rr = d.surfaceOnly ? r : r * Math.cbrt(RNG.nextDouble());
                px = u[0] * rr;
                py = u[1] * rr;
                pz = u[2] * rr;
                nx = u[0];
                ny = u[1];
                nz = u[2];
                break;
            }
            case DISC: {
                double r = d.radius.eval(ctx);
                double[] n = normalize(d.planeNormal[0], d.planeNormal[1], d.planeNormal[2]);
                double[] a = perpendicular(n);
                double[] b = cross(n, a);
                double ang = RNG.nextDouble() * Math.PI * 2;
                double rr = d.surfaceOnly ? r : r * Math.sqrt(RNG.nextDouble());
                double ca = Math.cos(ang), sa = Math.sin(ang);
                nx = a[0] * ca + b[0] * sa;
                ny = a[1] * ca + b[1] * sa;
                nz = a[2] * ca + b[2] * sa;
                px = nx * rr;
                py = ny * rr;
                pz = nz * rr;
                break;
            }
            case BOX: {
                double hx = d.halfDimensions[0].eval(ctx), hy = d.halfDimensions[1].eval(ctx), hz = d.halfDimensions[2].eval(ctx);
                px = (RNG.nextDouble() * 2 - 1) * hx;
                py = (RNG.nextDouble() * 2 - 1) * hy;
                pz = (RNG.nextDouble() * 2 - 1) * hz;
                double[] n = normalize(px, py, pz);
                nx = n[0];
                ny = n[1];
                nz = n[2];
                break;
            }
            default:
                break;
        }
        p.x = e.x + ox + px;
        p.y = e.y + oy + py;
        p.z = e.z + oz + pz;

        switch (d.dirMode) {
            case OUTWARDS:
                p.dx = nx;
                p.dy = ny;
                p.dz = nz;
                break;
            case INWARDS:
                p.dx = -nx;
                p.dy = -ny;
                p.dz = -nz;
                break;
            default:
                {
                    // custom direction vectors are normalised by Bedrock before being scaled by the initial speed
                    double[] n = normalize(d.dir[0].eval(ctx), d.dir[1].eval(ctx), d.dir[2].eval(ctx));
                    p.dx = n[0];
                    p.dy = n[1];
                    p.dz = n[2];
                }
                break;
        }
        double sp = d.speed.eval(ctx);
        p.vx = p.dx * sp;
        p.vy = p.dy * sp;
        p.vz = p.dz * sp;
        p.rot = d.spinRotation.eval(ctx);
        p.rotRate = d.spinRate.eval(ctx);
        particles.add(p);
        e.alive++;
    }

    /** Advance every emitter and particle by {@code dt} seconds. */
    public void step(double dt) {
        for (Emitter e : emitters) {
            ParticleDef d = e.def;
            if (d.instant) {
                if (!e.burstDone) {
                    e.burstDone = true;
                    fillEmitterCtx(e);
                    int n = (int) Math.round(d.count.eval(ctx));
                    for (int i = 0; i < n; i++) spawnParticle(e);
                }
            } else if (e.age < e.lifetime) {
                fillEmitterCtx(e);
                e.acc += d.steadyRate.eval(ctx) * dt;
                int max = (int) d.steadyMax.eval(ctx);
                while (e.acc >= 1.0) {
                    e.acc -= 1.0;
                    if (e.alive < max) spawnParticle(e);
                }
            }
            e.age += dt;
        }
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            ParticleDef d = p.def;
            fillEmitterCtx(p.em);
            System.arraycopy(p.rnd, 0, ctx.random, 0, 4);
            ctx.age = p.age;
            ctx.lifetime = p.life;
            double ax = d.accel[0].eval(ctx), ay = d.accel[1].eval(ctx), az = d.accel[2].eval(ctx);
            double drag = d.drag.eval(ctx);
            p.vx += (ax - drag * p.vx) * dt;
            p.vy += (ay - drag * p.vy) * dt;
            p.vz += (az - drag * p.vz) * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.rot += p.rotRate * dt;
            p.age += dt;
            if (p.age >= p.life) {
                p.em.alive--;
                it.remove();
            }
        }
        emitters.removeIf(e -> e.age >= e.lifetime && e.alive <= 0 && (!e.def.instant || e.burstDone));
    }

    // ------------------------------------------------------------------ geometry

    /**
     * Builds quads for every live particle. The camera is given in scene space: position plus the right/up/forward
     * axes of the view.
     */
    public void render(double[] cam, double[] right, double[] up, double[] fwd, QuadSink sink) {
        for (Particle p : particles) {
            ParticleDef d = p.def;
            fillEmitterCtx(p.em);
            System.arraycopy(p.rnd, 0, ctx.random, 0, 4);
            ctx.age = p.age;
            ctx.lifetime = p.life;
            double sx = d.size[0].eval(ctx) * SIZE_SCALE * 0.5;
            double sy = d.size[1].eval(ctx) * SIZE_SCALE * 0.5;
            if (sx == 0 || sy == 0) continue;
            d.tint(ctx, tint);
            if (tint[3] <= 0.002) continue;

            double[] ex = new double[3], ey = new double[3];
            axes(p, d, cam, right, up, fwd, ex, ey);
            double rotRad = Math.toRadians(p.rot);
            if (rotRad != 0) {
                double c = Math.cos(rotRad), s = Math.sin(rotRad);
                double[] nx = new double[3], ny = new double[3];
                for (int i = 0; i < 3; i++) {
                    nx[i] = ex[i] * c + ey[i] * s;
                    ny[i] = -ex[i] * s + ey[i] * c;
                }
                ex = nx;
                ey = ny;
            }
            // corners: (-,-), (+,-), (+,+), (-,+)
            for (int k = 0; k < 4; k++) {
                double kx = (k == 1 || k == 2) ? 1 : -1;
                double ky = (k >= 2) ? 1 : -1;
                corners[k * 3] = p.x + ex[0] * sx * kx + ey[0] * sy * ky;
                corners[k * 3 + 1] = p.y + ex[1] * sx * kx + ey[1] * sy * ky;
                corners[k * 3 + 2] = p.z + ex[2] * sx * kx + ey[2] * sy * ky;
            }

            // uv
            double u, v, uw, vh;
            if (d.flipbook) {
                double maxFrame = Math.max(1, d.fbMaxFrame.eval(ctx));
                double frame = d.fbStretch ? (p.age / p.life) * maxFrame : p.age * d.fbFps;
                frame = Math.floor(frame);
                if (d.fbLoop) frame = frame % maxFrame;
                else frame = Math.min(frame, maxFrame - 1);
                u = d.fbBase[0] + d.fbStep[0] * frame;
                v = d.fbBase[1] + d.fbStep[1] * frame;
                uw = d.fbSize[0];
                vh = d.fbSize[1];
            } else {
                u = d.uv[0].eval(ctx);
                v = d.uv[1].eval(ctx);
                uw = d.uvSize[0].eval(ctx);
                vh = d.uvSize[1].eval(ctx);
            }
            float u0 = (float) (u / d.texW), v0 = (float) (v / d.texH);
            float u1 = (float) ((u + uw) / d.texW), v1 = (float) ((v + vh) / d.texH);

            double ddx = p.x - cam[0], ddy = p.y - cam[1], ddz = p.z - cam[2];
            sink.quad(d, ddx * ddx + ddy * ddy + ddz * ddz, corners, u0, v0, u1, v1,
                    (float) clamp01(tint[0]), (float) clamp01(tint[1]), (float) clamp01(tint[2]), (float) clamp01(tint[3]));
        }
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static void axes(Particle p, ParticleDef d, double[] cam, double[] right, double[] up, double[] fwd,
                             double[] ex, double[] ey) {
        switch (d.facing) {
            case ROTATE_XYZ:
                set(ex, right);
                set(ey, up);
                return;
            case ROTATE_Y: {
                double[] f = normalize(fwd[0], 0, fwd[2]);
                if (f[0] == 0 && f[2] == 0) f = new double[] {0, 0, 1};
                double[] r = normalize(-f[2], 0, f[0]); // right of f
                set(ex, new double[] {-r[0], 0, -r[2]});
                set(ey, new double[] {0, 1, 0});
                return;
            }
            case LOOKAT_XYZ:
            case LOOKAT_Y: {
                double[] z = normalize(cam[0] - p.x, d.facing == ParticleDef.Facing.LOOKAT_Y ? 0 : cam[1] - p.y, cam[2] - p.z);
                if (z[0] == 0 && z[1] == 0 && z[2] == 0) {
                    set(ex, right);
                    set(ey, up);
                    return;
                }
                double[] x = cross(new double[] {0, 1, 0}, z);
                if (len(x) < 1e-6) x = right.clone();
                x = normalize(x[0], x[1], x[2]);
                double[] y = cross(z, x);
                set(ex, x);
                set(ey, y);
                return;
            }
            case LOOKAT_DIRECTION: {
                double[] dir = direction(p, d);
                if (dir == null) {
                    set(ex, right);
                    set(ey, up);
                    return;
                }
                double[] toCam = normalize(cam[0] - p.x, cam[1] - p.y, cam[2] - p.z);
                double dot = toCam[0] * dir[0] + toCam[1] * dir[1] + toCam[2] * dir[2];
                double[] z = normalize(toCam[0] - dir[0] * dot, toCam[1] - dir[1] * dot, toCam[2] - dir[2] * dot);
                if (len(z) < 1e-6) {
                    set(ex, right);
                    set(ey, up);
                    return;
                }
                set(ex, dir);
                set(ey, cross(z, dir));
                return;
            }
            default: {
                // direction_x / direction_y / direction_z: the particle's own axis is rotated onto its direction
                double[] dir = direction(p, d);
                double[] x = {1, 0, 0}, y = {0, 1, 0}, z = {0, 0, 1};
                if (dir != null) {
                    double[] from = d.facing == ParticleDef.Facing.DIR_X ? x : d.facing == ParticleDef.Facing.DIR_Y ? y : z;
                    double[][] m = arc(from, dir);
                    x = mul(m, x);
                    y = mul(m, y);
                }
                set(ex, x);
                set(ey, y);
            }
        }
    }

    /** Normalised direction of travel, falling back to the initial direction; null when there is none. */
    private static double[] direction(Particle p, ParticleDef d) {
        double sp = Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
        if (sp > d.minSpeedThreshold) return new double[] {p.vx / sp, p.vy / sp, p.vz / sp};
        double l = Math.sqrt(p.dx * p.dx + p.dy * p.dy + p.dz * p.dz);
        if (l > 1e-9) return new double[] {p.dx / l, p.dy / l, p.dz / l};
        return null;
    }

    // ------------------------------------------------------------------ small vector helpers

    private static void set(double[] to, double[] from) {
        to[0] = from[0];
        to[1] = from[1];
        to[2] = from[2];
    }

    private static double len(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    static double[] normalize(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return l < 1e-12 ? new double[] {0, 0, 0} : new double[] {x / l, y / l, z / l};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] perpendicular(double[] n) {
        double[] t = Math.abs(n[1]) < 0.9 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
        double[] a = cross(n, t);
        return normalize(a[0], a[1], a[2]);
    }

    private static double[] randomUnit() {
        double z = RNG.nextDouble() * 2 - 1;
        double t = RNG.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(1 - z * z);
        return new double[] {r * Math.cos(t), r * Math.sin(t), z};
    }

    /** Shortest-arc rotation matrix taking unit vector a onto unit vector b. */
    private static double[][] arc(double[] a, double[] b) {
        double[] v = cross(a, b);
        double c = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        if (c < -0.999999) {
            double[] axis = perpendicular(a);
            // 180 degrees about any axis perpendicular to a
            return new double[][] {
                    {2 * axis[0] * axis[0] - 1, 2 * axis[0] * axis[1], 2 * axis[0] * axis[2]},
                    {2 * axis[1] * axis[0], 2 * axis[1] * axis[1] - 1, 2 * axis[1] * axis[2]},
                    {2 * axis[2] * axis[0], 2 * axis[2] * axis[1], 2 * axis[2] * axis[2] - 1}};
        }
        double k = 1.0 / (1.0 + c);
        return new double[][] {
                {c + v[0] * v[0] * k, v[0] * v[1] * k - v[2], v[0] * v[2] * k + v[1]},
                {v[1] * v[0] * k + v[2], c + v[1] * v[1] * k, v[1] * v[2] * k - v[0]},
                {v[2] * v[0] * k - v[1], v[2] * v[1] * k + v[0], c + v[2] * v[2] * k}};
    }

    private static double[] mul(double[][] m, double[] v) {
        return new double[] {
                m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }
}
