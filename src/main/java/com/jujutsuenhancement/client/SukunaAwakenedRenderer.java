package com.jujutsuenhancement.client;

import com.jujutsuenhancement.client.model.SukunaAwakenedModel;
import com.jujutsuenhancement.entity.SukunaAwakenedEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class SukunaAwakenedRenderer extends GeoEntityRenderer<SukunaAwakenedEntity> {
    public SukunaAwakenedRenderer(EntityRendererProvider.Context context) {
        super(context, new SukunaAwakenedModel());
        this.shadowRadius = 0.45F;
    }
}
