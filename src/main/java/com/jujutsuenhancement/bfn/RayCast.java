package com.jujutsuenhancement.bfn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** {@code scriptevent ds:raycast <length> <thickness> <function> [...]}: the Bedrock script's rayCast(). */
final class RayCast {
    private static final Set<String> EXCLUDE_FAMILIES = Set.of("projectile", "despawncito", "damage", "motionless", "disappear");
    private static final Set<String> EXCLUDE_TYPES = Set.of("item", "xp_orb", "arrow", "thrown_trident", "fireball", "dragon_fireball", "small_fireball",
            "fireworks_rocket", "fishing_hook", "xp_bottle", "ender_pearl", "lightning_bolt", "splash_potion", "lingering_potion", "area_effect_cloud", "snowball",
            "egg", "falling_block", "ds:lapse_explode", "ds:red_reversal", "ds:hollow_nuke", "ds:limitless_explosion", "ds:pressure", "ds:energy_explosion",
            "ds:massive_bomb", "ds:shrine_dmg", "ds:pachinko_impact1", "ds:bolt_explode", "ds:shikigami_impact", "ds:oxrushex", "ds:gojo_knockback",
            "gg:impact_frame", "armor_stand");
    private static final Set<String> PROTECTED = Set.of("air", "bedrock", "chest", "trapped_chest", "ender_chest", "barrel", "shulker_box", "white_shulker_box", "orange_shulker_box", "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box", "pink_shulker_box", "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box", "blue_shulker_box", "brown_shulker_box", "green_shulker_box", "red_shulker_box", "black_shulker_box", "hopper", "dropper", "dispenser", "furnace", "blast_furnace", "smoker", "brewing_stand", "cauldron", "composter", "lectern", "jukebox", "chiseled_bookshelf", "command_block", "chain_command_block", "repeating_command_block", "structure_block", "jigsaw", "barrier");
    private static final double DOWNWARD_THRESHOLD = -0.6;
    private static final int SAFE_RADIUS_SQ = 9;

    private RayCast() {}

