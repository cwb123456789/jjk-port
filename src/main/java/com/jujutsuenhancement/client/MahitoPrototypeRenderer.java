package com.jujutsuenhancement.client;

import com.jujutsuenhancement.client.model.MahitoPrototypeModel;
import com.jujutsuenhancement.entity.MahitoPrototypeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MahitoPrototypeRenderer extends GeoEntityRenderer<MahitoPrototypeEntity> {
    public MahitoPrototypeRenderer(EntityRendererProvider.Context context) { super(context, new MahitoPrototypeModel()); this.shadowRadius = 0.35F; }
}
