package com.jujutsuenhancement.client;

import com.jujutsuenhancement.client.model.HigurumaAwakenedModel;
import com.jujutsuenhancement.entity.HigurumaAwakenedEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public final class HigurumaAwakenedRenderer extends GeoEntityRenderer<HigurumaAwakenedEntity> {
    public HigurumaAwakenedRenderer(EntityRendererProvider.Context context) {
        super(context, new HigurumaAwakenedModel());
        this.shadowRadius = 0.45F;
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, HigurumaAwakenedEntity animatable) {
                return "rightItem".equals(bone.getName()) ? animatable.getMainHandItem() : null;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, HigurumaAwakenedEntity animatable) {
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }
        });
    }
}
