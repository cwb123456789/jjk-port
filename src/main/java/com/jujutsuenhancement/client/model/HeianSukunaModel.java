package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.HeianSukunaEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class HeianSukunaModel extends GeoModel<HeianSukunaEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/heian_sukuna.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(JujutsuEnhancement.MODID, "textures/entity/heian_sukuna.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(JujutsuEnhancement.MODID, "animations/heian_sukuna.animation.json");
    @Override public ResourceLocation getModelResource(HeianSukunaEntity entity) { return MODEL; }
    @Override public ResourceLocation getTextureResource(HeianSukunaEntity entity) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(HeianSukunaEntity entity) { return ANIMATION; }
}
