package com.jujutsuenhancement.bfn;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** {@code ds:shootprojectile}: spawns (or picks) an entity and moves it by velocity blocks per tick along the view for at most 200 ticks. */
final class Projectiles {
    private Projectiles() {}

    static void shoot(McHost host, Engine eng, Ctx c, Entity shooter, List<String> args) {
        if (args.size() < 2 || !(shooter.level() instanceof ServerLevel lv)) return;
        String type = args.get(0), name = args.get(1);
        double velocity = 3;
        String sbName = null;
        Integer sbValue = null;
        boolean nearest = false;
        String target = null;
        for (int i = 2; i < args.size(); i++) {
            String a = args.get(i);
            if (a.startsWith("velocity:")) {
                try {
                    double v = Double.parseDouble(a.substring(9));
                    velocity = v == 0 ? 3 : v;
                } catch (NumberFormatException ex) {
                    velocity = 3;
                }
            } else if (a.equals("scores:")) {
                if (i + 2 < args.size()) {
                    sbName = args.get(i + 1);
                    try {
                        sbValue = Integer.parseInt(args.get(i + 2));
                    } catch (NumberFormatException ex) {
                        sbValue = null;
                    }
                    i += 2;
                }
            } else if (a.equals("nearestTypeApply")) nearest = true;
            else if (a.startsWith("target:")) {
                target = (a.substring(7) + (i + 1 < args.size() ? " " + String.join(" ", args.subList(i + 1, args.size())) : "")).trim();
                break;
            }
        }
        Vec3 dir = shooter.getLookAngle();
        if (target != null && !target.isEmpty()) {
            Entity t = nearestNamed(lv, shooter, target, 400, null);
            if (t != null) {
                double dx = t.getX() - shooter.getX(), dy = t.getY() + 1 - (shooter.getY() + 1.6), dz = t.getZ() - shooter.getZ();
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d > 0) dir = new Vec3(dx / d, dy / d, dz / d);
            }
        }
        boolean hasName = !name.equals("null") && !name.equals("none") && !name.isEmpty();
        Entity p;
        if (nearest) {
            p = nearestNamed(lv, shooter, hasName ? name : null, 50, type);
            if (p == null) return;
        } else {
            p = BedrockEntities.create(lv, type);
            if (p == null) {
                host.unknown("shootprojectile " + type);
                return;
            }
            double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z)), pitch = Math.toDegrees(Math.asin(-dir.y));
            p.moveTo(shooter.getX() + dir.x * 0.1, shooter.getY() + 1.6 + dir.y * 0.1, shooter.getZ() + dir.z * 0.1, (float) yaw, (float) pitch);
            if (hasName) p.setCustomName(net.minecraft.network.chat.Component.literal(name));
            lv.addFreshEntity(p);
            host.track(p, McHost.dimOf(lv));
            BedrockEntities.start(p, null);
        }
        if (sbName != null && sbValue != null) eng.scores().set(sbName, new McEnt(p, 0).key(), sbValue);
        final Entity proj = p;
        final Vec3 vel = dir.scale(velocity);
        final int[] age = {0};
        host.addTask(() -> {
            if (proj.isRemoved()) return true;
            double yaw = Math.toDegrees(Math.atan2(-vel.x, vel.z));
            double len = vel.length();
            double pitch = len == 0 ? 0 : Math.toDegrees(Math.asin(-vel.y / len));
            proj.moveTo(proj.getX() + vel.x, proj.getY() + vel.y, proj.getZ() + vel.z, (float) yaw, (float) pitch);
            proj.setDeltaMovement(Vec3.ZERO);
            if (++age[0] > 200) {
                proj.discard();
                return true;
            }
            return false;
        });
    }

    /** the closest entity with the name tag (null name: any) within the distance, of the Bedrock type when given; creative/spectator players are skipped */
    private static Entity nearestNamed(ServerLevel lv, Entity from, String name, double dist, String type) {
        AABB box = from.getBoundingBox().inflate(dist);
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : lv.getEntities((Entity) null, box, x -> true)) {
            if (e == from || e.isRemoved()) continue;
            if (type != null && !McEnt.bedrockId(e).equals(type)) continue;
            if (name != null && !new McEnt(e, 0).nameTag().equals(name)) continue;
            if (e instanceof ServerPlayer sp) {
                GameType g = sp.gameMode.getGameModeForPlayer();
                if (g == GameType.CREATIVE || g == GameType.SPECTATOR) continue;
            }
            double d = e.distanceToSqr(from);
            if (d <= dist * dist && d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }
}
