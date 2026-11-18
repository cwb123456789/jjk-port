package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.FlyheadEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** geometry.flyhead1, textures/entity/curses/flyhead, animation.flyhead of the addon's client entity ds:flyhead. */
public final class FlyheadModel extends GeoModel<FlyheadEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/flyhead.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(JujutsuEnhancement.MODID, "textures/entity/flyhead.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(JujutsuEnhancement.MODID, "animations/flyhead.animation.json");

    @Override public ResourceLocation getModelResource(FlyheadEntity entity) { return MODEL; }
    @Override public ResourceLocation getTextureResource(FlyheadEntity entity) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(FlyheadEntity entity) { return ANIMATION; }
}
