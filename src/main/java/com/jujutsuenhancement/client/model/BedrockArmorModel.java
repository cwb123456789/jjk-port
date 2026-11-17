package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.item.BedrockArmorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

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

    @Override public ResourceLocation getModelResource(BedrockArmorItem item) { return model; }
    @Override public ResourceLocation getTextureResource(BedrockArmorItem item) { return texture; }
    @Override public ResourceLocation getAnimationResource(BedrockArmorItem item) { return animation; }
}
