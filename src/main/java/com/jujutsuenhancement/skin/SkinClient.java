package com.jujutsuenhancement.skin;

import com.jujutsuenhancement.client.anim.AnimPlayerModel;
import com.jujutsuenhancement.client.anim.BedrockAnim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Draws the players that wear a skin of the skin mods with the addon's animations: the skin mods' own renderer is switched off for the
 * frame (their skin state is taken out and put back), and the vanilla player render calls {@link #draw}, which poses the skin's Bedrock
 * bones with the Bedrock animation (or the vanilla pose), and draws them with the texture and a full-bright glow layer.
 */
final class SkinClient {
    private record Active(SkinBridge bridge, Object state, String skin, boolean bedrock, int entityId, MultiBufferSource buffers) {}

    private static Active active;
    private static final List<Active> TO_RESTORE = new ArrayList<>();
    private static final String[] NAMES = {"head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"};
    /** after 咒术替换: the skin screen was opened, tell the server when it is closed so the CG restarts */
    static boolean waitingScreen;
    private static int waitTicks;

    private SkinClient() {}

    static void init() {
        MinecraftForge.EVENT_BUS.register(new SkinClient());
        AnimPlayerModel.hook = SkinClient::draw;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        for (SkinBridge b : SkinBridge.all()) {
            Object st = b.state(player.getId());
            if (st == null) continue;
            Active a = new Active(b, st, b.skinOf(st), b.bedrockOf(st), player.getId(), event.getMultiBufferSource());
            b.removeState(player.getId());   // the skin mod's own renderer sees no skin this frame
            TO_RESTORE.add(a);
            active = a;
            return;
        }
        active = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPost(RenderLivingEvent.Post<?, ?> event) {
        restore();
    }

    @SubscribeEvent
    public void onRenderEnd(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) restore();
    }

    private static void restore() {
        active = null;
        for (Active a : TO_RESTORE) a.bridge().setState(a.entityId(), a.skin(), a.bedrock());
        TO_RESTORE.clear();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !waitingScreen) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && ++waitTicks > 3) {
            waitingScreen = false;
            waitTicks = 0;
            SkinNet.CHANNEL.sendToServer(new SkinNet.Done());
        }
        if (mc.screen != null) waitTicks = 0;
    }

    /** Opens the skin screen of the (first or chosen) skin mod; the CG restarts when it is closed. */
    static void openScreen(boolean replayAfter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        List<SkinBridge> all = SkinBridge.all();
        if (all.isEmpty()) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("没有安装皮肤包"), false);
            if (replayAfter) SkinNet.CHANNEL.sendToServer(new SkinNet.Done());
            return;
        }
        waitingScreen = replayAfter;
        waitTicks = 0;
        if (all.size() == 1) all.get(0).openScreen(mc.player.getId());
        else mc.setScreen(new PackSelectScreen(all));
    }

    static void openFrom(SkinBridge b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) b.openScreen(mc.player.getId());
    }

    private static float[] rad(float[] deg) {
        return new float[] {(float) Math.toRadians(deg[0]), (float) Math.toRadians(deg[1]), (float) Math.toRadians(deg[2])};
    }

    /** The render hook of the animated player model. */
    private static boolean draw(AnimPlayerModel m, PoseStack ps, VertexConsumer vanillaBuffer, int light, int overlay, float r, float g, float b, float a) {
        Active act = active;
        if (act == null || act.skin() == null) return false;
        SkinBridge br = act.bridge();
        Object bm = br.model(act.state());
        SkinTextures.Pair tex = SkinTextures.get(br.texture(act.skin()));
        if (bm == null || tex == null) return false;

        br.reset(bm);
        ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        // the vanilla pose as the skin mods do it: the part's change from its initial pose, added to the bone
        for (int i = 0; i < 6; i++) {
            Object bone = br.bone(bm, NAMES[i]);
            if (bone == null) continue;
            var init = parts[i].getInitialPose();
            float[] v = m.vanilla[i];
            br.addDelta(bone, v[0] - init.x, v[1] - init.y, v[2] - init.z, v[3] - init.xRot, v[4] - init.yRot, v[5] - init.zRot);
        }
        // a Bedrock animation names bones of the player skeleton: the skin's bones of those names take its pose
        BedrockAnim anim = m.curAnim;
        if (anim != null) {
            for (String name : anim.boneNames()) {
                Object bone = br.bone(bm, name);
                if (bone == null) continue;
                BedrockAnim.Pose p = anim.sample(name, m.curTime);
                if (p == null) continue;
                br.setFromAnim(bone, p.rot == null ? null : rad(p.rot), p.pos);
            }
        }
        MultiBufferSource buffers = act.buffers();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex.base()));
        br.render(bm, ps, vc, light, overlay, r, g, b, a);
        if (tex.glow() != null) {
            br.render(bm, ps, buffers.getBuffer(RenderType.eyes(tex.glow())), 0xF000F0, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (com.jujutsuenhancement.client.render.SixEyes.has(act.entityId())) {
            Object head = br.bone(bm, "head");
            if (head != null) {
                ps.pushPose();
                br.translateTo(bm, head, ps);
                com.jujutsuenhancement.client.render.SixEyes.draw(ps, buffers);
                ps.popPose();
            }
        }
        return true;
    }
}
