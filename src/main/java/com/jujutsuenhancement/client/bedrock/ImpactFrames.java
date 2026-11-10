package com.jujutsuenhancement.client.bedrock;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * The Bedrock addon's "impact frames". The addon shows a full-screen picture by sending the player a title made of one
 * private-use glyph ({@code title @a title }); the pictures are the cells of the glyph page {@code glyph_E3.png}
 * (see {@code tools/build_glyph_frames.py}). A title stays for 20 ticks ({@code title @a times 0 20 0}) unless another
 * title replaces it, and {@code title @a title §l} (an empty bold title) removes it.
 * <p>
 * {@link #show(int)} and {@link #clear()} are those two commands. A glyph without a picture in the resources (the
 * addon's 1-pixel "blank" glyphs such as {@code }) simply shows nothing, like the real thing.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ImpactFrames {
    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(JujutsuEnhancement.MODID, "impact_frames");
    public static final IGuiOverlay OVERLAY = ImpactFrames::render;

    /** {@code title @a times 0 20 0}. */
    private static final int STAY = 20;

    private static final Map<Integer, ResourceLocation> TEXTURES = new HashMap<>();
    private static int glyph;
    private static int left;

    private ImpactFrames() {}

    /** {@code title @a title <glyph>}: the frame replaces whatever title is up and stays for 20 ticks. */
    public static void show(int codepoint) {
        glyph = codepoint;
        left = STAY;
    }

    /** {@code title @a title §l}. */
    public static void clear() {
        glyph = 0;
        left = 0;
    }

    public static boolean active() {
        return glyph != 0 && texture(glyph) != null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (left > 0 && --left == 0) glyph = 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static ResourceLocation texture(int codepoint) {
        if (codepoint < 0xE300 || codepoint > 0xE3FF) return null;
        if (TEXTURES.containsKey(codepoint)) return TEXTURES.get(codepoint);
        ResourceLocation rl = new ResourceLocation(JujutsuEnhancement.MODID,
                String.format("textures/bedrock/glyph/e3%02x.png", codepoint & 0xFF));
        if (Minecraft.getInstance().getResourceManager().getResource(rl).isEmpty()) rl = null;
        TEXTURES.put(codepoint, rl);
        return rl;
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        if (glyph == 0) return;
        ResourceLocation tex = texture(glyph);
        if (tex == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderTexture(0, tex);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, 0F, h, 0F).uv(0F, 1F).endVertex();
        bb.vertex(m, w, h, 0F).uv(1F, 1F).endVertex();
        bb.vertex(m, w, 0F, 0F).uv(1F, 0F).endVertex();
        bb.vertex(m, 0F, 0F, 0F).uv(0F, 0F).endVertex();
        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
