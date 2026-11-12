package com.jujutsuenhancement.client.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A Bedrock {@code particle_effect} file, parsed into expressions that can be evaluated per particle. */
public final class ParticleDef {
    public enum Material { ADD, BLEND, ALPHA }
    public enum Shape { POINT, SPHERE, DISC, BOX }
    public enum DirMode { OUTWARDS, INWARDS, CUSTOM }
    public enum Facing { ROTATE_XYZ, ROTATE_Y, LOOKAT_XYZ, LOOKAT_Y, DIR_X, DIR_Y, DIR_Z, LOOKAT_DIRECTION }

    public String id = "";
    public Material material = Material.BLEND;
    /** Texture path as written in the file, e.g. {@code textures/particle/circle}. */
    public String texture = "";

    // emitter
    public boolean instant = true;
    public Molang.Expr count = Molang.constant(1);
    public Molang.Expr steadyRate = Molang.constant(1);
    public Molang.Expr steadyMax = Molang.constant(50);
    public double activeTime = 10.0;
    public Shape shape = Shape.POINT;
    public Molang.Expr[] offset = zero3();
    public Molang.Expr radius = Molang.constant(1);
    public Molang.Expr[] halfDimensions = {Molang.constant(1), Molang.constant(1), Molang.constant(1)};
    public boolean surfaceOnly;
    public DirMode dirMode = DirMode.OUTWARDS;
    public Molang.Expr[] dir = zero3();
    public double[] planeNormal = {0, 1, 0};
    /** {@code minecraft:emitter_local_space} with {@code rotation: true}: the whole effect turns with the emitter's yaw. */
    public boolean localRotation;

    // particle
    public Molang.Expr lifetime = Molang.constant(1);
    public Molang.Expr speed = Molang.constant(0);
    public Molang.Expr spinRotation = Molang.constant(0);
    public Molang.Expr spinRate = Molang.constant(0);
    public Molang.Expr[] accel = zero3();
    public Molang.Expr drag = Molang.constant(0);

    // appearance
    public Molang.Expr[] size = {Molang.constant(0.1), Molang.constant(0.1)};
    public Facing facing = Facing.ROTATE_XYZ;
    public double minSpeedThreshold = 0.01;
    public double texW = 16;
    public double texH = 16;
    public Molang.Expr[] uv = {Molang.constant(0), Molang.constant(0)};
    public Molang.Expr[] uvSize = {Molang.constant(16), Molang.constant(16)};
    public boolean flipbook;
    public double[] fbBase = {0, 0};
    public double[] fbStep = {0, 0};
    public double[] fbSize = {0, 0};
    public double fbFps = 8;
    public Molang.Expr fbMaxFrame = Molang.constant(1);
    public boolean fbLoop;
    public boolean fbStretch;

    // tint
    public double[] tintConst = {1, 1, 1, 1};
    public boolean tintGradient;
    public Molang.Expr tintInterpolant = Molang.constant(0);
    public double[] tintKeys = new double[0];
    public double[][] tintColors = new double[0][];
    public Molang.Expr[] tintExpr;

    private static Molang.Expr[] zero3() {
        return new Molang.Expr[] {Molang.constant(0), Molang.constant(0), Molang.constant(0)};
    }

    private static Molang.Expr expr(JsonElement e, Molang.Expr fallback) {
        if (e == null || e.isJsonNull()) return fallback;
        if (e.isJsonPrimitive()) {
            if (e.getAsJsonPrimitive().isNumber()) return Molang.constant(e.getAsDouble());
            if (e.getAsJsonPrimitive().isBoolean()) return Molang.constant(e.getAsBoolean() ? 1 : 0);
            return Molang.compile(e.getAsString());
        }
        return fallback;
    }

    private static Molang.Expr[] exprN(JsonElement e, int n, Molang.Expr[] fallback) {
        if (e == null || !e.isJsonArray()) return fallback;
        JsonArray a = e.getAsJsonArray();
        Molang.Expr[] out = new Molang.Expr[n];
        for (int i = 0; i < n; i++) out[i] = i < a.size() ? expr(a.get(i), Molang.constant(0)) : Molang.constant(0);
        return out;
    }

