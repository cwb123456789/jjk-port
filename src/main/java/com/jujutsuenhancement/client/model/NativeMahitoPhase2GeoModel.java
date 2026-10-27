package com.jujutsuenhancement.client.model;

import com.jujutsuenhancement.JujutsuEnhancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

import java.lang.reflect.Method;

/**
 * Client-side model adapter for the native JujutsuCraft Mahito entity.
 * The original human model/animation set is retained outside the two phase-two
 * skills; phase two swaps only the geometry, keeping native animation tracks.
 */
public final class NativeMahitoPhase2GeoModel extends GeoModel<GeoAnimatable> {
    private static final ResourceLocation NATIVE_MODEL = new ResourceLocation("jujutsucraft", "geo/human.geo.json");
    private static final ResourceLocation PHASE_TWO_MODEL = new ResourceLocation(JujutsuEnhancement.MODID, "geo/mahito_stage2_native.geo.json");
    private static final ResourceLocation NATIVE_ANIMATION = new ResourceLocation("jujutsucraft", "animations/human.animation.json");
    private static final ResourceLocation FALLBACK_TEXTURE = new ResourceLocation("jujutsucraft", "textures/entities/mahito.png");

    @Override
    public ResourceLocation getModelResource(GeoAnimatable animatable) {
        Entity entity = asEntity(animatable);
        return isPhaseTwo(entity) ? PHASE_TWO_MODEL : NATIVE_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(GeoAnimatable animatable) {
        Entity entity = asEntity(animatable);
        String texture = getNativeTextureName(entity);
        if ("mahito_phase2__body".equals(texture)) {
            return new ResourceLocation("jujutsucraft", "textures/entities/mahito_phase2__body.png");
        }
        if (texture != null && !texture.isBlank()) {
            return new ResourceLocation("jujutsucraft", "textures/entities/" + texture + ".png");
        }
        return FALLBACK_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(GeoAnimatable animatable) {
        // Native animation names such as domain_expansion1 are triggered by JujutsuCraft.
        return NATIVE_ANIMATION;
    }

    private static boolean isPhaseTwo(Entity entity) {
        return "mahito_phase2__body".equals(getNativeTextureName(entity));
    }

    private static String getNativeTextureName(Entity entity) {
        if (entity == null) return null;
        try {
            Method getter = entity.getClass().getMethod("getTexture");
            Object value = getter.invoke(entity);
            return value instanceof String texture ? texture : null;
        } catch (ReflectiveOperationException ignored) {
            // The native texture field is synchronized; if the optional API changes, use fallback resources.
            return null;
        }
    }

    private static Entity asEntity(GeoAnimatable animatable) {
        return animatable instanceof Entity entity ? entity : null;
    }
}
