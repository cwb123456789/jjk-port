package com.jujutsuenhancement.client;

import com.jujutsuenhancement.client.model.GojoAwakenedModel;
import com.jujutsuenhancement.entity.GojoAwakenedEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GojoAwakenedRenderer extends GeoEntityRenderer<GojoAwakenedEntity> {
    public GojoAwakenedRenderer(EntityRendererProvider.Context context) {
        super(context, new GojoAwakenedModel());
        this.shadowRadius = 0.45F;
    }
}
