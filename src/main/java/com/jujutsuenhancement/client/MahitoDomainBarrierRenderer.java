package com.jujutsuenhancement.client;

import com.jujutsuenhancement.client.model.MahitoDomainBarrierModel;
import com.jujutsuenhancement.entity.MahitoDomainBarrierEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MahitoDomainBarrierRenderer extends GeoEntityRenderer<MahitoDomainBarrierEntity> {
    public MahitoDomainBarrierRenderer(EntityRendererProvider.Context context) {
        super(context, new MahitoDomainBarrierModel());
        this.shadowRadius = 0.0F;
    }
}
