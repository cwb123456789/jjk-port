package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.HigurumaAwakenedEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class HigurumaAwakenedModel extends GeoModel<HigurumaAwakenedEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/gojo_awakened.geo.json");
    private static final ResourceLocation ANIMATION = new ResourceLocation(JujutsuEnhancement.MODID, "animations/higuruma_awakened.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation("jujutsucraft", "textures/entities/higuruma_hiromi.png");

    @Override public ResourceLocation getModelResource(HigurumaAwakenedEntity e) { return MODEL; }
    @Override public ResourceLocation getTextureResource(HigurumaAwakenedEntity e) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(HigurumaAwakenedEntity e) { return ANIMATION; }
}