    private static double num(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    static void run(McHost host, Engine eng, Ctx c, List<String> a) {
        if (c.self() == null || !(c.self().handle() instanceof Entity src) || a.size() < 3) return;
        Vec3 view = src.getLookAngle();
        double maxDist = num(a.get(0)), thick = num(a.get(1));
        String fn = a.get(2);
        if (Double.isNaN(maxDist) || Double.isNaN(thick)) return;
        boolean pierces = false, piercesEnemies = false;
        String particle = null;
        double pSpacing = 0.5;
        double sphereR = Double.NaN, sphereSp = Double.NaN;
        List<String> ignore = new ArrayList<>(List.of("unselect"));
        for (int i = 3; i < a.size(); i++) {
            String arg = a.get(i);
            if (arg.equals("pierces:true")) pierces = true;
            else if (arg.equals("piercesEnemies:true")) piercesEnemies = true;
            else if (arg.startsWith("particle:")) {
                particle = arg.substring(9);
                pSpacing = 0.5;
                if (i + 1 < a.size() && !a.get(i + 1).contains(":") && !Double.isNaN(num(a.get(i + 1)))) {
                    pSpacing = num(a.get(i + 1));
                    i++;
                }
            } else if (arg.startsWith("destruction:true")) {
                if (i + 2 < a.size() && !Double.isNaN(num(a.get(i + 1))) && !Double.isNaN(num(a.get(i + 2)))) {
                    sphereR = num(a.get(i + 1));
                    sphereSp = num(a.get(i + 2));
                    i += 2;
                }
            } else if (arg.startsWith("boxdestruction:true")) {
                host.unknown("raycast boxdestruction");
            } else if (arg.startsWith("ignoretag:")) {
                if (arg.length() > 10) ignore.add(arg.substring(10));
            }
        }
        ServerLevel lv = (ServerLevel) src.level();
        int dim = McHost.dimOf(lv);
        Vec3 feet = src.position();
        Vec3 head = src instanceof Player ? src.getEyePosition() : feet;
        boolean aimingDown = view.y < DOWNWARD_THRESHOLD;
        Vec3 vd = view.normalize();

        // entities near the ray (sampled every 0.5 from the feet)
        Vec3 mid = feet.add(vd.scale(maxDist / 2));
        double reach = maxDist / 2 + thick;
        String srcName = src instanceof Player p ? p.getGameProfile().getName() : src.hasCustomName() ? src.getCustomName().getString() : "";
        List<Vec3> samples = new ArrayList<>();
        for (double i = 0; i <= maxDist; i += 0.5) samples.add(feet.add(vd.scale(i)));
        record Hit(Ent e, double dist) {}
        List<Hit> hits = new ArrayList<>();
        for (Ent en : host.entities(dim)) {
            Entity e = (Entity) en.handle();
            if (e == src || !e.isAlive()) continue;
            if (EXCLUDE_TYPES.contains(en.typeId())) continue;
            boolean skip = false;
            for (String t : ignore) if (en.tags().contains(t)) skip = true;
            for (String f : en.families()) if (EXCLUDE_FAMILIES.contains(f)) skip = true;
            if (!srcName.isEmpty() && en.nameTag().equals(srcName)) skip = true;
            if (skip) continue;
            Vec3 ep = e.position();
            if (ep.distanceTo(mid) > reach) continue;
            boolean near = false;
            for (Vec3 s : samples) if (ep.distanceToSqr(s) <= thick * thick) {
                near = true;
                break;
            }
            if (near) hits.add(new Hit(en, ep.distanceTo(head)));
        }
        hits.sort(Comparator.comparingDouble(Hit::dist));
        double hitDist = maxDist;
        List<Ent> hitEntities = new ArrayList<>();
        if (piercesEnemies) for (Hit h : hits) hitEntities.add(h.e());
        else if (!hits.isEmpty()) {
            hitEntities.add(hits.get(0).e());
            hitDist = hits.get(0).dist();
        }
        if (!pierces) {
            // the first solid block of the ray
            for (double t = 0; t <= maxDist; t += 0.05) {
                BlockPos bp = BlockPos.containing(head.x + vd.x * t, head.y + vd.y * t, head.z + vd.z * t);
                if (!lv.hasChunkAt(bp) || lv.isOutsideBuildHeight(bp)) break;
                var st = lv.getBlockState(bp);
                if (st.getCollisionShape(lv, bp).isEmpty() || !st.getFluidState().isEmpty()) continue;
                var key = ForgeRegistries.BLOCKS.getKey(st.getBlock());
                boolean destructible = bp.getY() >= -64 && bp.getY() <= 319 && key != null && !PROTECTED.contains(key.getPath());
                if (destructible) {
                    double dx = bp.getX() - head.x, dy = bp.getY() - head.y, dz = bp.getZ() - head.z;
                    double bd = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (piercesEnemies) hitDist = bd;
                    else if (hitEntities.isEmpty() || bd < hitDist) {
                        hitDist = bd;
                        hitEntities.clear();
                    }
                }
                break;
            }
        }
        if (!Double.isNaN(sphereR) && sphereSp > 0) destroy(lv, dim, head, vd, hitDist, sphereR, sphereSp, feet, aimingDown);
        if (particle != null && particle.startsWith("ds:") && pSpacing > 0) {
            for (double i = 0; i <= hitDist; i += pSpacing) {
                Vec3 p = feet.add(vd.scale(i));
                host.sendNear(dim, p.x, p.y, p.z, 128.0, "P", new double[] {p.x, p.y, p.z, 0}, particle.substring(3));
            }
        }
        for (Ent e : hitEntities) eng.runFunction(fn, new Ctx(e, e.x(), e.y(), e.z(), e.yaw(), e.pitch(), false, dim));
        Vec3 end = feet.add(vd.scale(hitDist));
        eng.runFunction(fn, new Ctx(null, end.x, end.y, end.z, 0, 0, false, dim));
    }

    private static void destroy(ServerLevel lv, int dim, Vec3 src, Vec3 dir, double dist, double radius, double spacing, Vec3 feetLoc, boolean aimingDown) {
        // only where a player with the tag "griefable" is in the dimension
        boolean any = false;
        for (var p : lv.players()) if (p.getTags().contains("griefable")) any = true;
        if (!any) return;
        double r2 = radius * radius;
        int feetX = (int) Math.floor(feetLoc.x), feetZ = (int) Math.floor(feetLoc.z);
        double feetY = feetLoc.y;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (double i = 0; i <= dist; i += spacing) {
            double cx = src.x + dir.x * i, cy = src.y + dir.y * i, cz = src.z + dir.z * i;
            for (double x = -radius; x <= radius; x++) {
                double x2 = x * x;
                if (x2 > r2) continue;
                int bx = (int) Math.floor(cx + x);
                for (double z = -radius; z <= radius; z++) {
                    double xz2 = x2 + z * z;
                    if (xz2 > r2) continue;
                    int bz = (int) Math.floor(cz + z);
                    boolean safe = !aimingDown && ((bx - feetX) * (bx - feetX) + (bz - feetZ) * (bz - feetZ)) < SAFE_RADIUS_SQ;
                    for (double y = -radius; y <= radius; y++) {
                        if (xz2 + y * y > r2) continue;
                        int by = (int) Math.floor(cy + y);
                        if (by < -64 || by > 319) continue;
                        if (safe && by <= feetY + 1) continue;
                        mp.set(bx, by, bz);
                        if (!lv.hasChunkAt(mp)) continue;
                        var st = lv.getBlockState(mp);
                        if (st.isAir() || st.is(Blocks.BEDROCK) || st.is(Blocks.BARRIER)) continue;
                        lv.setBlock(mp, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}
