package com.jujutsuenhancement.client.render;

import com.jujutsuenhancement.client.anim.AnimPlayerModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** The 六眼 eyes on the vanilla head (a worn skin draws them on its own head bone, see the skin add-on). */
public final class SixEyesLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public SixEyesLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || !SixEyes.has(player.getId())) return;
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        if (model instanceof AnimPlayerModel am && am.skinDrawn) return;
        ps.pushPose();
        model.head.translateAndRotate(ps);
        SixEyes.draw(ps, buffer);
        ps.popPose();
    }
}
