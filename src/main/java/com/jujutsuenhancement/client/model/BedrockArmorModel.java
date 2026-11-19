package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.item.BedrockArmorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/** Geometry, texture and animation of one piece of equipment, by the addon's id. */
public final class BedrockArmorModel extends GeoModel<BedrockArmorItem> {
    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;

    public BedrockArmorModel(String id) {
        this.model = new ResourceLocation(JujutsuEnhancement.MODID, "geo/" + id + ".geo.json");
        this.texture = new ResourceLocation(JujutsuEnhancement.MODID, "textures/armor/" + id + ".png");
        this.animation = new ResourceLocation(JujutsuEnhancement.MODID, "animations/" + id + ".animation.json");
    }

    /** Rotation (GeckoLib's sign convention) that the wearer's matching body part gives each tracked bone this frame. */
    private final Map<String, float[]> base = new HashMap<>();

    /** Called by the renderer for every tracked bone after it copied the wearer's pose onto it. */
    public void setBase(String bone, float x, float y, float z) {
        base.put(bone, new float[] {x, y, z});
    }

    /**
     * GeckoLib sets the rotation of a bone that has rotation keyframes to (keyframe + the geometry's rotation), which
     * throws away the pose copied from the wearer: the sleeves of an animated cloak would hang still while the arms
     * move. The attachable's animation is added on top of the wearer's pose, as Bedrock does.
     */
    @Override
    public void setCustomAnimations(BedrockArmorItem animatable, long instanceId, AnimationState<BedrockArmorItem> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        for (Map.Entry<String, float[]> e : base.entrySet()) {
            CoreGeoBone bone = getAnimationProcessor().getBone(e.getKey());
            if (bone == null) continue;
            float[] b = e.getValue();
            float x = bone.getRotX(), y = bone.getRotY(), z = bone.getRotZ();
            if (Math.abs(x - b[0]) < 1.0E-6F && Math.abs(y - b[1]) < 1.0E-6F && Math.abs(z - b[2]) < 1.0E-6F) continue;
            bone.setRotX(b[0] + x);
            bone.setRotY(b[1] + y);
            bone.setRotZ(b[2] + z);
        }
    }

    @Override public ResourceLocation getModelResource(BedrockArmorItem item) { return model; }
    @Override public ResourceLocation getTextureResource(BedrockArmorItem item) { return texture; }
    @Override public ResourceLocation getAnimationResource(BedrockArmorItem item) { return animation; }
}
