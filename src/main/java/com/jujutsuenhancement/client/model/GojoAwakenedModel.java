package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.GojoAwakenedEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class GojoAwakenedModel extends GeoModel<GojoAwakenedEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/gojo_awakened.geo.json");
    private static final ResourceLocation ANIMATION = new ResourceLocation(JujutsuEnhancement.MODID, "animations/gojo_awakened.animation.json");
    // The skin belongs to the original JujutsuCraft mod and is loaded from it at runtime.
    private static final ResourceLocation TEXTURE = new ResourceLocation("jujutsucraft", "textures/entities/gojo_satoru.png");

    @Override public ResourceLocation getModelResource(GojoAwakenedEntity entity) { return MODEL; }
    @Override public ResourceLocation getTextureResource(GojoAwakenedEntity entity) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(GojoAwakenedEntity entity) { return ANIMATION; }
}
