package com.jujutsuenhancement.client;

import com.jujutsuenhancement.client.model.DomainShrineModel;
import com.jujutsuenhancement.entity.DomainShrineEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class DomainShrineRenderer extends GeoEntityRenderer<DomainShrineEntity> {
    public DomainShrineRenderer(EntityRendererProvider.Context context) {
        super(context, new DomainShrineModel());
        this.shadowRadius = 0.0F;
    }
}
