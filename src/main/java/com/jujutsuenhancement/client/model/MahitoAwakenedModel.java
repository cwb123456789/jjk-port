package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.MahitoAwakenedEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class MahitoAwakenedModel extends GeoModel<MahitoAwakenedEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/mahito.geo.json");
    private static final ResourceLocation ANIMATION = new ResourceLocation(JujutsuEnhancement.MODID, "animations/mahito_compatible.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(JujutsuEnhancement.MODID, "textures/entity/mahito.png");
    private static final ResourceLocation STAGE2 = new ResourceLocation(JujutsuEnhancement.MODID, "textures/entity/mahito_stage2.png");

    @Override public ResourceLocation getModelResource(MahitoAwakenedEntity e) { return MODEL; }
    @Override public ResourceLocation getTextureResource(MahitoAwakenedEntity e) { return e.isStage2() ? STAGE2 : TEXTURE; }
    @Override public ResourceLocation getAnimationResource(MahitoAwakenedEntity e) { return ANIMATION; }
}
