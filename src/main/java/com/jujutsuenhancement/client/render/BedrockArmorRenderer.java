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
    private final BedrockArmorModel armorModel;

    public BedrockArmorRenderer(String id) {
        this(new BedrockArmorModel(id));
    }

    private BedrockArmorRenderer(BedrockArmorModel model) {
        super(model);
        this.armorModel = model;
    }

    private void record(String name, GeoBone bone) {
        armorModel.setBase(name, bone.getRotX(), bone.getRotY(), bone.getRotZ());
    }

    /** The bones are called like the addon's player skeleton calls them, not armorHead / armorBody ... */
    @Override
    protected void grabRelevantBones(BakedGeoModel baked) {
        if (lastModel == baked) return;
        head = baked.getBone("head").orElse(null);
        body = baked.getBone("body").orElse(null);
        rightArm = baked.getBone("rightArm").orElse(null);
        leftArm = baked.getBone("leftArm").orElse(null);
        rightLeg = baked.getBone("rightLeg").orElse(null);
        leftLeg = baked.getBone("leftLeg").orElse(null);
        rightBoot = null;
        leftBoot = null;
        lastModel = baked;
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
        setAllBonesVisible(true);
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
            record("head", head);
            head.updatePosition(part.x, -part.y, part.z);
        }
        if (body != null) {
            ModelPart part = base.body;
            RenderUtils.matchModelPartRot(part, body);
            record("body", body);
            body.updatePosition(part.x, -part.y, part.z);
        }
        if (rightArm != null) {
            ModelPart part = base.rightArm;
            RenderUtils.matchModelPartRot(part, rightArm);
            record("rightArm", rightArm);
            rightArm.updatePosition(part.x + 5, 2 - part.y, part.z);
        }
        if (leftArm != null) {
            ModelPart part = base.leftArm;
            RenderUtils.matchModelPartRot(part, leftArm);
            record("leftArm", leftArm);
            leftArm.updatePosition(part.x - 5, 2 - part.y, part.z);
        }
        if (rightLeg != null) {
            ModelPart part = base.rightLeg;
            RenderUtils.matchModelPartRot(part, rightLeg);
            record("rightLeg", rightLeg);
            rightLeg.updatePosition(part.x + 1.9F, 12 - part.y, part.z);
        }
        if (leftLeg != null) {
            ModelPart part = base.leftLeg;
            RenderUtils.matchModelPartRot(part, leftLeg);
            record("leftLeg", leftLeg);
            leftLeg.updatePosition(part.x - 1.9F, 12 - part.y, part.z);
        }
    }
}
