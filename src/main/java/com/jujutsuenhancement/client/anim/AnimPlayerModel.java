package com.jujutsuenhancement.client.anim;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The player model with Bedrock animations on top of the vanilla pose.
 *
 * <p>A Bedrock player animation sets bones (root, body, head, arms, legs) in the addon's skeleton: root > body > head /
 * arms and root > legs. A bone the animation names replaces the pose of the vanilla part; the others keep it. Bedrock's
 * rotation angles are the same numbers as the vanilla parts' (checked against GeckoLib's own conversion), and a
 * position (x, y, z) of Bedrock is (x, -y, z) in the model space of vanilla, whose y points down. The parents' pose is
 * carried to the children by composing matrices and writing the result into the part (position + Euler angles).
 */
public final class AnimPlayerModel extends PlayerModel<AbstractClientPlayer> {
    private final float[] time = new float[1];

    public AnimPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        BedrockAnim anim = PlayerAnims.current(player, ageInTicks, time);
        if (anim == null) {
            PlayerAnims.setItemRot(player.getId(), null);
            return;
        }
        float t = time[0];
        BedrockAnim.Pose root = anim.sample("root", t);
        BedrockAnim.Pose bodyPose = anim.sample("body", t);
        PlayerAnims.setItemRot(player.getId(), pose(anim.sample("rightItem", t)));

        Matrix4f rootW = new Matrix4f();
        if (root != null) rootW = world(new Matrix4f(), 0, 24, 0, root, null);
        Matrix4f bodyW = rootW;
        float[] bodyRot = {body.xRot, body.yRot, body.zRot};
        bodyW = world(rootW, body.x, body.y, body.z, bodyPose, bodyRot);
        apply(body, rootW, body.x, body.y, body.z, bodyPose, bodyRot);
        apply(head, bodyW, head.x, head.y, head.z, anim.sample("head", t), new float[] {head.xRot, head.yRot, head.zRot});
        apply(rightArm, bodyW, rightArm.x, rightArm.y, rightArm.z, anim.sample("rightArm", t),
                new float[] {rightArm.xRot, rightArm.yRot, rightArm.zRot});
        apply(leftArm, bodyW, leftArm.x, leftArm.y, leftArm.z, anim.sample("leftArm", t),
                new float[] {leftArm.xRot, leftArm.yRot, leftArm.zRot});
        apply(rightLeg, rootW, rightLeg.x, rightLeg.y, rightLeg.z, anim.sample("rightLeg", t),
                new float[] {rightLeg.xRot, rightLeg.yRot, rightLeg.zRot});
        apply(leftLeg, rootW, leftLeg.x, leftLeg.y, leftLeg.z, anim.sample("leftLeg", t),
                new float[] {leftLeg.xRot, leftLeg.yRot, leftLeg.zRot});

        hat.copyFrom(head);
        jacket.copyFrom(body);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
    }

    private static float[] pose(BedrockAnim.Pose p) {
        return p == null ? new float[3] : p.rot == null ? new float[3] : p.rot;
    }

    private static Quaternionf rot(float[] deg) {
        return new Quaternionf().rotationZYX((float) Math.toRadians(deg[2]), (float) Math.toRadians(deg[1]),
                (float) Math.toRadians(deg[0]));
    }

    private static Quaternionf rotRad(float[] rad) {
        return new Quaternionf().rotationZYX(rad[2], rad[1], rad[0]);
    }

    /** W = parent * T(pivot + offset) * R * T(-pivot), in pixels of the vanilla model space. */
    private static Matrix4f world(Matrix4f parent, float px, float py, float pz, BedrockAnim.Pose pose, float[] vanillaRotRad) {
        Quaternionf q = pose != null && pose.rot != null ? rot(pose.rot) : vanillaRotRad != null ? rotRad(vanillaRotRad) : new Quaternionf();
        float ox = 0, oy = 0, oz = 0;
        if (pose != null && pose.pos != null) {
            ox = pose.pos[0];
            oy = -pose.pos[1];
            oz = pose.pos[2];
        }
        return new Matrix4f(parent).translate(px + ox, py + oy, pz + oz).rotate(q).translate(-px, -py, -pz);
    }

    private static void apply(ModelPart part, Matrix4f parent, float px, float py, float pz, BedrockAnim.Pose pose,
                              float[] vanillaRotRad) {
        Quaternionf q = pose != null && pose.rot != null ? rot(pose.rot) : rotRad(vanillaRotRad);
        float ox = 0, oy = 0, oz = 0;
        if (pose != null && pose.pos != null) {
            ox = pose.pos[0];
            oy = -pose.pos[1];
            oz = pose.pos[2];
        }
        Vector4f at = new Vector4f(px + ox, py + oy, pz + oz, 1).mul(parent);
        Quaternionf total = parent.getNormalizedRotation(new Quaternionf()).mul(q);
        Vector3f euler = total.getEulerAnglesZYX(new Vector3f());
        part.x = at.x;
        part.y = at.y;
        part.z = at.z;
        part.xRot = euler.x;
        part.yRot = euler.y;
        part.zRot = euler.z;
        if (pose != null && pose.scale != null) {
            part.xScale = pose.scale[0];
            part.yScale = pose.scale[1];
            part.zScale = pose.scale[2];
        } else {
            part.xScale = 1;
            part.yScale = 1;
            part.zScale = 1;
        }
    }
}
