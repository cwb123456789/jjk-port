package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.MahitoPrototypeEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class MahitoPrototypeModel extends GeoModel<MahitoPrototypeEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/mahito.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(JujutsuEnhancement.MODID, "textures/entity/mahito.png");
    private static final ResourceLocation STAGE2_MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/mahito_stage2.geo.json");
    private static final ResourceLocation STAGE2_TEXTURE = new ResourceLocation(JujutsuEnhancement.MODID, "textures/entity/mahito_stage2.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(JujutsuEnhancement.MODID, "animations/mahito_compatible.animation.json");
    @Override public ResourceLocation getModelResource(MahitoPrototypeEntity entity) { return entity.isTransformed() ? STAGE2_MODEL : MODEL; }
    @Override public ResourceLocation getTextureResource(MahitoPrototypeEntity entity) { return entity.isTransformed() ? STAGE2_TEXTURE : TEXTURE; }
    @Override public ResourceLocation getAnimationResource(MahitoPrototypeEntity entity) { return ANIMATION; }
}
