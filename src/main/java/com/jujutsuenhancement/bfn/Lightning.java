package com.jujutsuenhancement.bfn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** {@code scriptevent ds:lightning}: port of the Bedrock add-on's bolt generator (index.js). Lines are particles with script variables. */
final class Lightning {
    private static final Random R = new Random();
    private static final int MAX_GLOBAL = 15;
    static int active;
    private static final Map<Integer, Long> MAGNETIC_COOLDOWN = new HashMap<>();

    private Lightning() {}

    static void reset() {
        active = 0;
        MAGNETIC_COOLDOWN.clear();
    }

    private static double rnd() {
        return R.nextDouble();
    }

    private static double sym() {
        return R.nextDouble() * 2 - 1;
    }

    private record Curve(Vec3 s, Vec3 cp1, Vec3 cp2, Vec3 e) {}

    private static Vec3 bezier(double t, Curve c) {
        double u = 1 - t;
        double a = u * u * u, b = 3 * u * u * t, d = 3 * u * t * t, e = t * t * t;
        return new Vec3(a * c.s.x + b * c.cp1.x + d * c.cp2.x + e * c.e.x, a * c.s.y + b * c.cp1.y + d * c.cp2.y + e * c.e.y,
                a * c.s.z + b * c.cp1.z + d * c.cp2.z + e * c.e.z);
    }

    private static Curve magnetCurve(Vec3 s, Vec3 e) {
        return new Curve(s,
                new Vec3(s.x + (e.x - s.x) * 0.3 + sym() * 1.5, s.y + (e.y - s.y) * 0.3 + Math.abs(e.y - s.y) * 0.4 + 2, s.z + (e.z - s.z) * 0.3 + sym() * 1.5),
                new Vec3(s.x + (e.x - s.x) * 0.7 + sym() * 1.0, e.y + 3 + rnd() * 2, s.z + (e.z - s.z) * 0.7 + sym() * 1.0), e);
    }

    private record Hit(Vec3 pos, int nx, int ny, int nz) {}

    private static boolean solid(Level l, double x, double y, double z) {
        BlockPos p = BlockPos.containing(x, y, z);
        if (!l.hasChunkAt(p) || l.isOutsideBuildHeight(p)) return false;
        BlockState st = l.getBlockState(p);
        return !st.getCollisionShape(l, p).isEmpty();
    }

