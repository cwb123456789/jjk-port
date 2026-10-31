package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.YujiAwakenedEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class YujiAwakenedModel extends GeoModel<YujiAwakenedEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/gojo_awakened.geo.json");
    private static final ResourceLocation ANIMATION = new ResourceLocation(JujutsuEnhancement.MODID, "animations/yuji_awakened.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation("jujutsucraft", "textures/entities/itadori_yuji.png");

    @Override public ResourceLocation getModelResource(YujiAwakenedEntity e) { return MODEL; }
    @Override public ResourceLocation getTextureResource(YujiAwakenedEntity e) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(YujiAwakenedEntity e) { return ANIMATION; }
}
