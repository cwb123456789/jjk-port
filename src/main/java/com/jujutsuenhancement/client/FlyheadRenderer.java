package com.jujutsuenhancement.client;

import com.jujutsuenhancement.client.model.FlyheadModel;
import com.jujutsuenhancement.entity.FlyheadEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class FlyheadRenderer extends GeoEntityRenderer<FlyheadEntity> {
    public FlyheadRenderer(EntityRendererProvider.Context context) {
        super(context, new FlyheadModel());
        this.shadowRadius = 0.2F;
    }
}
