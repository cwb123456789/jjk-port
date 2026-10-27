package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.DomainShrineEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class DomainShrineModel extends GeoModel<DomainShrineEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(
            JujutsuEnhancement.MODID, "geo/malevolent_shrine.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            JujutsuEnhancement.MODID, "textures/entity/malevolent_shrine.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(
            JujutsuEnhancement.MODID, "animations/malevolent_shrine.animation.json");

    @Override public ResourceLocation getModelResource(DomainShrineEntity entity) { return MODEL; }
    @Override public ResourceLocation getTextureResource(DomainShrineEntity entity) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(DomainShrineEntity entity) { return ANIMATION; }
}
