package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.MahitoDomainHandsEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class MahitoDomainHandsModel extends GeoModel<MahitoDomainHandsEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/perfection_domain_hands_compat.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(JujutsuEnhancement.MODID, "textures/entity/perfection_domain_hands.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(JujutsuEnhancement.MODID, "animations/empty.animation.json");
    @Override public ResourceLocation getModelResource(MahitoDomainHandsEntity entity) { return MODEL; }
    @Override public ResourceLocation getTextureResource(MahitoDomainHandsEntity entity) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(MahitoDomainHandsEntity entity) { return ANIMATION; }
}
