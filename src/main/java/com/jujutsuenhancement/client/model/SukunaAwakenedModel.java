package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.SukunaAwakenedEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class SukunaAwakenedModel extends GeoModel<SukunaAwakenedEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/innate_sukuna.geo.json");
    private static final ResourceLocation ANIMATION = new ResourceLocation(JujutsuEnhancement.MODID, "animations/sukuna_awakened.animation.json");
    // Skin from the original JujutsuCraft mod (possessed Megumi).
    private static final ResourceLocation TEXTURE = new ResourceLocation("jujutsucraft", "textures/entities/fushiguro_megumi_sukuna.png");

    @Override public ResourceLocation getModelResource(SukunaAwakenedEntity e) { return MODEL; }
    @Override public ResourceLocation getTextureResource(SukunaAwakenedEntity e) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(SukunaAwakenedEntity e) { return ANIMATION; }
}
