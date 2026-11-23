package com.jujutsuenhancement.client.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.client.anim.PlayerAnims;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The locators of the addon's player model (locator_right, locator_left, chest_locator, forward_locator2 ...): the particle events of the
 * Bedrock animations are attached to them. {@code bedrock/locators.json} (tools/build_locators.py) holds the bone each locator follows and its
 * position in Bedrock model pixels (x to the entity's left, y up, -z forward). A locator on a bone of the skeleton follows the pose the playing
 * animation gives that bone (the matrices {@link PlayerAnims#pose} keeps), the others stay where they are relative to the entity.
 */
public final class Locators {
    private record Def(String bone, float x, float y, float z) {}

    private static Map<String, Def> defs;
    private static final Map<String, String> ALIAS = Map.of("rightleg_particle", "right_leg_particle", "leftleg", "leftleg_locator",
            "rightleg", "rightleg_locator", "forward_locator4", "forward_locator3");

    private Locators() {}

    private static void load() {
        if (defs != null) return;
        defs = new HashMap<>();
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager()
                    .getResource(new ResourceLocation(JujutsuEnhancement.MODID, "bedrock/locators.json"));
            if (res.isEmpty()) return;
            try (java.io.Reader r = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                for (var en : root.entrySet()) {
                    JsonObject o = en.getValue().getAsJsonObject();
                    var pos = o.getAsJsonArray("pos");
                    defs.put(en.getKey(), new Def(o.get("bone").getAsString(), pos.get(0).getAsFloat(), pos.get(1).getAsFloat(), pos.get(2).getAsFloat()));
                }
            }
        } catch (Exception ignored) {
            // no locators: the particles stay at the feet
        }
    }

    /** {left, up, forward} in blocks from the entity's feet, or null when the locator is unknown. */
    public static double[] resolve(Entity e, String name) {
        if (name == null) return null;
        load();
        Def d = defs.get(name);
        if (d == null && ALIAS.containsKey(name)) d = defs.get(ALIAS.get(name));
        if (d == null) return null;
        float px = d.x(), py = 24.0F - d.y(), pz = d.z();   // Bedrock model space -> the vanilla model space the matrices are in (y down)
        Map<String, Matrix4f> pose = PlayerAnims.pose(e.getId());
        Matrix4f m = pose == null ? null : pose.get(d.bone());
        if (m != null) {
            Vector4f v = new Vector4f(px, py, pz, 1.0F).mul(m);
            px = v.x;
            py = v.y;
            pz = v.z;
        }
        return new double[] {px / 16.0D, (24.0D - py) / 16.0D, -pz / 16.0D};
    }

    /** The turn of the model: the body, not the head. */
    public static double yaw(Entity e) {
        return e instanceof LivingEntity le ? le.yBodyRot : e.getYRot();
    }
}
