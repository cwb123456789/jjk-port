package com.jujutsuenhancement.bfn;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The {@code scriptevent} handlers of the Bedrock add-on's scripts (index.js), ported by hand; the source entity is the executor. */
final class ScriptEvents {
    private ScriptEvents() {}

    /** @return false when the id is not handled (yet) */
    static boolean run(McHost host, Engine eng, Ctx c, String id, List<String> args) {
        if (c.self() == null || !(c.self().handle() instanceof Entity e)) return true;
        switch (id) {
            case "ds:knockback" -> knock(e, view(e), num(args, 0), num(args, 1));
            case "ds:dashEast" -> {
                Vec3 v = view(e);
                knock(e, new Vec3(-v.z, 0, v.x), 9, 0);
            }
            case "ds:dashWest" -> {
                Vec3 v = view(e);
                knock(e, new Vec3(v.z, 0, -v.x), 9, 0);
            }
            case "ds:upslightly2" -> knock(e, view(e), 0, 0.25);
            case "ds:sent_back" -> knock(e, view(e), -10, 1.5);
            case "ds:sent_back2" -> knock(e, view(e), -13, 2.25);
            case "ds:antiair" -> knock(e, view(e), 0, 0.035);
            case "ds:downfall" -> knock(e, Vec3.ZERO, 0, e.getDeltaMovement().y < 0 ? -3 : -1);
            case "ds:leapforwards" -> knock(e, view(e), 7, 2);
            case "ds:leapforwards2" -> knock(e, view(e), 3, 0.85);
            case "ds:leapforwards3" -> knock(e, view(e), 3, 0.8);
            case "ds:leapforwards4" -> knock(e, view(e), 9, 1.75);
            case "ds:leap_back" -> knock(e, view(e), -8, 3);
            case "ds:leap_back2" -> knock(e, view(e), -8, 2);
            case "ds:autoJump2" -> autoJump(e);
            case "ds:placeblock" -> placeBlock(host, c, args);
            case "ds:boostup" -> knock(e, Vec3.ZERO, 0, args.isEmpty() || num(args, 0) == 0 ? 1 : num(args, 0));
            case "ds:leapup" -> {
                if (e instanceof net.minecraft.world.entity.player.Player) knock(e, Vec3.ZERO, 0, 4);
            }
            case "ds:fly" -> {
                if (e instanceof net.minecraft.world.entity.player.Player) {
                    Vec3 d = e.getLookAngle();
                    double mag = d.length();
                    if (mag != 0) {
                        double dx = d.x / mag, dy = d.y / mag, dz = d.z / mag;
                        double ff = 1 - Math.abs(dy);
                        knock(e, new Vec3(dx * ff, 0, dz * ff), 3, dy * Math.abs(dy) * 3);
                    }
                }
            }
            case "ds:regeneration" -> {
                if (e instanceof net.minecraft.world.entity.LivingEntity le) le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 20, 6, false, false));
            }
            case "ds:backward2", "ds:backward5", "ds:leapbackwards", "ds:leapforwardss", "ds:dismantle_small", "sl:knockback", "ds:D1", "ds:D2", "ds:D3", "ds:D4", "ds:D5", "ds:D6",
                 "ds:granite_beam_green", "ds:granite_beam_purple", "ds:granite_beam_red", "ds:granite_beam_blue", "ds:granite_beam_blue_spread",
                 "ds:granite_beam_red_spread", "ds:granite_beam_green_spread", "ds:granite_beam_purple_spread" -> { }   // the Bedrock scripts have no handler: nothing happens there either
            case "ds:lightning" -> Lightning.run(host, c, args);
            case "ds:raycast" -> RayCast.run(host, eng, c, args);
            case "ds:trail_particle" -> TrailParticle.run(host, c, args);
            default -> {
                if (DELUSIONAL.containsKey(id)) {
                    delusional(host, c, id, args);
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    /** particles only the players with the tag "delusional" see, placed relative to each of them */
    private static final java.util.Map<String, String[]> DELUSIONAL = java.util.Map.of(
            "ds:delusional_hit", new String[] {"delusional_hit1", "delusional_hit2", "delusional_hit3", "delusional_hit4"},
            "ds:delusional", new String[] {"delusional1", "delusional2", "delusional3", "delusional4"},
            "ds:delusional_small", new String[] {"delusional_small1", "delusional_small2", "delusional_small3", "delusional_small4"},
            "ds:delusional_climax", new String[] {"delusional_climax1", "delusional_climax2", "delusional_climax3", "delusional_climax4", "delusional_climax5", "delusional_climax6"},
            "ds:delusional_hearts", new String[] {"delusional_hearts"},
            "ds:delusional_hearts2", new String[] {"delusional_hearts2"},
            "ds:delusional_glitter", new String[] {"delusional_glitter"},
            "ds:delusional_end", new String[] {"delusional_end1"});

    /** calcVectorOffset: right / up / forward offsets of an entity's view */
    static double[] offset(Entity p, double xf, double yf, double zf) {
        return offset(p.getLookAngle(), p.getX(), p.getY(), p.getZ(), xf, yf, zf);
    }

    static double[] offset(Vec3 d, double lx, double ly, double lz, double xf, double yf, double zf) {
        double m = Math.hypot(d.x, d.z);
        if (m < 1e-9) m = 1e-9;
        double[] xx = norm(d.z, 0, -d.x, xf);
        double[] yy = norm(d.x / m * -d.y, m, d.z / m * -d.y, yf);
        double[] zz = norm(d.x, d.y, d.z, zf);
        return new double[] {lx + xx[0] + yy[0] + zz[0], ly + xx[1] + yy[1] + zz[1], lz + xx[2] + yy[2] + zz[2]};
    }

    private static double[] norm(double x, double y, double z, double s) {
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1e-12) return new double[] {0, 0, 0};
        return new double[] {s * x / l, s * y / l, s * z / l};
    }

    private static void delusional(McHost host, Ctx c, String id, List<String> args) {
        String[] o = String.join(" ", args).split(",");
        if (o.length < 3) return;
        double a = Double.parseDouble(o[0].trim()), b = Double.parseDouble(o[1].trim()), d = Double.parseDouble(o[2].trim());
        for (int dim = 0; dim < 3; dim++) {
            for (Ent pl : host.players(dim)) {
                if (!pl.tags().contains("delusional") || !(pl.handle() instanceof net.minecraft.server.level.ServerPlayer sp)) continue;
                double[] p = offset(sp, a, b, d);
                for (String name : DELUSIONAL.get(id)) McHost.send(sp, "P", new double[] {p[0], p[1], p[2], sp.getYRot()}, name);
            }
        }
    }

    private static void placeBlock(McHost host, Ctx c, List<String> args) {
        int radius = 0, time = 20;
        String block = null, replace = null;
        Integer light = null;
        for (String a : args) {
            if (a.startsWith("radius:")) radius = (int) num(List.of(a.substring(7)), 0);
            else if (a.startsWith("time:")) {
                time = (int) num(List.of(a.substring(5)), 0);
                if (time == 0) time = 20;
            } else if (a.startsWith("replace:")) replace = a.substring(8).replace("minecraft:", "");
            else if (a.contains("light_block")) {
                String last = a.substring(a.lastIndexOf('_') + 1);
                try {
                    light = Math.min(15, Math.max(0, Integer.parseInt(last)));
                } catch (NumberFormatException ex) {
                    light = 15;
                }
                block = "light_block";
            } else block = a.replace("minecraft:", "");
        }
        if (block != null) host.placeTemp(c, radius, time, block, light, replace);
    }

    private static double num(List<String> a, int i) {
        try {
            return i < a.size() ? Double.parseDouble(a.get(i)) : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static Vec3 view(Entity e) {
        return e.getLookAngle();
    }

    /**
     * applyKnockback(direction * horizontal, vertical): the horizontal velocity is replaced by the force (no force when the direction or the strength
     * is 0), the vertical strength is added.
     */
    static void knock(Entity e, Vec3 dir, double horizontal, double vertical) {
        double mag = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        double hx = 0, hz = 0;
        if (mag != 0 && horizontal != 0) {
            hx = dir.x / mag * horizontal;
            hz = dir.z / mag * horizontal;
        }
        Vec3 m = e.getDeltaMovement();
        e.setDeltaMovement(hx, m.y + vertical, hz);
        e.hurtMarked = true;
        if (vertical > 0) e.fallDistance = 0;
    }

    private static boolean solid(Level l, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        return l.hasChunkAt(p) && !l.getBlockState(p).isAir();
    }

    private static void autoJump(Entity e) {
        Level l = e.level();
        Vec3 look = e.getLookAngle();
        double hl = Math.sqrt(look.x * look.x + look.z * look.z);
        double height = 0;
        if (hl > 1e-6) {
            double dx = look.x / hl, dz = look.z / hl;
            double ox = e.getX(), oy = e.getY() + 0.5, oz = e.getZ();
            int hitX = 0, hitY = 0, hitZ = 0;
            boolean hit = false;
            for (double t = 0; t <= 4.5 && !hit; t += 0.1) {
                int bx = (int) Math.floor(ox + dx * t), by = (int) Math.floor(oy), bz = (int) Math.floor(oz + dz * t);
                if (solid(l, bx, by, bz)) {
                    hit = true;
                    hitX = bx;
                    hitY = by;
                    hitZ = bz;
                }
            }
            if (hit) {
                int top = hitY;
                for (int y = hitY + 4; y >= hitY; y--) {
                    if (solid(l, hitX, y, hitZ)) {
                        top = y;
                        break;
                    }
                }
                height = top + 1.5 - e.getY();
            } else {
                int bx = (int) Math.floor(e.getX()), bz = (int) Math.floor(e.getZ());
                for (int y = (int) Math.floor(e.getY()) + 5; y >= (int) Math.floor(e.getY()) - 5; y--) {
                    if (solid(l, bx, y, bz)) {
                        if (Math.abs(y - e.getY()) < 5) height = y - e.getY();
                        break;
                    }
                }
            }
        }
        knock(e, look, 6, height);
    }
}