    private static Hit surface(Level l, Vec3 o, double radius, boolean walls) {
        int[][] checks = walls
                ? new int[][] {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, -1, 0}}
                : new int[][] {{0, -1, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}};
        for (int[] f : checks) {
            int nx = f[0] == 0 ? 0 : f[0], ny = f[1] == 0 ? 0 : -f[1], nz = f[2];
            if (f[1] != 0) ny = 1;   // the straight-down check lands on a floor: its normal is up
            for (double d = 1; d <= radius; d += 1.0) {
                double cx = o.x + f[0] * d, cy = o.y + f[1] * d, cz = o.z + f[2] * d;
                if (solid(l, cx, cy, cz)) {
                    return new Hit(new Vec3(Math.floor(cx) + 0.5 + (f[0] != 0 ? f[0] : 0) * 0.6, ny > 0 ? Math.floor(cy) + 1.0 : Math.floor(cy) + 0.5,
                            Math.floor(cz) + 0.5 + (f[2] != 0 ? f[2] : 0) * 0.6), nx, ny, nz);
                }
            }
        }
        return null;
    }

    private static double[] sphere() {
        double theta = rnd() * Math.PI * 2, phi = Math.acos(2 * rnd() - 1);
        return new double[] {Math.sin(phi) * Math.cos(theta), Math.sin(phi) * Math.sin(theta), Math.cos(phi)};
    }

    private static Hit magnetSurface(Level l, Vec3 o, double radius) {
        for (int a = 0; a < 8; a++) {
            double[] dir = sphere();
            for (double d = 1; d <= radius; d += 1.5) {
                double cx = o.x + dir[0] * d, cy = o.y + dir[1] * d, cz = o.z + dir[2] * d;
                if (solid(l, cx, cy, cz)) {
                    double lx = Math.abs(dir[0]), ly = Math.abs(dir[1]), lz = Math.abs(dir[2]);
                    int nx = 0, ny = 0, nz = 0;
                    if (ly >= lx && ly >= lz) ny = dir[1] < 0 ? 1 : -1;
                    else if (lx >= lz) nx = dir[0] < 0 ? 1 : -1;
                    else nz = dir[2] < 0 ? 1 : -1;
                    return new Hit(new Vec3(Math.floor(cx) + 0.5 + nx * 0.6, ny > 0 ? Math.floor(cy) + 1.0 : ny < 0 ? Math.floor(cy) : Math.floor(cy) + 0.5,
                            Math.floor(cz) + 0.5 + nz * 0.6), nx, ny, nz);
                }
            }
        }
        return null;
    }

    private static final class Branch {
        int origin, segments;
        double size, dx, dy, dz;
        Curve curve;
        int hnx, hny, hnz;
        Vec3 hitPos;
    }

    private static double[] parse3(Matcher m) {
        return new double[] {Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3))};
    }

    private static final String NUM = "([-\\d.]+)";

    static void run(McHost host, Ctx c, List<String> args) {
        if (c.self() == null || !(c.self().handle() instanceof Entity player)) return;
        if (active >= MAX_GLOBAL) return;
        String raw = String.join(" ", args);
        boolean noRotation = Pattern.compile("\\bno_rotation\\b").matcher(raw).find();
        raw = raw.replaceFirst("\\bno_rotation\\b", "");
        boolean inverted = Pattern.compile("\\binverted\\b").matcher(raw).find();
        raw = raw.replaceFirst("\\binverted\\b", "");
        double slowed = 1;
        Matcher m = Pattern.compile("\\bslowed:" + NUM).matcher(raw);
        if (m.find()) {
            double sv = Double.parseDouble(m.group(1));
            slowed = sv == 0 ? 1 : sv < 0 ? Math.max(0.5, 1 / Math.abs(sv)) : sv;
            raw = raw.replace(m.group(0), "");
        }
        double outwards = 0;
        m = Pattern.compile("\\boutwards(?::([\\d.]+))?").matcher(raw);
        if (m.find()) {
            outwards = m.group(1) != null ? Double.parseDouble(m.group(1)) : 10;
            raw = raw.replace(m.group(0), "");
        }
        int branchMin = 0, branchMax = 0;
        m = Pattern.compile("\\bbranches?:(\\d+)(?:,(\\d+))?").matcher(raw);
        if (m.find()) {
            branchMin = Integer.parseInt(m.group(1));
            branchMax = m.group(2) != null ? Integer.parseInt(m.group(2)) : branchMin;
            raw = raw.replace(m.group(0), "");
        }
        double magnetic = 0;
        m = Pattern.compile("\\bmagnetic:([\\d.]+)").matcher(raw);
        if (m.find()) {
            magnetic = Double.parseDouble(m.group(1));
            raw = raw.replace(m.group(0), "");
        }
        long now = host.server.getTickCount();
        if (magnetic > 0) {
            long last = MAGNETIC_COOLDOWN.getOrDefault(player.getId(), 0L);
            if (now - last < 3) magnetic = 0;
            else MAGNETIC_COOLDOWN.put(player.getId(), now);
        }
        List<String> magnetParticles = new ArrayList<>();
        m = Pattern.compile("\\bmagnetparticle:(\\S+)").matcher(raw);
        while (m.find()) magnetParticles.add(m.group(1));
        raw = raw.replaceAll("\\bmagnetparticle:\\S+", "");
        double spread = 0;
        m = Pattern.compile("\\bspread:" + NUM).matcher(raw);
        if (m.find()) {
            spread = Double.parseDouble(m.group(1));
            raw = raw.replace(m.group(0), "");
        }
        Vec3 view = player.getLookAngle();
        Vec3 loc = player.position();
        Vec3 customStart = null, customEnd = null;
        m = Pattern.compile("\\bfront_pos:" + NUM + "," + NUM + "," + NUM).matcher(raw);
        if (m.find()) {
            double[] v = parse3(m);
            double[] wp = ScriptEvents.offset(view, loc.x, loc.y, loc.z, v[0], v[1], v[2]);
            customStart = new Vec3(wp[0] - loc.x, wp[1] - loc.y, wp[2] - loc.z);
            raw = raw.replace(m.group(0), "");
        }
        m = Pattern.compile("\\bpos:" + NUM + "," + NUM + "," + NUM).matcher(raw);
        if (m.find()) {
            double[] v = parse3(m);
            if (customStart == null) customStart = new Vec3(v[0], v[1], v[2]);
            raw = raw.replace(m.group(0), "");
        }
        m = Pattern.compile("\\bfront_end:" + NUM + "," + NUM + "," + NUM).matcher(raw);
        if (m.find()) {
            double[] v = parse3(m);
            double[] wp = ScriptEvents.offset(view, loc.x, loc.y, loc.z, v[0], v[1], v[2]);
            customEnd = new Vec3(wp[0] - loc.x, wp[1] - loc.y, wp[2] - loc.z);
            raw = raw.replace(m.group(0), "");
        }
        m = Pattern.compile("\\bend:" + NUM + "," + NUM + "," + NUM).matcher(raw);
        if (m.find()) {
            double[] v = parse3(m);
            if (customEnd == null) customEnd = new Vec3(v[0], v[1], v[2]);
            raw = raw.replace(m.group(0), "");
        }
        List<Double> offList = new ArrayList<>();
        for (String s : raw.split(",")) {
            try {
                offList.add(Double.parseDouble(s.trim()));
            } catch (NumberFormatException ex) {
                // dropped like the script's isNaN filter
            }
        }
        if (offList.size() < 15) return;
        double[] off = new double[offList.size()];
        for (int i = 0; i < off.length; i++) off[i] = offList.get(i);
        boolean explicit = customStart != null || customEnd != null;
        double sx = spread > 0 ? sym() * spread : 0, sy = spread > 0 ? sym() * spread : 0, sz = spread > 0 ? sym() * spread : 0;
        ServerLevel lv = (ServerLevel) player.level();
        int dim = McHost.dimOf(lv);

        Curve magCurve = null;
        Vec3 magTarget = null;
        int mnx = 0, mny = 1, mnz = 0;
        if (magnetic > 0 && explicit) {
            Vec3 o = new Vec3(loc.x + (customEnd != null ? customEnd.x : 0) + sx, loc.y + (customEnd != null ? customEnd.y : 0) + sy,
                    loc.z + (customEnd != null ? customEnd.z : 0) + sz);
            Hit h = magnetSurface(lv, o, magnetic);
            if (h != null) {
                magTarget = h.pos;
                mnx = h.nx;
                mny = h.ny;
                mnz = h.nz;
                Vec3 s = customStart != null ? loc.add(customStart) : loc;
                magCurve = magnetCurve(s, magTarget);
            }
        } else if (magnetic > 0) {
            Hit h = magnetSurface(lv, loc.add(view.scale(8)), magnetic);
            if (h != null) {
                magTarget = h.pos;
                mnx = h.nx;
                mny = h.ny;
                mnz = h.nz;
            }
        }
        List<double[]> outBolts = new ArrayList<>();
        if (outwards > 0) {
            int n = (int) Math.min(8, Math.max(4, Math.floor(outwards / 2)));
            for (int i = 0; i < n; i++) {
                double[] d = sphere();
                outBolts.add(new double[] {d[0] * outwards, d[1] * outwards, d[2] * outwards});
            }
        }
        final int mainLocs = 6;
        int numBranches = Math.min(3, branchMax > 0 ? (int) Math.floor(rnd() * (branchMax - branchMin + 1)) + branchMin : 0);
        List<Branch> branches = new ArrayList<>();
        List<Integer> nodes = new ArrayList<>(List.of(2, 3, 4, 5));
        java.util.Collections.shuffle(nodes, R);
        for (int b = 0; b < numBranches; b++) {
            Branch br = new Branch();
            br.origin = nodes.get(b % nodes.size());
            br.size = 0.3 + rnd() * 0.5;
            br.dx = sym();
            br.dy = -(0.3 + rnd() * 0.7);
            br.dz = sym();
            br.hny = 1;
            if (magnetic > 0 && rnd() < 0.3) {
                double t = br.origin / (double) (mainLocs - 1);
                Vec3 approx;
                if (magCurve != null) approx = bezier(t, magCurve);
                else approx = new Vec3(loc.x + view.x * t * 8 + sym() * 2, loc.y + view.y * t * 8 + sym() * 2, loc.z + view.z * t * 8 + sym() * 2);
                Hit h = surface(lv, approx, 8, true);
                if (h != null) {
                    br.curve = magnetCurve(approx, h.pos);
                    br.hnx = h.nx;
                    br.hny = h.ny;
                    br.hnz = h.nz;
                    br.hitPos = h.pos;
                }
            }
            br.segments = br.curve != null ? 4 : 2 + (int) Math.floor(rnd() * 2);
            branches.add(br);
        }
        final Bolt bolt = new Bolt(host, player, lv, dim, off, inverted, noRotation, explicit, customStart, customEnd, sx, sy, sz, magCurve, magTarget, mnx, mny, mnz,
                outBolts, branches, magnetParticles, mainLocs, slowed);
        active++;
        host.addTask(bolt::tick);
    }

    private static final class Bolt {
        final McHost host;
        final Entity player;
        final ServerLevel lv;
        final int dim;
        final double[] off;
        final boolean inverted, noRotation, explicit;
        final Vec3 customStart, customEnd;
        final double sx, sy, sz;
        final Curve magCurve;
        final Vec3 magTarget;
        final int mnx, mny, mnz;
        final List<double[]> outBolts;
        final List<Branch> branches;
        final List<String> magnetParticles;
        final int mainLocs;
        final int tickSpacing;
        final int totalTicks = 4;
        final int maxTick;
        List<Vec3[]> pre;
        boolean magnetDone;
        int elapsed;

        Bolt(McHost host, Entity player, ServerLevel lv, int dim, double[] off, boolean inverted, boolean noRotation, boolean explicit, Vec3 cs, Vec3 ce, double sx,
             double sy, double sz, Curve mc, Vec3 mt, int mnx, int mny, int mnz, List<double[]> out, List<Branch> br, List<String> mp, int mainLocs, double slowed) {
            this.host = host;
            this.player = player;
            this.lv = lv;
            this.dim = dim;
            this.off = off;
            this.inverted = inverted;
            this.noRotation = noRotation;
            this.explicit = explicit;
            this.customStart = cs;
            this.customEnd = ce;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.magCurve = mc;
            this.magTarget = mt;
            this.mnx = mnx;
            this.mny = mny;
            this.mnz = mnz;
            this.outBolts = out;
            this.branches = br;
            this.magnetParticles = mp;
            this.mainLocs = mainLocs;
            this.tickSpacing = Math.max(1, (int) Math.round(slowed));
            this.maxTick = tickSpacing > 1 ? tickSpacing * (totalTicks - 1) : totalTicks - 1;
            if (tickSpacing > 1) {
                pre = new ArrayList<>();
                for (int i = 0; i < 4; i++) pre.add(locations());
            }
        }

        private double dist(Vec3 a, Vec3 b) {
            return a.distanceTo(b);
        }

        private void line(Vec3 p, double dx, double dy, double dz, double length, double width, double r, double g, double b, String name) {
            host.sendNear(dim, p.x, p.y, p.z, 128.0, "L", new double[] {p.x, p.y, p.z, dx, dy, dz, length, width, r, g, b}, name);
        }

        private void segment(Vec3 p1, Vec3 p2, double sf, double ws) {
            double d = dist(p1, p2);
            double dx = p1.x - p2.x, dy = p1.y - p2.y, dz = p1.z - p2.z;
            Vec3 mid = new Vec3((p1.x + p2.x) / 2, (p1.y + p2.y) / 2, (p1.z + p2.z) / 2);
            if (inverted) {
                line(mid, dx, dy, dz, d / 2, 0.03 * off[13] * sf * ws, 0, 0, 0, "lightning_line_black");
                line(mid, dx, dy, dz, d / 2, 0.04 * off[13] * sf * ws * 0.65, off[6], off[7], off[8], "lightning_line");
            } else {
                double w1 = off[12] * sf * ws, w2 = off[13] * sf * ws * 0.7, w3 = off[14] * sf * ws;
                line(mid, dx, dy, dz, d / 2, 0.04 * w1, off[3], off[4], off[5], "lightning_line");
                line(mid, dx, dy, dz, d / 2, 0.04 * w2, off[6], off[7], off[8], "lightning_line");
                line(mid, dx, dy, dz, d / 2, 0.03 * w3, off[9], off[10], off[11], "lightning_line_black");
            }
        }

        private double sf(Vec3 p) {
            return 5 / Math.max(0.25, 0.5 * dist(player.position(), p));
        }

        private void drawBolt(Vec3 start, Vec3 end, double ws) {
            Vec3 prev = null;
            for (int i = 0; i < mainLocs; i++) {
                double t = i / (double) (mainLocs - 1);
                double jitter = (i == 0 || i == mainLocs - 1) ? 0 : 0.5;
                Vec3 loc = new Vec3(start.x + (end.x - start.x) * t + sym() * jitter * off[0], start.y + (end.y - start.y) * t + sym() * jitter * off[1],
                        start.z + (end.z - start.z) * t + sym() * jitter * off[2]);
                if (i > 0) segment(prev, loc, sf(loc), ws);
                prev = loc;
            }
        }

        private Vec3 startPoint() {
            Vec3 p = player.position();
            return customStart != null ? p.add(customStart) : p;
        }

        private Vec3[] locations() {
            Vec3 p = player.position();
            Vec3 start = startPoint();
            Vec3 end = null;
            if (explicit) {
                if (magTarget != null) end = magTarget;
                else if (customEnd != null) end = new Vec3(p.x + customEnd.x + sx, p.y + customEnd.y + sy, p.z + customEnd.z + sz);
            }
            Vec3[] out = new Vec3[mainLocs];
            Vec3 view = player.getLookAngle();
            for (int i = 0; i < mainLocs; i++) {
                boolean last = i == mainLocs - 1, first = i == 0;
                double xo = last ? sx : first ? 0 : rnd() * 6 - 3;
                double yo = last ? sy : first ? 0.6 : rnd() * 6 - 2;
                double zo = last ? 8 + sz : 2 * i + rnd() * 2;
                Vec3 loc;
                if (magCurve != null) {
                    double t = i / (double) (mainLocs - 1);
                    double j = (first || last) ? 0 : 0.6;
                    Vec3 bp = bezier(t, magCurve);
                    loc = new Vec3(bp.x + sym() * j * off[0], bp.y + sym() * j * off[1], bp.z + sym() * j * off[2]);
                } else if (explicit && end != null) {
                    double t = i / (double) (mainLocs - 1);
                    double j = (first || last) ? 0 : 0.5;
                    loc = new Vec3(start.x + (end.x - start.x) * t + sym() * j * off[0], start.y + (end.y - start.y) * t + sym() * j * off[1],
                            start.z + (end.z - start.z) * t + sym() * j * off[2]);
                } else if (noRotation) {
                    double[] o = ScriptEvents.offset(new Vec3(0, 0, 1), start.x, start.y, start.z, xo * off[0], yo * off[1] + 1, zo * off[2]);
                    loc = new Vec3(o[0], o[1], o[2]);
                } else if (last && magTarget != null) {
                    loc = magTarget;
                } else {
                    double[] o = ScriptEvents.offset(view, start.x, start.y, start.z, xo * off[0], yo * off[1] + 1, zo * off[2]);
                    loc = new Vec3(o[0], o[1], o[2]);
                }
                out[i] = loc;
            }
            return out;
        }

        private void magnetParticlesAt(Vec3 pos, int nx, int ny, int nz) {
            if (magnetParticles.isEmpty()) return;
            boolean wall = Math.abs(ny) < 0.9;
            for (String id : magnetParticles) {
                String n = id.startsWith("ds:") ? id.substring(3) : null;
                if (n == null) continue;
                if (n.equals("blackflash_floor") && wall) n = "blackflash_wall";
                host.sendNear(dim, pos.x, pos.y, pos.z, 128.0, "P", new double[] {pos.x, pos.y, pos.z, 0}, n);
            }
        }

        private void drawBranches(Vec3[] locs, double t, boolean interpolated) {
            for (Branch br : branches) {
                Vec3 origin = locs[Math.min(br.origin, locs.length - 1)];
                if (origin == null) continue;
                if (br.curve != null) {
                    Curve live = magnetCurve(origin, br.curve.e);
                    Vec3 prev = origin;
                    for (int s = 1; s <= br.segments; s++) {
                        double bt = s / (double) br.segments;
                        double jitter = s == br.segments ? 0 : interpolated ? 0.4 * (1 - t) : 0.4;
                        Vec3 bp = bezier(bt, live);
                        Vec3 next = new Vec3(bp.x + sym() * jitter, bp.y + sym() * jitter, bp.z + sym() * jitter);
                        segment(prev, next, sf(next), Math.max(0.1, br.size * (1 - s * 0.15)));
                        prev = next;
                        if (!interpolated && s == br.segments && !magnetDone && br.hitPos != null) magnetParticlesAt(br.hitPos, br.hnx, br.hny, br.hnz);
                    }
                } else {
                    Vec3 prev = origin;
                    for (int s = 0; s < br.segments; s++) {
                        double len = 1.0 + rnd() * 2.0;
                        Vec3 next = new Vec3(prev.x + br.dx * len + sym() * 0.8, prev.y + br.dy * len + sym() * 0.4, prev.z + br.dz * len + sym() * 0.8);
                        segment(prev, next, sf(next), Math.max(0.1, br.size * (1 - s * 0.25)));
                        prev = next;
                    }
                }
            }
        }

        /** @return true when the bolt is over */
        boolean tick() {
            if (elapsed > maxTick || !player.isAlive() && elapsed > 0) {
                active = Math.max(0, active - 1);
                return true;
            }
            if (tickSpacing > 1) {
                double frameFloat = (elapsed / (double) maxTick) * (totalTicks - 1);
                int from = Math.min((int) Math.floor(frameFloat), totalTicks - 1);
                int to = Math.min(from + 1, totalTicks - 1);
                double t = frameFloat - from;
                Vec3[] a = pre.get(from), b = pre.get(to);
                Vec3[] locs = new Vec3[a.length];
                for (int i = 0; i < a.length; i++) locs[i] = new Vec3(a[i].x + (b[i].x - a[i].x) * t, a[i].y + (b[i].y - a[i].y) * t, a[i].z + (b[i].z - a[i].z) * t);
                for (int i = 1; i < locs.length; i++) segment(locs[i - 1], locs[i], sf(locs[i]), 1.0);
                drawBranches(locs, t, true);
                if (elapsed == maxTick && !magnetDone) {
                    if (magTarget != null) magnetParticlesAt(magTarget, mnx, mny, mnz);
                    for (Branch br : branches) if (br.hitPos != null) magnetParticlesAt(br.hitPos, br.hnx, br.hny, br.hnz);
                    magnetDone = true;
                }
            } else {
                if (!outBolts.isEmpty()) {
                    Vec3 o = player.position();
                    for (double[] ob : outBolts) drawBolt(o, o.add(ob[0], ob[1], ob[2]), 1.0);
                    elapsed++;
                    return false;
                }
                Vec3[] locs = locations();
                for (int i = 1; i < locs.length; i++) {
                    segment(locs[i - 1], locs[i], sf(locs[i]), 1.0);
                    if (i == locs.length - 1 && magTarget != null && !magnetDone) magnetParticlesAt(magTarget, mnx, mny, mnz);
                }
                drawBranches(locs, 0, false);
                if (!magnetDone) magnetDone = true;
            }
            elapsed++;
            return false;
        }
    }
}
