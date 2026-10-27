package com.jujutsuenhancement.client;

import com.jujutsuenhancement.client.model.HeianSukunaModel;
import com.jujutsuenhancement.entity.HeianSukunaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class HeianSukunaRenderer extends GeoEntityRenderer<HeianSukunaEntity> {
    public HeianSukunaRenderer(EntityRendererProvider.Context context) { super(context, new HeianSukunaModel()); this.shadowRadius = 0.35F; }
}
