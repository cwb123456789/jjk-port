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
    static boolean run(McHost host, Ctx c, String id, List<String> args) {
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
            default -> {
                return false;
            }
        }
        return true;
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
