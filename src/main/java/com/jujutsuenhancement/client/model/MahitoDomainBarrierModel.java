package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.MahitoDomainBarrierEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class MahitoDomainBarrierModel extends GeoModel<MahitoDomainBarrierEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/mahito_domain_barrier.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(JujutsuEnhancement.MODID, "textures/entity/mahito_domain_barrier.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(JujutsuEnhancement.MODID, "animations/empty.animation.json");
    @Override public ResourceLocation getModelResource(MahitoDomainBarrierEntity entity) { return MODEL; }
    @Override public ResourceLocation getTextureResource(MahitoDomainBarrierEntity entity) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(MahitoDomainBarrierEntity entity) { return ANIMATION; }
}