    private static double num(JsonElement e, double fallback) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsDouble();
        return fallback;
    }

    private static double[] numN(JsonElement e, int n, double[] fallback) {
        if (e == null || !e.isJsonArray()) return fallback;
        JsonArray a = e.getAsJsonArray();
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = i < a.size() ? num(a.get(i), 0) : 0;
        return out;
    }

    /** #AARRGGBB (or #RRGGBB) to {r, g, b, a} in 0..1. */
    static double[] hex(String s) {
        String h = s.startsWith("#") ? s.substring(1) : s;
        long v = Long.parseLong(h, 16);
        if (h.length() <= 6) v |= 0xFF000000L;
        return new double[] {((v >> 16) & 255) / 255.0, ((v >> 8) & 255) / 255.0, (v & 255) / 255.0, ((v >> 24) & 255) / 255.0};
    }

    public static ParticleDef parse(JsonObject root) {
        JsonObject pe = root.getAsJsonObject("particle_effect");
        ParticleDef d = new ParticleDef();
        JsonObject desc = pe.getAsJsonObject("description");
        d.id = desc.get("identifier").getAsString();
        JsonObject brp = desc.getAsJsonObject("basic_render_parameters");
        String mat = brp.get("material").getAsString();
        d.material = mat.contains("add") ? Material.ADD : mat.contains("alpha") ? Material.ALPHA : Material.BLEND;
        d.texture = brp.get("texture").getAsString();
        JsonObject c = pe.getAsJsonObject("components");

        JsonObject ls = c.getAsJsonObject("minecraft:emitter_local_space");
        if (ls != null && ls.has("rotation")) d.localRotation = ls.get("rotation").getAsBoolean();

        JsonObject o = c.getAsJsonObject("minecraft:emitter_rate_instant");
        if (o != null) {
            d.instant = true;
            d.count = expr(o.get("num_particles"), Molang.constant(1));
        }
        o = c.getAsJsonObject("minecraft:emitter_rate_steady");
        if (o != null) {
            d.instant = false;
            d.steadyRate = expr(o.get("spawn_rate"), Molang.constant(1));
            d.steadyMax = expr(o.get("max_particles"), Molang.constant(50));
        }
        o = c.getAsJsonObject("minecraft:emitter_lifetime_once");
        if (o != null) d.activeTime = num(o.get("active_time"), 10.0);
        o = c.getAsJsonObject("minecraft:emitter_lifetime_looping");
        if (o != null) d.activeTime = num(o.get("active_time"), 10.0);

        JsonObject sh = null;
        if ((sh = c.getAsJsonObject("minecraft:emitter_shape_sphere")) != null) d.shape = Shape.SPHERE;
        else if ((sh = c.getAsJsonObject("minecraft:emitter_shape_disc")) != null) d.shape = Shape.DISC;
        else if ((sh = c.getAsJsonObject("minecraft:emitter_shape_box")) != null) d.shape = Shape.BOX;
        else if ((sh = c.getAsJsonObject("minecraft:emitter_shape_point")) != null) d.shape = Shape.POINT;
        if (sh != null) {
            d.offset = exprN(sh.get("offset"), 3, d.offset);
            d.radius = expr(sh.get("radius"), d.radius);
            d.halfDimensions = exprN(sh.get("half_dimensions"), 3, d.halfDimensions);
            if (sh.has("surface_only")) d.surfaceOnly = sh.get("surface_only").getAsBoolean();
            d.planeNormal = numN(sh.get("plane_normal"), 3, d.planeNormal);
            JsonElement dir = sh.get("direction");
            if (dir != null && dir.isJsonPrimitive()) {
                d.dirMode = "inwards".equals(dir.getAsString()) ? DirMode.INWARDS : DirMode.OUTWARDS;
            } else if (dir != null && dir.isJsonArray()) {
                d.dirMode = DirMode.CUSTOM;
                d.dir = exprN(dir, 3, d.dir);
            } else if (d.shape == Shape.POINT) {
                d.dirMode = DirMode.CUSTOM;
            }
        }

        o = c.getAsJsonObject("minecraft:particle_lifetime_expression");
        if (o != null) d.lifetime = expr(o.get("max_lifetime"), d.lifetime);
        d.speed = expr(c.get("minecraft:particle_initial_speed"), d.speed);
        o = c.getAsJsonObject("minecraft:particle_initial_spin");
        if (o != null) {
            d.spinRotation = expr(o.get("rotation"), d.spinRotation);
            d.spinRate = expr(o.get("rotation_rate"), d.spinRate);
        }
        o = c.getAsJsonObject("minecraft:particle_motion_dynamic");
        if (o != null) {
            d.accel = exprN(o.get("linear_acceleration"), 3, d.accel);
            d.drag = expr(o.get("linear_drag_coefficient"), d.drag);
        }

        o = c.getAsJsonObject("minecraft:particle_appearance_billboard");
        if (o != null) {
            d.size = exprN(o.get("size"), 2, d.size);
            String f = o.has("facing_camera_mode") ? o.get("facing_camera_mode").getAsString() : "rotate_xyz";
            switch (f) {
                case "rotate_y": d.facing = Facing.ROTATE_Y; break;
                case "lookat_xyz": d.facing = Facing.LOOKAT_XYZ; break;
                case "lookat_y": d.facing = Facing.LOOKAT_Y; break;
                case "direction_x": d.facing = Facing.DIR_X; break;
                case "direction_y": d.facing = Facing.DIR_Y; break;
                case "direction_z": d.facing = Facing.DIR_Z; break;
                case "lookat_direction": d.facing = Facing.LOOKAT_DIRECTION; break;
                default: d.facing = Facing.ROTATE_XYZ; break;
            }
            JsonObject dm = o.getAsJsonObject("direction");
            if (dm != null && dm.has("min_speed_threshold")) d.minSpeedThreshold = dm.get("min_speed_threshold").getAsDouble();
            JsonObject uv = o.getAsJsonObject("uv");
            if (uv != null) {
                d.texW = num(uv.get("texture_width"), 16);
                d.texH = num(uv.get("texture_height"), 16);
                d.uv = exprN(uv.get("uv"), 2, d.uv);
                d.uvSize = exprN(uv.get("uv_size"), 2, new Molang.Expr[] {Molang.constant(d.texW), Molang.constant(d.texH)});
                JsonObject fb = uv.getAsJsonObject("flipbook");
                if (fb != null) {
                    d.flipbook = true;
                    d.fbBase = numN(fb.get("base_UV"), 2, d.fbBase);
                    d.fbStep = numN(fb.get("step_UV"), 2, d.fbStep);
                    d.fbSize = numN(fb.get("size_UV"), 2, new double[] {d.texW, d.texH});
                    d.fbFps = num(fb.get("frames_per_second"), 8);
                    d.fbMaxFrame = expr(fb.get("max_frame"), d.fbMaxFrame);
                    d.fbLoop = fb.has("loop") && fb.get("loop").getAsBoolean();
                    d.fbStretch = fb.has("stretch_to_lifetime") && fb.get("stretch_to_lifetime").getAsBoolean();
                }
            }
        }

        o = c.getAsJsonObject("minecraft:particle_appearance_tinting");
        if (o != null && o.has("color")) {
            JsonElement col = o.get("color");
            if (col.isJsonArray()) {
                JsonArray a = col.getAsJsonArray();
                boolean allNum = true;
                for (JsonElement e : a) if (!(e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber())) allNum = false;
                if (allNum) {
                    for (int i = 0; i < 4; i++) d.tintConst[i] = i < a.size() ? a.get(i).getAsDouble() : 1.0;
                } else {
                    d.tintExpr = exprN(col, 4, null);
                }
            } else if (col.isJsonPrimitive()) {
                d.tintConst = hex(col.getAsString());
            } else if (col.isJsonObject()) {
                JsonObject co = col.getAsJsonObject();
                d.tintGradient = true;
                d.tintInterpolant = expr(co.get("interpolant"), Molang.constant(0));
                JsonElement g = co.get("gradient");
                List<double[]> stops = new ArrayList<>();
                if (g != null && g.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> en : g.getAsJsonObject().entrySet()) {
                        double[] rgba = hex(en.getValue().getAsString());
                        stops.add(new double[] {Double.parseDouble(en.getKey()), rgba[0], rgba[1], rgba[2], rgba[3]});
                    }
                } else if (g != null && g.isJsonArray()) {
                    JsonArray ga = g.getAsJsonArray();
                    for (int i = 0; i < ga.size(); i++) {
                        double[] rgba = hex(ga.get(i).getAsString());
                        stops.add(new double[] {ga.size() == 1 ? 0 : i / (double) (ga.size() - 1), rgba[0], rgba[1], rgba[2], rgba[3]});
                    }
                }
                stops.sort((x, y) -> Double.compare(x[0], y[0]));
                d.tintKeys = new double[stops.size()];
                d.tintColors = new double[stops.size()][];
                for (int i = 0; i < stops.size(); i++) {
                    double[] s = stops.get(i);
                    d.tintKeys[i] = s[0];
                    d.tintColors[i] = new double[] {s[1], s[2], s[3], s[4]};
                }
            }
        }
        return d;
    }

    /** Writes the tint for the given particle state into {@code out} (r, g, b, a). */
    public void tint(Molang.Ctx ctx, double[] out) {
        if (tintGradient && tintKeys.length > 0) {
            double t = tintInterpolant.eval(ctx);
            int n = tintKeys.length;
            if (t <= tintKeys[0]) {
                System.arraycopy(tintColors[0], 0, out, 0, 4);
            } else if (t >= tintKeys[n - 1]) {
                System.arraycopy(tintColors[n - 1], 0, out, 0, 4);
            } else {
                int i = 1;
                while (i < n - 1 && t > tintKeys[i]) i++;
                double span = tintKeys[i] - tintKeys[i - 1];
                double f = span <= 0 ? 0 : (t - tintKeys[i - 1]) / span;
                for (int k = 0; k < 4; k++) out[k] = tintColors[i - 1][k] + (tintColors[i][k] - tintColors[i - 1][k]) * f;
            }
        } else if (tintExpr != null) {
            for (int k = 0; k < 4; k++) out[k] = tintExpr[k].eval(ctx);
        } else {
            System.arraycopy(tintConst, 0, out, 0, 4);
        }
    }
}
