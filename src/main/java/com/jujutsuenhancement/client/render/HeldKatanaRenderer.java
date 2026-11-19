package com.jujutsuenhancement.client.render;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.client.anim.PlayerAnims;
import com.jujutsuenhancement.item.KatanaItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.RenderUtils;

/** Draws the katana's attachable on the holder: the sheath on the body, the sword on the arm with the animation's item bone. */
public final class HeldKatanaRenderer extends GeoArmorRenderer<KatanaItem> {
    private GeoBone item;
    private BakedGeoModel itemModel;

    public HeldKatanaRenderer() {
        super(new GeoModel<>() {
            private final ResourceLocation model = new ResourceLocation(JujutsuEnhancement.MODID, "geo/katana.geo.json");
            private final ResourceLocation texture = new ResourceLocation(JujutsuEnhancement.MODID, "textures/armor/katana.png");
            private final ResourceLocation animation = new ResourceLocation(JujutsuEnhancement.MODID, "animations/katana.animation.json");

            @Override public ResourceLocation getModelResource(KatanaItem i) { return model; }
            @Override public ResourceLocation getTextureResource(KatanaItem i) { return texture; }
            @Override public ResourceLocation getAnimationResource(KatanaItem i) { return animation; }
        });
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel baked) {
        if (lastModel == baked) return;
        head = null;
        body = baked.getBone("body").orElse(null);
        rightArm = baked.getBone("rightArm").orElse(null);
        leftArm = null;
        rightLeg = null;
        leftLeg = null;
        rightBoot = null;
        leftBoot = null;
        item = baked.getBone("rightItem").orElse(null);
        lastModel = baked;
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
        setAllBonesVisible(true);
    }

    @Override
    protected void applyBaseTransformations(HumanoidModel<?> base) {
        if (body != null) {
            ModelPart part = base.body;
            RenderUtils.matchModelPartRot(part, body);
            body.updatePosition(part.x, -part.y, part.z);
        }
        if (rightArm != null) {
            ModelPart part = base.rightArm;
            RenderUtils.matchModelPartRot(part, rightArm);
            rightArm.updatePosition(part.x + 5, 2 - part.y, part.z);
        }
        if (item != null) {
            float[] r = currentEntity == null ? null : PlayerAnims.itemRot(currentEntity.getId());
            if (r == null) r = new float[3];
            item.updateRotation((float) Math.toRadians(-r[0]), (float) Math.toRadians(-r[1]), (float) Math.toRadians(r[2]));
        }
    }
}
