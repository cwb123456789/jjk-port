package com.jujutsuenhancement.client.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jujutsuenhancement.JujutsuEnhancement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The per-tick effects of the addon's projectile functions (hollow_purple, 200hollow_purple, imaginary_purple), exported by
 * tools/build_gojo_moves.py into bedrock/gojo/projectiles.json: the particles of the function lines and of the per-tick
 * entity animation, the camera shakes, with the addon's conditions (inside a block / on the ground, 1-in-5 chance).
 */
public final class ProjFx {
    private record P(String name, boolean local, double a, double b, double c, int cond, int chance) {}

    private record K(double amp, int ticks, double radius, int cond) {}

    private record Def(List<P> particles, List<K> shakes) {}

    private static Map<String, Def> defs;
    private static final java.util.Random RNG = new java.util.Random();

    private ProjFx() {}

    private static void load(Minecraft mc) {
        if (defs != null) return;
        defs = new HashMap<>();
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(new ResourceLocation(JujutsuEnhancement.MODID, "bedrock/gojo/projectiles.json"));
            if (res.isEmpty()) return;
            try (java.io.Reader r = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                for (Map.Entry<String, JsonElement> en : root.entrySet()) {
                    JsonObject o = en.getValue().getAsJsonObject();
                    List<P> ps = new ArrayList<>();
                    for (JsonElement el : o.getAsJsonArray("p")) {
                        JsonArray a = el.getAsJsonArray();
                        ps.add(new P(a.get(0).getAsString(), a.get(1).getAsInt() == 1, a.get(2).getAsDouble(), a.get(3).getAsDouble(),
                                a.get(4).getAsDouble(), a.get(5).getAsInt(), a.get(6).getAsInt()));
                    }
                    List<K> ks = new ArrayList<>();
                    for (JsonElement el : o.getAsJsonArray("k")) {
                        JsonArray a = el.getAsJsonArray();
                        ks.add(new K(a.get(0).getAsDouble(), a.get(1).getAsInt(), a.get(2).getAsDouble(), a.get(3).getAsInt()));
                    }
                    defs.put(en.getKey(), new Def(ps, ks));
                }
            }
        } catch (Exception ignored) {
            // no effects then
        }
    }

    private static boolean solid(Minecraft mc, BlockPos pos) {
        BlockState s = mc.level.getBlockState(pos);
        return !s.isAir() && !s.is(Blocks.BARRIER);
    }

    private static boolean cond(Minecraft mc, Entity e, int cond) {
        if (cond == 0) return true;
        if (cond == 1) return solid(mc, BlockPos.containing(e.getX(), e.getY(), e.getZ()));
        return solid(mc, BlockPos.containing(e.getX(), e.getY() - 0.3D, e.getZ()));
    }

    /** One tick of the projectile {@code e}: call it on the client every tick it exists. */
    public static void tick(Entity e, String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        load(mc);
        Def def = defs.get(key);
        if (def == null) return;
        double y = Math.toRadians(e.getYRot()), p = Math.toRadians(e.getXRot());
        Vec3 fwd = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        Vec3 left = new Vec3(Math.cos(y), 0.0D, Math.sin(y));
        Vec3 up = new Vec3(-Math.sin(y) * Math.sin(p), Math.cos(p), Math.cos(y) * Math.sin(p));
        for (P pt : def.particles()) {
            if (!cond(mc, e, pt.cond())) continue;
            if (pt.chance() > 1 && RNG.nextInt(pt.chance()) != 0) continue;
            Vec3 at = pt.local()
                    ? e.position().add(left.scale(pt.a())).add(up.scale(pt.b())).add(fwd.scale(pt.c()))
                    : e.position().add(pt.a(), pt.b(), pt.c());
            FxShow.particleAt(pt.name(), at.x, at.y, at.z, e.getYRot());
        }
        for (K k : def.shakes()) {
            if (!cond(mc, e, k.cond())) continue;
            if (k.radius() > 0 && mc.player.distanceToSqr(e) > k.radius() * k.radius()) continue;
            FxShow.shakeFor(k.amp(), k.ticks());
        }
    }
}
