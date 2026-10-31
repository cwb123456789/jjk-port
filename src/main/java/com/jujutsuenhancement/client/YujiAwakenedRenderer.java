package com.jujutsuenhancement.client;

import com.jujutsuenhancement.client.model.YujiAwakenedModel;
import com.jujutsuenhancement.entity.YujiAwakenedEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class YujiAwakenedRenderer extends GeoEntityRenderer<YujiAwakenedEntity> {
    public YujiAwakenedRenderer(EntityRendererProvider.Context context) {
        super(context, new YujiAwakenedModel());
        this.shadowRadius = 0.45F;
    }
}
