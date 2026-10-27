package com.jujutsuenhancement.client;

import com.jujutsuenhancement.client.model.MahitoDomainHandsModel;
import com.jujutsuenhancement.entity.MahitoDomainHandsEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MahitoDomainHandsRenderer extends GeoEntityRenderer<MahitoDomainHandsEntity> {
    public MahitoDomainHandsRenderer(EntityRendererProvider.Context context) {
        super(context, new MahitoDomainHandsModel());
        this.shadowRadius = 0.0F;
    }
}
