package com.jujutsuenhancement.client;

import com.jujutsuenhancement.client.model.MahitoAwakenedModel;
import com.jujutsuenhancement.entity.MahitoAwakenedEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MahitoAwakenedRenderer extends GeoEntityRenderer<MahitoAwakenedEntity> {
    public MahitoAwakenedRenderer(EntityRendererProvider.Context context) {
        super(context, new MahitoAwakenedModel());
        this.shadowRadius = 0.45F;
    }
}
