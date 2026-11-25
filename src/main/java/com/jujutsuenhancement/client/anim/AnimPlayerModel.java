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

    /** Implemented by the skin add-on: draws a worn skin's Bedrock geometry instead of the vanilla boxes (true = drawn). */
    public interface RenderHook {
        boolean render(AnimPlayerModel model, com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                int light, int overlay, float r, float g, float b, float a);
    }

    public static volatile RenderHook hook;
    /** head, body, rightArm, leftArm, rightLeg, leftLeg of the vanilla pose: x, y, z, xRot, yRot, zRot (before the animation). */
    public final float[][] vanilla = new float[6][6];
    /** the animation playing on this player and its time, null when none (read by the skin add-on). */
    public BedrockAnim curAnim;
    public float curTime;

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer buffer, int light,
            int overlay, float r, float g, float b, float a) {
        RenderHook h = hook;
        if (h != null && h.render(this, poseStack, buffer, light, overlay, r, g, b, a)) return;
        super.renderToBuffer(poseStack, buffer, light, overlay, r, g, b, a);
    }

    private void stash() {
        ModelPart[] ps = {head, body, rightArm, leftArm, rightLeg, leftLeg};
        for (int i = 0; i < 6; i++) {
            ModelPart p = ps[i];
            vanilla[i][0] = p.x;
            vanilla[i][1] = p.y;
            vanilla[i][2] = p.z;
            vanilla[i][3] = p.xRot;
            vanilla[i][4] = p.yRot;
            vanilla[i][5] = p.zRot;
        }
    }

    public AnimPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        stash();
        BedrockAnim anim = PlayerAnims.current(player, ageInTicks, time);
        curAnim = anim;
        curTime = time[0];
        if (anim == null) {
            PlayerAnims.setItemRot(player.getId(), null);
            PlayerAnims.setPose(player.getId(), null);
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
        // the world matrices of the bones, kept for the particle locators (Locators)
        java.util.Map<String, Matrix4f> bones = new java.util.HashMap<>();
        bones.put("root", rootW);
        bones.put("body", bodyW);
        BedrockAnim.Pose hp = anim.sample("head", t), rp = anim.sample("rightArm", t), lp = anim.sample("leftArm", t);
        BedrockAnim.Pose rlp = anim.sample("rightLeg", t), llp = anim.sample("leftLeg", t);
        float[] hr = {head.xRot, head.yRot, head.zRot}, rr = {rightArm.xRot, rightArm.yRot, rightArm.zRot};
        float[] lr = {leftArm.xRot, leftArm.yRot, leftArm.zRot}, rlr = {rightLeg.xRot, rightLeg.yRot, rightLeg.zRot};
        float[] llr = {leftLeg.xRot, leftLeg.yRot, leftLeg.zRot};
        bones.put("head", world(bodyW, head.x, head.y, head.z, hp, hr));
        Matrix4f rightArmW = world(bodyW, rightArm.x, rightArm.y, rightArm.z, rp, rr);
        bones.put("rightArm", rightArmW);
        bones.put("leftArm", world(bodyW, leftArm.x, leftArm.y, leftArm.z, lp, lr));
        bones.put("rightLeg", world(rootW, rightLeg.x, rightLeg.y, rightLeg.z, rlp, rlr));
        bones.put("leftLeg", world(rootW, leftLeg.x, leftLeg.y, leftLeg.z, llp, llr));
        bones.put("rightItem", world(rightArmW, -6, 9, 1, anim.sample("rightItem", t), null));
        PlayerAnims.setPose(player.getId(), bones);
        apply(body, rootW, body.x, body.y, body.z, bodyPose, bodyRot);
        apply(head, bodyW, head.x, head.y, head.z, hp, hr);
        apply(rightArm, bodyW, rightArm.x, rightArm.y, rightArm.z, rp, rr);
        apply(leftArm, bodyW, leftArm.x, leftArm.y, leftArm.z, lp, lr);
        apply(rightLeg, rootW, rightLeg.x, rightLeg.y, rightLeg.z, rlp, rlr);
        apply(leftLeg, rootW, leftLeg.x, leftLeg.y, leftLeg.z, llp, llr);

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
