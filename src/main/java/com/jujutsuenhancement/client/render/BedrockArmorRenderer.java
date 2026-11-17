package com.jujutsuenhancement.client.render;

import com.jujutsuenhancement.client.model.BedrockArmorModel;
import com.jujutsuenhancement.item.BedrockArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Draws a Bedrock attachable on the wearer. An attachable is attached to the wearer's skeleton by bone name (head,
 * body, leftArm, rightArm, leftLeg, rightLeg) whatever the slot of the item is: the "最强の穿搭" is a leggings item
 * whose model is a whole outfit with body and arms, so every bone is shown and not only the ones of the slot.
 */
public final class BedrockArmorRenderer extends GeoArmorRenderer<BedrockArmorItem> {
    public BedrockArmorRenderer(String id) {
        super(new BedrockArmorModel(id));
    }

    // the bones are called like the addon's player skeleton calls them, not armorHead / armorBody ...
    @Override public GeoBone getHeadBone(BakedGeoModel model) { return model.getBone("head").orElse(null); }
    @Override public GeoBone getBodyBone(BakedGeoModel model) { return model.getBone("body").orElse(null); }
    @Override public GeoBone getRightArmBone(BakedGeoModel model) { return model.getBone("rightArm").orElse(null); }
    @Override public GeoBone getLeftArmBone(BakedGeoModel model) { return model.getBone("leftArm").orElse(null); }
    @Override public GeoBone getRightLegBone(BakedGeoModel model) { return model.getBone("rightLeg").orElse(null); }
    @Override public GeoBone getLeftLegBone(BakedGeoModel model) { return model.getBone("leftLeg").orElse(null); }
    @Override public GeoBone getRightBootBone(BakedGeoModel model) { return null; }
    @Override public GeoBone getLeftBootBone(BakedGeoModel model) { return null; }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
        setAllVisible(true);
    }

    /**
     * Same as GeckoLib's, but the legs use the pivots of the addon's skeleton (x = +-1.9) instead of +-2, so the bones
     * rest exactly where the geometry puts them.
     */
    @Override
    protected void applyBaseTransformations(HumanoidModel<?> base) {
        if (head != null) {
            ModelPart part = base.head;
            RenderUtils.matchModelPartRot(part, head);
            head.updatePosition(part.x, -part.y, part.z);
        }
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
        if (leftArm != null) {
            ModelPart part = base.leftArm;
            RenderUtils.matchModelPartRot(part, leftArm);
            leftArm.updatePosition(part.x - 5, 2 - part.y, part.z);
        }
        if (rightLeg != null) {
            ModelPart part = base.rightLeg;
            RenderUtils.matchModelPartRot(part, rightLeg);
            rightLeg.updatePosition(part.x + 1.9F, 12 - part.y, part.z);
        }
        if (leftLeg != null) {
            ModelPart part = base.leftLeg;
            RenderUtils.matchModelPartRot(part, leftLeg);
            leftLeg.updatePosition(part.x - 1.9F, 12 - part.y, part.z);
        }
    }
}
