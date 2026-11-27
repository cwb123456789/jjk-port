package com.jujutsuenhancement.bfn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** {@code scriptevent ds:trail_particle <x,y,z> radius:R amount:N speed:T <pattern> <particle> [spacing] [anchor] [time:N]} (projectiles_particles.js). */
final class TrailParticle {
    private static final Random R = new Random();
    private static final Set<String> ELECTRIC = new HashSet<>();

    private TrailParticle() {}

    static void reset() {
        ELECTRIC.clear();
    }

    private static double rnd() {
        return R.nextDouble();
    }

    private static double[] normalize(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l == 0) return new double[] {0, 1, 0};
        return new double[] {x / l, y / l, z / l};
    }

    private static double[] bez(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
        double t1 = 1 - t, a = t1 * t1 * t1, b = 3 * t1 * t1 * t, c = 3 * t1 * t * t, d = t * t * t;
        return new double[] {a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1], a * p0[2] + b * p1[2] + c * p2[2] + d * p3[2]};
    }

    private static double[] sphere() {
        double theta = rnd() * Math.PI * 2, phi = Math.acos(2 * rnd() - 1);
        return new double[] {Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta)};
    }

    static void run(McHost host, Ctx c, List<String> a) {
        if (c.self() == null || !(c.self().handle() instanceof Entity entity)) return;
        String[] args = String.join(" ", a).trim().split("\\s+");
        if (args.length < 6) return;
        double spacing = 0.25;
        if (args.length > 6) {
            try {
                double s = Double.parseDouble(args[6]);
                if (s != 0) spacing = s;
            } catch (NumberFormatException ex) {
                // the script's Number(x) || 0.25
            }
        }
        double lifetime = 0;
        boolean anchor = false;
        for (String s : args) {
            if (s.equals("anchor")) anchor = true;
            if (s.startsWith("time:")) {
                try {
                    lifetime = Double.parseDouble(s.substring(5));
                } catch (NumberFormatException ex) {
                    lifetime = 0;
                }
            }
        }
        String[] pp = args[0].split(",");
        if (pp.length != 3) return;
        double[] rel;
        double radius, amount, speed;
        try {
            rel = new double[] {Double.parseDouble(pp[0]), Double.parseDouble(pp[1]), Double.parseDouble(pp[2])};
            radius = Double.parseDouble(args[1].substring(7));
            amount = Double.parseDouble(args[2].substring(7));
            speed = Double.parseDouble(args[3].substring(6));
        } catch (RuntimeException ex) {
            return;
        }
        String pattern = args[4], particle = args[5];
        if (!particle.startsWith("ds:")) return;
        String pname = particle.substring(3);
        int dim = McHost.dimOf(entity.level());
        final double[] relF = rel;
        final boolean anchorF = anchor;
        java.util.function.Supplier<double[]> center = () -> {
            if (!entity.isAlive()) return null;
            double yaw = Math.toRadians(entity.getYRot()), pitch = Math.toRadians(entity.getXRot());
            double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
            double rx = Math.cos(yaw), rz = Math.sin(yaw);
            double ux = -Math.sin(yaw) * Math.sin(pitch), uy = Math.cos(pitch), uz = Math.cos(yaw) * Math.sin(pitch);
            return new double[] {entity.getX() + fx * relF[2] + rx * relF[0] + ux * relF[1], entity.getY() + fy * relF[2] + uy * relF[1],
                    entity.getZ() + fz * relF[2] + rz * relF[0] + uz * relF[1]};
        };
        double[] init = center.get();
        if (init == null) return;
        boolean outwards = pattern.startsWith("outwards_");
        String base = outwards ? pattern.substring(9) : pattern;
        boolean wiggle = base.equals("wiggle_line");

        if (base.equals("electric")) {
            electric(host, entity, dim, pname, radius, amount, spacing, lifetime, init);
            return;
        }
        for (int i = 0; i < amount; i++) {
            double[] sp = sphere();
            double[] start, end;
            if (outwards) {
                start = init.clone();
                double od = radius + rnd() * 10;
                end = new double[] {init[0] + sp[0] * od, init[1] + sp[1] * od, init[2] + sp[2] * od};
            } else {
                start = new double[] {init[0] + sp[0] * radius, init[1] + sp[1] * radius, init[2] + sp[2] * radius};
                end = init.clone();
            }
            double[] cp1, cp2;
            if (base.equals("curve")) {
                double[] rd = sphere();
                double bs = radius * 0.8;
                double mx = (start[0] + end[0]) / 2, my = (start[1] + end[1]) / 2, mz = (start[2] + end[2]) / 2;
                cp1 = new double[] {mx + rd[0] * bs * 0.7, my + rd[1] * bs * 0.7, mz + rd[2] * bs * 0.7};
                cp2 = new double[] {mx + rd[0] * bs * 0.3, my + rd[1] * bs * 0.3, mz + rd[2] * bs * 0.3};
            } else if (base.equals("spiral")) {
                double[] ax = normalize(end[0] - start[0], end[1] - start[1], end[2] - start[2]);
                double[] pv = {-ax[1], ax[0], 0};
                double pl = Math.sqrt(pv[0] * pv[0] + pv[1] * pv[1] + pv[2] * pv[2]);
                if (pl > 0.001) {
                    pv[0] /= pl;
                    pv[1] /= pl;
                    pv[2] /= pl;
                } else {
                    pv[0] = 1;
                    pv[1] = 0;
                    pv[2] = 0;
                }
                double sr = radius * 0.5, rot = 2 + rnd();
                double a1 = Math.PI * 2 * rot * 0.33, a2 = Math.PI * 2 * rot * 0.67;
                cp1 = new double[] {start[0] + (end[0] - start[0]) * 0.33 + (pv[0] * Math.cos(a1) - pv[2] * Math.sin(a1)) * sr,
                        start[1] + (end[1] - start[1]) * 0.33 + pv[1] * sr * Math.sin(a1),
                        start[2] + (end[2] - start[2]) * 0.33 + (pv[2] * Math.cos(a1) + pv[0] * Math.sin(a1)) * sr};
                cp2 = new double[] {start[0] + (end[0] - start[0]) * 0.67 + (pv[0] * Math.cos(a2) - pv[2] * Math.sin(a2)) * sr * 0.5,
                        start[1] + (end[1] - start[1]) * 0.67 + pv[1] * sr * Math.sin(a2) * 0.5,
                        start[2] + (end[2] - start[2]) * 0.67 + (pv[2] * Math.cos(a2) + pv[0] * Math.sin(a2)) * sr * 0.5};
            } else if (wiggle) {
                double[] ld = {end[0] - start[0], end[1] - start[1], end[2] - start[2]};
                double[] perp = Math.abs(ld[0]) < 0.9 ? new double[] {0, ld[2], -ld[1]} : new double[] {-ld[2], 0, ld[0]};
                double pl = Math.sqrt(perp[0] * perp[0] + perp[1] * perp[1] + perp[2] * perp[2]);
                double[] pn = {perp[0] / pl, perp[1] / pl, perp[2] / pl};
                double[] p2 = {ld[1] * pn[2] - ld[2] * pn[1], ld[2] * pn[0] - ld[0] * pn[2], ld[0] * pn[1] - ld[1] * pn[0]};
                double p2l = Math.sqrt(p2[0] * p2[0] + p2[1] * p2[1] + p2[2] * p2[2]);
                double[] pn2 = {p2[0] / p2l, p2[1] / p2l, p2[2] / p2l};
                double w = radius * 0.12;
                double w1x = (rnd() - 0.5) * 2 * w, w1y = (rnd() - 0.5) * 2 * w, w2x = (rnd() - 0.5) * 2 * w, w2y = (rnd() - 0.5) * 2 * w;
                cp1 = new double[3];
                cp2 = new double[3];
                for (int k = 0; k < 3; k++) {
                    cp1[k] = start[k] + (end[k] - start[k]) * 0.33 + pn[k] * w1x + pn2[k] * w1y;
                    cp2[k] = start[k] + (end[k] - start[k]) * 0.67 + pn[k] * w2x + pn2[k] * w2y;
                }
            } else {
                cp1 = new double[] {start[0] * 0.67 + end[0] * 0.33, start[1] * 0.67 + end[1] * 0.33, start[2] * 0.67 + end[2] * 0.33};
                cp2 = new double[] {start[0] * 0.33 + end[0] * 0.67, start[1] * 0.33 + end[1] * 0.67, start[2] * 0.33 + end[2] * 0.67};
            }
            trail(host, dim, pname, center, start, end, (int) speed, cp1, cp2, anchorF, wiggle);
        }
    }

    private static void trail(McHost host, int dim, String pname, java.util.function.Supplier<double[]> center, double[] from, double[] to, int duration,
                              double[] cp1, double[] cp2, boolean anchored, boolean wiggle) {
        final double[] lastT = {0};
        final int[] cur = {0};
        final double[][] lastCenter = {to.clone()};
        host.addTask(() -> {
            if (cur[0] >= duration) {
                double[] fin = anchored ? center.get() : to;
                if (fin != null) host.sendParticles(dim, pname, List.of(fin));
                return true;
            }
            double targetT = Math.min(1, (cur[0] + 1) / (double) duration);
            int steps = Math.max(5, (int) Math.ceil((targetT - lastT[0]) * 150));
            double[] cc = to;
            if (anchored) {
                double[] nc = center.get();
                if (nc != null) {
                    cc = nc;
                    lastCenter[0] = nc.clone();
                } else cc = lastCenter[0];
            }
            double ox = cc[0] - to[0], oy = cc[1] - to[1], oz = cc[2] - to[2];
            List<double[]> pts = new ArrayList<>(steps);
            for (int i = 0; i < steps; i++) {
                double t = lastT[0] + (targetT - lastT[0]) * (i / (double) steps);
                if (t > 1) break;
                double sm = t * t * (3 - 2 * t);
                double[] d1 = {cp1[0] + ox * sm, cp1[1] + oy * sm, cp1[2] + oz * sm};
                double[] d2 = {cp2[0] + ox * sm, cp2[1] + oy * sm, cp2[2] + oz * sm};
                double[] dt = {to[0] + ox * sm, to[1] + oy * sm, to[2] + oz * sm};
                double[] p = bez(from, d1, d2, dt, t);
                double j = wiggle ? 0.04 : 0;
                pts.add(new double[] {p[0] + (rnd() - 0.5) * 2 * j, p[1] + (rnd() - 0.5) * 2 * j, p[2] + (rnd() - 0.5) * 2 * j});
            }
            host.sendParticles(dim, pname, pts);
            lastT[0] = targetT;
            cur[0]++;
            return false;
        });
    }

    private static void electric(McHost host, Entity entity, int dim, String pname, double radius, double amount, double spacing, double lifetimeD, double[] initCenter) {
        String key = entity.getId() + ":" + pname;
        if (ELECTRIC.contains(key)) return;
        final double skelSegLen = 0.5;
        final int numSegs = (int) Math.ceil(radius / skelSegLen);
        final int arcCount = (int) Math.min(amount, 12);
        final int maxCmds = 800;
        int rawFill = Math.max(1, (int) Math.round(skelSegLen / Math.max(0.05, spacing)));
        final int fillPerGap = Math.min(rawFill, Math.max(1, (int) Math.floor(maxCmds / (double) Math.max(1, arcCount * numSegs))));
        final double turn = 0.85, jagChance = 0.28, jagStrength = 2.0, outwardBias = 0.38;
        final boolean hasLife = lifetimeD > 0;
        final int life = (int) lifetimeD;
        final int expandTicks = hasLife ? Math.max(3, (int) Math.floor(life * 0.30)) : 0;
        final int retractStart = hasLife ? (int) Math.floor(life * 0.70) : Integer.MAX_VALUE;
        final long expiresAt = host.server.getTickCount() + (hasLife ? life + 1 : 3);
        List<double[]> dirs = new ArrayList<>();
        for (int i = 0; i < arcCount; i++) {
            double phi = Math.acos(1 - 2 * (i + 0.5) / arcCount), theta = i * 2.399963;
            dirs.add(new double[] {Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta)});
        }
        ELECTRIC.add(key);
        final int[] tick = {0};
        host.addTask(() -> {
            int t = tick[0];
            if (hasLife ? t >= life : host.server.getTickCount() >= expiresAt) {
                ELECTRIC.remove(key);
                return true;
            }
            double scale;
            if (!hasLife) scale = 1;
            else if (t < expandTicks) {
                double p = t / (double) expandTicks;
                scale = 1 - (1 - p) * (1 - p) * (1 - p);
            } else if (t >= retractStart) {
                int rl = life - retractStart;
                double p = rl > 0 ? (t - retractStart) / (double) rl : 1;
                scale = (1 - p) * (1 - p) * (1 - p);
            } else scale = 1;
            if (scale < 0.005) {
                host.sendParticles(dim, pname, List.of(initCenter));
                tick[0]++;
                return false;
            }
            boolean inRetract = hasLife && t >= retractStart;
            List<double[]> pts = new ArrayList<>();
            for (int a = 0; a < dirs.size(); a++) {
                if (inRetract && (a % 2) != (t % 2)) continue;
                double[] sd = dirs.get(a);
                double seg = skelSegLen * Math.max(0.01, scale);
                double[] cur = initCenter.clone();
                double[] dir = normalize(sd[0], sd[1], sd[2]);
                List<double[]> skel = new ArrayList<>();
                skel.add(cur);
                for (int i = 0; i < numSegs; i++) {
                    double[] p1 = Math.abs(dir[0]) < 0.9 ? normalize(0, dir[2], -dir[1]) : normalize(-dir[2], 0, dir[0]);
                    double[] p2 = normalize(dir[1] * p1[2] - dir[2] * p1[1], dir[2] * p1[0] - dir[0] * p1[2], dir[0] * p1[1] - dir[1] * p1[0]);
                    double ba = rnd() * Math.PI * 2, bd = (0.2 + rnd() * 0.5) * turn;
                    boolean jag = rnd() < jagChance;
                    double ja = rnd() * Math.PI * 2, jm = jag ? (0.8 + rnd() * 0.6) * jagStrength : 0;
                    double[] n = new double[3];
                    for (int k = 0; k < 3; k++) {
                        n[k] = dir[k] + p1[k] * Math.cos(ba) * bd + p2[k] * Math.sin(ba) * bd + p1[k] * Math.cos(ja) * jm + p2[k] * Math.sin(ja) * jm;
                    }
                    dir = normalize(n[0] * (1 - outwardBias) + sd[0] * outwardBias, n[1] * (1 - outwardBias) + sd[1] * outwardBias, n[2] * (1 - outwardBias) + sd[2] * outwardBias);
                    double[] prev = skel.get(skel.size() - 1);
                    skel.add(new double[] {prev[0] + dir[0] * seg, prev[1] + dir[1] * seg, prev[2] + dir[2] * seg});
                }
                for (int g = 0; g < skel.size() - 1; g++) {
                    double[] p0 = skel.get(g), pn = skel.get(g + 1);
                    for (int s = 0; s < fillPerGap; s++) {
                        double tt = (s + 0.5) / fillPerGap;
                        pts.add(new double[] {p0[0] + (pn[0] - p0[0]) * tt, p0[1] + (pn[1] - p0[1]) * tt, p0[2] + (pn[2] - p0[2]) * tt});
                    }
                }
            }
            host.sendParticles(dim, pname, pts);
            tick[0]++;
            return false;
        });
    }
}
