package com.jujutsuenhancement.bfn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Bedrock command interpreter: runs the add-on's {@code .mcfunction} files. It does the part that has nothing to do with the world
 * (execute chains, selectors, scoreboard, tags, function calls); everything that changes the world goes to {@link Host#command}.
 */
public final class Engine {
    private final Host host;
    private final Map<String, Selector> selectors = new ConcurrentHashMap<>();
    private final Map<String, List<Line>> functions = new ConcurrentHashMap<>();
    private final Map<String, Line> lines = new ConcurrentHashMap<>();
    /** commands left in this tick (Bedrock stops a runaway function chain; so do we) */
    public int budget = 400_000;
    private int depth;
    private final java.util.Random random = new java.util.Random();

    public Engine(Host host) {
        this.host = host;
    }

    public Host host() {
        return host;
    }

    public Scores scores() {
        return host.scores();
    }

    // ---------------------------------------------------------------------------------------------------------------- functions

    public List<Line> function(String path) {
        List<Line> f = functions.get(path);
        if (f != null) return f;
        List<String> src = host.functionLines(path);
        if (src == null) {
            functions.put(path, List.of());
            host.unknown("function " + path);
            return List.of();
        }
        List<Line> out = new ArrayList<>(src.size());
        for (String s : src) {
            s = s.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;
            try {
                out.add(lines.computeIfAbsent(s, Line::parse));
            } catch (RuntimeException e) {
                host.unknown("parse " + s);
            }
        }
        functions.put(path, out);
        return out;
    }

    public void runFunction(String path, Ctx ctx) {
        if (depth > 200) return;
        depth++;
        try {
            for (Line ln : function(path)) {
                if (--budget < 0) return;
                exec(ln, ctx);
            }
        } finally {
            depth--;
        }
    }

    public void runLine(String text, Ctx ctx) {
        exec(lines.computeIfAbsent(text.strip(), Line::parse), ctx);
    }

    // ---------------------------------------------------------------------------------------------------------------- execute

    public void exec(Line ln, Ctx ctx) {
        step(ln, 0, ctx);
    }

    private void step(Line ln, int i, Ctx c) {
        if (i == ln.subs.size()) {
            command(ln, c);
            return;
        }
        Line.Sub s = ln.subs.get(i);
        switch (s.type) {
            case AS -> {
                for (Ent e : select(s.sel, c)) step(ln, i + 1, c.withSelf(e));
            }
            case AT -> {
                for (Ent e : select(s.sel, c)) step(ln, i + 1, c.at(e));
            }
            case POS_AS -> {
                for (Ent e : select(s.sel, c)) step(ln, i + 1, c.pos(e.x(), e.y(), e.z()));
            }
            case POS -> {
                double[] p = resolve(s.coords, c);
                step(ln, i + 1, c.pos(p[0], p[1], p[2]));
            }
            case ANCHOR -> step(ln, i + 1, c.anchor(s.text.equals("eyes")));
            case ALIGN -> {
                double nx = c.x(), ny = c.y(), nz = c.z();
                if (s.text.indexOf('x') >= 0) nx = Math.floor(nx);
                if (s.text.indexOf('y') >= 0) ny = Math.floor(ny);
                if (s.text.indexOf('z') >= 0) nz = Math.floor(nz);
                step(ln, i + 1, c.pos(nx, ny, nz));
            }
            case IN -> step(ln, i + 1, c);
            case ROTATED -> step(ln, i + 1, c.rot((float) (s.yawRel ? c.yaw() + s.yaw : s.yaw), (float) (s.pitchRel ? c.pitch() + s.pitch : s.pitch)));
            case ROTATED_AS -> {
                for (Ent e : select(s.sel, c)) step(ln, i + 1, c.rot(e.yaw(), e.pitch()));
            }
            case FACING -> {
                double[] p = resolve(s.coords, c);
                step(ln, i + 1, face(c, p[0], p[1], p[2]));
            }
            case FACING_ENTITY -> {
                for (Ent e : select(s.sel, c)) {
                    double ty = s.anchor.equals("eyes") ? e.y() + e.eyeHeight() : e.y();
                    step(ln, i + 1, face(c, e.x(), ty, e.z()));
                }
            }
            case IF_ENTITY -> {
                if (exists(s.sel, c) != s.unless) step(ln, i + 1, c);
            }
            case IF_SCORE -> {
                if (ifScore(s, c) != s.unless) step(ln, i + 1, c);
            }
            case IF_BLOCK -> {
                double[] p = resolve(s.coords, c);
                String id = host.blockId(c.dim(), (int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2]));
                String want = s.text.startsWith("minecraft:") ? s.text.substring(10) : s.text;
                if (id.equals(want) != s.unless) step(ln, i + 1, c);
            }
            case IF_BLOCKS -> {
                if (blocksEqual(s, c) != s.unless) step(ln, i + 1, c);
            }
        }
    }

    private boolean blocksEqual(Line.Sub s, Ctx c) {
        double[] a = resolve(s.coords, c);
        double[] b = resolve(s.coords2, c);
        double[] d = resolve(s.coords3, c);
        int x0 = (int) Math.floor(Math.min(a[0], b[0])), x1 = (int) Math.floor(Math.max(a[0], b[0]));
        int y0 = (int) Math.floor(Math.min(a[1], b[1])), y1 = (int) Math.floor(Math.max(a[1], b[1]));
        int z0 = (int) Math.floor(Math.min(a[2], b[2])), z1 = (int) Math.floor(Math.max(a[2], b[2]));
        int ox = (int) Math.floor(d[0]) - x0, oy = (int) Math.floor(d[1]) - y0, oz = (int) Math.floor(d[2]) - z0;
        boolean masked = "masked".equals(s.text);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 32768) return false;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    String p = host.blockId(c.dim(), x, y, z);
                    if (masked && p.equals("air")) continue;
                    if (!p.equals(host.blockId(c.dim(), x + ox, y + oy, z + oz))) return false;
                }
            }
        }
        return true;
    }

    private boolean ifScore(Line.Sub s, Ctx c) {
        List<String> a = holders(s.sel, c);
        if (a.isEmpty()) return false;
        Integer va = scores().get(s.text, a.get(0));
        if (va == null) return false;
        if (s.text2.equals("matches")) return s.range.test(va);
        List<String> b = holders(s.sel2, c);
        if (b.isEmpty()) return false;
        Integer vb = scores().get(s.obj2, b.get(0));
        if (vb == null) return false;
        return switch (s.text2) {
            case "=" -> va.intValue() == vb.intValue();
            case "<" -> va < vb;
            case ">" -> va > vb;
            case "<=" -> va <= vb;
            case ">=" -> va >= vb;
            default -> false;
        };
    }

    private Ctx face(Ctx c, double tx, double ty, double tz) {
        double dx = tx - c.ox(), dy = ty - c.oy(), dz = tz - c.oz();
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return c.rot(yaw, pitch);
    }

    /** absolute, relative or local coordinates to a point */
    public double[] resolve(Coords k, Ctx c) {
        if (k.local) {
            double yaw = Math.toRadians(c.yaw()), pitch = Math.toRadians(c.pitch());
            double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
            double[] fwd = {-sy * cp, -sp, cy * cp};
            double[] up = {-sy * sp, cp, cy * sp};
            double[] left = {cy, 0, sy};
            double l = k.v[0], u = k.v[1], f = k.v[2];
            return new double[] {
                    c.ox() + left[0] * l + up[0] * u + fwd[0] * f,
                    c.oy() + left[1] * l + up[1] * u + fwd[1] * f,
                    c.oz() + left[2] * l + up[2] * u + fwd[2] * f};
        }
        return new double[] {k.rel[0] ? c.x() + k.v[0] : k.v[0], k.rel[1] ? c.y() + k.v[1] : k.v[1], k.rel[2] ? c.z() + k.v[2] : k.v[2]};
    }

    // ---------------------------------------------------------------------------------------------------------------- selectors

    public Selector selector(String text) {
        Selector s = selectors.get(text);
        if (s == null) {
            s = Selector.parse(text);
            selectors.put(text, s);
        }
        return s;
    }

    public boolean exists(String text, Ctx c) {
        Selector s = selector(text);
        if (s.kind == 0) return false;
        return !select(s, c, 1).isEmpty();
    }

    public List<Ent> select(String text, Ctx c) {
        Selector s = selector(text);
        if (s.kind == 0) return List.of();
        return select(s, c, Integer.MAX_VALUE);
    }

    /** score holder keys of a target: the entities of a selector, or the literal name of a fake player */
    public List<String> holders(String text, Ctx c) {
        Selector s = selector(text);
        if (s.kind == 0) return List.of(text);
        List<Ent> es = select(s, c, Integer.MAX_VALUE);
        List<String> out = new ArrayList<>(es.size());
        for (Ent e : es) out.add(e.key());
        return out;
    }

    private List<Ent> select(Selector s, Ctx c, int limit) {
        List<Ent> out = new ArrayList<>();
        int lim = s.c == null ? (s.kind == 'p' ? Math.min(limit, 1) : limit) : Math.min(limit, Math.abs(s.c));
        boolean sorted = s.c != null || s.kind == 'p';
        if (s.kind == 's') {
            Ent e = c.self();
            if (e != null && matches(s, e, c)) out.add(e);
            return out;
        }
        Iterable<? extends Ent> pool = s.kind == 'a' || s.kind == 'p' || s.kind == 'r' ? host.players(c.dim()) : host.entities(c.dim());
        if (s.kind == 'r') {
            List<Ent> all = new ArrayList<>();
            for (Ent e : pool) if (matches(s, e, c)) all.add(e);
            if (all.isEmpty()) return out;
            int n = s.c == null ? 1 : Math.abs(s.c);
            java.util.Collections.shuffle(all, random);
            return new ArrayList<>(all.subList(0, Math.min(n, all.size())));
        }
        if (!sorted) {
            for (Ent e : pool) {
                if (!matches(s, e, c)) continue;
                out.add(e);
                if (out.size() >= lim) break;
            }
            return out;
        }
        double ox = s.x != null ? (s.xRel ? c.x() + s.x : s.x) : c.x();
        double oy = s.y != null ? (s.yRel ? c.y() + s.y : s.y) : c.y();
        double oz = s.z != null ? (s.zRel ? c.z() + s.z : s.z) : c.z();
        for (Ent e : pool) if (matches(s, e, c)) out.add(e);
        boolean far = s.c != null && s.c < 0;
        out.sort(Comparator.comparingDouble((Ent e) -> sq(e.x() - ox, e.y() - oy, e.z() - oz)));
        if (far) java.util.Collections.reverse(out);
        return out.size() > lim ? new ArrayList<>(out.subList(0, lim)) : out;
    }

    private static double sq(double a, double b, double c) {
        return a * a + b * b + c * c;
    }

    /** the cheap tests first (tags, scores), the geometry last */
    private boolean matches(Selector s, Ent e, Ctx c) {
        if (!e.isAlive()) return false;
        if (!s.tags.isEmpty()) {
            for (Selector.Neg t : s.tags) {
                boolean has;
                if (t.value().isEmpty()) has = !e.tags().isEmpty();
                else has = e.tags().contains(t.value());
                if (has == t.neg()) return false;
            }
        }
        if (!s.scores.isEmpty()) {
            Scores sc = scores();
            for (Map.Entry<String, List<Range>> en : s.scores.entrySet()) {
                Integer v = sc.get(en.getKey(), e.key());
                for (Range r : en.getValue()) {
                    if (v == null) {
                        if (!r.neg) return false;
                    } else if (!r.test(v)) return false;
                }
            }
        }
        if (!s.types.isEmpty()) {
            String id = e.typeId();
            for (Selector.Neg t : s.types) {
                String want = t.value().startsWith("minecraft:") ? t.value().substring(10) : t.value();
                if (id.equals(want) == t.neg()) return false;
            }
        }
        if (!s.families.isEmpty()) {
            List<String> fam = e.families();
            for (Selector.Neg t : s.families) if (fam.contains(t.value()) == t.neg()) return false;
        }
        if (!s.names.isEmpty()) {
            for (Selector.Neg t : s.names) {
                String want = t.value();
                boolean negated = want.startsWith("!");
                if (negated) want = want.substring(1);
                if (e.nameTag().equals(want) == (negated || t.neg())) return false;
            }
        }
        if (s.gamemode != null) {
            int gm = e.gamemode();
            String v = s.gamemode.value();
            int want = switch (v) {
                case "0", "s", "survival" -> 0;
                case "1", "c", "creative" -> 1;
                case "2", "a", "adventure" -> 2;
                case "3", "spectator" -> 3;
                default -> -1;
            };
            if (e.isPlayer() ? (gm == want) == s.gamemode.neg() : !s.gamemode.neg()) return false;
        }
        if (s.l != null && e.level() > s.l || s.lm != null && e.level() < s.lm) return false;
        if (s.rx != null && e.pitch() > s.rx || s.rxm != null && e.pitch() < s.rxm) return false;
        if (s.ry != null || s.rym != null) {
            float y = e.yaw();
            while (y > 180) y -= 360;
            while (y < -180) y += 360;
            if (s.ry != null && y > s.ry || s.rym != null && y < s.rym) return false;
        }
        if (s.r != null || s.rm != null) {
            double ox = s.x != null ? (s.xRel ? c.x() + s.x : s.x) : c.x();
            double oy = s.y != null ? (s.yRel ? c.y() + s.y : s.y) : c.y();
            double oz = s.z != null ? (s.zRel ? c.z() + s.z : s.z) : c.z();
            double d2 = sq(e.x() - ox, e.y() - oy, e.z() - oz);
            if (s.r != null && d2 > s.r * s.r || s.rm != null && d2 < s.rm * s.rm) return false;
        }
        if (s.dx != null || s.dy != null || s.dz != null) {
            double ox = s.x != null ? (s.xRel ? c.x() + s.x : s.x) : c.x();
            double oy = s.y != null ? (s.yRel ? c.y() + s.y : s.y) : c.y();
            double oz = s.z != null ? (s.zRel ? c.z() + s.z : s.z) : c.z();
            double dx = s.dx == null ? 0 : s.dx, dy = s.dy == null ? 0 : s.dy, dz = s.dz == null ? 0 : s.dz;
            double[] b = e.box();
            // the volume is the box from the origin to origin + d; an entity whose bounding box touches it is inside (d = 0: a point probe)
            double x0 = Math.min(ox, ox + dx), x1 = Math.max(ox, ox + dx);
            double y0 = Math.min(oy, oy + dy), y1 = Math.max(oy, oy + dy);
            double z0 = Math.min(oz, oz + dz), z1 = Math.max(oz, oz + dz);
            if (b[3] < x0 || b[0] > x1 || b[4] < y0 || b[1] > y1 || b[5] < z0 || b[2] > z1) return false;
        }
        if (!s.hasItems.isEmpty()) {
            for (Selector.HasItem q : s.hasItems) if (!e.hasItem(q)) return false;
        }
        if (!s.props.isEmpty()) {
            for (Map.Entry<String, String> en : s.props.entrySet()) {
                if (en.getKey().startsWith("has_property") && !propsMatch(e, en.getValue())) return false;
            }
        }
        return true;
    }

    private boolean propsMatch(Ent e, String body) {
        String b = body.startsWith("{") ? body.substring(1, body.length() - 1) : body;
        for (String kv : Tok.commas(b)) {
            int eq = kv.indexOf('=');
            if (eq < 0) continue;
            String k = kv.substring(0, eq).trim();
            Range r = Range.parse(kv.substring(eq + 1).trim());
            if (!r.test(e.property(k))) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------- commands

    private void command(Line ln, Ctx c) {
        switch (ln.verb) {
            case "" -> { }
            case "function" -> runFunction(ln.args.get(0), c);
            case "scoreboard" -> scoreboard(ln, c);
            case "tag" -> tag(ln, c);
            default -> host.command(this, c, ln);
        }
    }

    private void tag(Line ln, Ctx c) {
        List<String> a = ln.args;
        if (a.size() < 2) return;
        List<Ent> es = select(a.get(0), c);
        if (a.get(1).equals("add") && a.size() > 2) {
            for (Ent e : es) e.tags().add(a.get(2));
        } else if (a.get(1).equals("remove") && a.size() > 2) {
            for (Ent e : es) e.tags().remove(a.get(2));
        }
    }

    private void scoreboard(Line ln, Ctx c) {
        List<String> a = ln.args;
        if (a.isEmpty()) return;
        if (a.get(0).equals("objectives")) {
            if (a.size() > 2 && a.get(1).equals("add")) scores().addObjective(a.get(2));
            else if (a.size() > 2 && a.get(1).equals("remove")) scores().removeObjective(a.get(2));
            return;
        }
        if (!a.get(0).equals("players") || a.size() < 3) return;
        String op = a.get(1);
        switch (op) {
            case "set", "add", "remove" -> {
                if (a.size() < 5) return;
                List<String> hs = holders(a.get(2), c);
                int v = parseInt(a.get(4));
                String obj = a.get(3);
                for (String h : hs) {
                    switch (op) {
                        case "set" -> scores().set(obj, h, v);
                        case "add" -> scores().add(obj, h, v);
                        default -> scores().add(obj, h, -v);
                    }
                }
            }
            case "reset" -> {
                for (String h : holders(a.get(2), c)) scores().reset(a.size() > 3 ? a.get(3) : null, h);
            }
            case "random" -> {
                if (a.size() < 6) return;
                int lo = parseInt(a.get(4)), hi = parseInt(a.get(5));
                for (String h : holders(a.get(2), c)) scores().set(a.get(3), h, lo + random.nextInt(Math.max(1, hi - lo + 1)));
            }
            case "operation" -> operation(a, c);
            case "test" -> { }
            default -> { }
        }
    }

    private void operation(List<String> a, Ctx c) {
        if (a.size() < 7) return;
        List<String> ts = holders(a.get(2), c);
        String tobj = a.get(3);
        String op = a.get(4);
        List<String> ss = holders(a.get(5), c);
        String sobj = a.get(6);
        if (ss.isEmpty()) return;
        Integer sv = scores().get(sobj, ss.get(0));
        if (sv == null) return;
        for (String t : ts) {
            Integer tv = scores().get(tobj, t);
            int cur = tv == null ? 0 : tv;
            switch (op) {
                case "=" -> scores().set(tobj, t, sv);
                case "+=" -> scores().set(tobj, t, cur + sv);
                case "-=" -> scores().set(tobj, t, cur - sv);
                case "*=" -> scores().set(tobj, t, cur * sv);
                case "/=" -> {
                    if (sv != 0) scores().set(tobj, t, Math.floorDiv(cur, sv));
                }
                case "%=" -> {
                    if (sv != 0) scores().set(tobj, t, Math.floorMod(cur, sv));
                }
                case "<" -> scores().set(tobj, t, Math.min(cur, sv));
                case ">" -> scores().set(tobj, t, Math.max(cur, sv));
                case "><" -> {
                    if (!ss.isEmpty()) {
                        scores().set(sobj, ss.get(0), cur);
                        scores().set(tobj, t, sv);
                    }
                }
                default -> { }
            }
        }
    }

    public static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return (int) Double.parseDouble(s);
        }
    }
}
