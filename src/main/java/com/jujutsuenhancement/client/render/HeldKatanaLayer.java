package com.jujutsuenhancement.client.render;

import com.jujutsuenhancement.item.KatanaItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Draws the katana on the players that hold it (the flat item model is hidden in the third person). */
public final class HeldKatanaLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("jujutsuenhancement", "textures/armor/katana.png");
    private HeldKatanaRenderer renderer;

    public HeldKatanaLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof KatanaItem) || player.isInvisible()) return;
        if (renderer == null) renderer = new HeldKatanaRenderer();
        renderer.prepForRender(player, stack, EquipmentSlot.CHEST, getParentModel());
        VertexConsumer consumer = buffer.getBuffer(RenderType.armorCutoutNoCull(TEXTURE));
        renderer.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
