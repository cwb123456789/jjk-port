package com.jujutsuenhancement.client;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.GojoAwakenedEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Opening of the screen sequence shown to everyone inside an awakened Gojo's Unlimited Void, taken from a
 * recording of the original JujutsuCraft domain:
 * <pre>
 *   0.0 s  white light fades in over the world
 *   4.0 s  red / blue / violet light-streak tunnel on black (8 s)
 *  12.0 s  tunnel washes out to white
 *  16.0 s  pure white (4 s)
 *  20.0 s  white clears and the real scene shows through
 * </pre>
 * Everything after that is the world itself ({@code UnlimitedVoidDomain}): the black sphere built from the
 * original mod's blocks, its black hole entity, ink-blot and sparkle particles. Nothing is drawn over it any
 * more; the overlay only fades out together with the lock.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DomainVoidFx implements IGuiOverlay {
    public static final DomainVoidFx OVERLAY = new DomainVoidFx();

    private static final double RANGE = 40.0D;
    private static final float FADE_OUT_TICKS = 40.0F;

    // ----------------------------------------------------------------- timeline (ticks)
    private static final float WHITE_IN_END = 80F;
    private static final float TUNNEL_START = 80F;
    private static final float TUNNEL_CLEAR = 20F;
    private static final float TUNNEL_END = 240F;
    private static final float TO_WHITE_END = 320F;
    private static final float WHITE_HOLD_END = 400F;
    private static final float WHITE_CLEAR = 30F;

    // ----------------------------------------------------------------- client state
    private static int tickCounter;
    private static int activeId = -1;
    private static int startTick;
    private static int skip;
    private static int fadeOut;
    private static boolean lastOpaque;

    // ----------------------------------------------------------------- generated scenery
    private static final int STREAKS = 280;
    private static final float[] S_ANG = new float[STREAKS];
    private static final float[] S_PHASE = new float[STREAKS];
    private static final float[] S_SPEED = new float[STREAKS];
    private static final float[] S_LEN = new float[STREAKS];
    private static final float[] S_WIDTH = new float[STREAKS];
    private static final float[] S_ALPHA = new float[STREAKS];
    private static final int[] S_COLOR = new int[STREAKS];

    private static final int DOTS = 110;
    private static final float[] D_ANG = new float[DOTS];
    private static final float[] D_PHASE = new float[DOTS];
    private static final float[] D_SPEED = new float[DOTS];
    private static final float[] D_SIZE = new float[DOTS];


    private static final int[] PALETTE = {
            0xFF3A5A, 0xFF3A5A, 0xFF5AA8, 0xE63CE6, 0x4678FF, 0x4678FF, 0x5C8CFF,
            0x96BEFF, 0x9650FF, 0x9650FF, 0xB478FF, 0xFFE6FF, 0xD2287D};

    static {
        Random rng = new Random(20261102L);
        for (int i = 0; i < STREAKS; i++) {
            S_ANG[i] = rng.nextFloat() * Mth.TWO_PI;
            S_PHASE[i] = rng.nextFloat();
            S_SPEED[i] = 0.6F + rng.nextFloat() * 1.1F;
            S_LEN[i] = 0.05F + rng.nextFloat() * 0.30F;
            boolean beam = rng.nextInt(24) == 0;
            S_WIDTH[i] = beam ? 7F + rng.nextFloat() * 7F : 0.6F + rng.nextFloat() * 1.8F;
            S_ALPHA[i] = beam ? 0.55F : 0.5F + rng.nextFloat() * 0.5F;
            S_COLOR[i] = PALETTE[rng.nextInt(PALETTE.length)];
        }
        for (int i = 0; i < DOTS; i++) {
            D_ANG[i] = rng.nextFloat() * Mth.TWO_PI;
            D_PHASE[i] = rng.nextFloat();
            D_SPEED[i] = 0.5F + rng.nextFloat() * 0.8F;
            D_SIZE[i] = 1F + rng.nextFloat() * 2.4F;
        }
    }

    private DomainVoidFx() {}

    // ------------------------------------------------------------------- tracking

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        if (mc.isPaused()) return;
        tickCounter++;

        GojoAwakenedEntity best = null;
        double bestDistance = RANGE * RANGE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof GojoAwakenedEntity gojo && gojo.isAlive() && gojo.getHealth() > 0.0F && gojo.getDomainFx() >= 0) {
                double d = gojo.distanceToSqr(mc.player);
                if (d <= bestDistance) {
                    best = gojo;
                    bestDistance = d;
                }
            }
        }
        if (best != null) {
            if (activeId != best.getId()) {
                activeId = best.getId();
                startTick = tickCounter;
                skip = best.getDomainFx();
                fadeOut = 0;
            }
        } else if (activeId != -1) {
            activeId = -1;
            fadeOut = lastOpaque ? 15 : 0;
        } else if (fadeOut > 0) {
            fadeOut--;
        }
    }

    private static void reset() {
        activeId = -1;
        fadeOut = 0;
        lastOpaque = false;
    }

    /** True while the screen sequence is running (used so other overlays can stay quiet). */
    public static boolean isActive() {
        return activeId != -1;
    }

    // ------------------------------------------------------------------- rendering

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (activeId == -1) {
            if (fadeOut > 0 && lastOpaque) {
                g.fill(0, 0, w, h, argb(fadeOut / 15.0F, 0, 0, 0));
            }
            return;
        }

        float real = tickCounter - startTick + partialTick;
        float e = real + skip;
        float master = Mth.clamp((GojoAwakenedEntity.domainDurationTicks() - real) / FADE_OUT_TICKS, 0F, 1F);
        if (master <= 0F) {
            lastOpaque = false;
            return;
        }

        float white;
        boolean tunnel = false;
        boolean black = true;
        if (e < WHITE_IN_END) {
            black = false;
            white = ease(e / WHITE_IN_END);
        } else if (e < TUNNEL_END) {
            tunnel = true;
            white = e < TUNNEL_START + TUNNEL_CLEAR ? 1F - (e - TUNNEL_START) / TUNNEL_CLEAR : 0F;
        } else if (e < TO_WHITE_END) {
            tunnel = true;
            white = ease((e - TUNNEL_END) / (TO_WHITE_END - TUNNEL_END));
        } else if (e < WHITE_HOLD_END) {
            white = 1F;
        } else {
            // The real scene (black sphere, black hole, particles) is behind this.
            black = false;
            white = e < WHITE_HOLD_END + WHITE_CLEAR ? 1F - (e - WHITE_HOLD_END) / WHITE_CLEAR : 0F;
        }
        lastOpaque = black;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        if (black) g.fill(0, 0, w, h, argb(master, 0, 0, 0));
        if (tunnel) drawTunnel(g, w, h, e, master);
        if (white > 0F) g.fill(0, 0, w, h, argb(white * master, 255, 255, 255));

        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------- tunnel

    private static void drawTunnel(GuiGraphics g, int w, int h, float t, float master) {
        Matrix4f m = g.pose().last().pose();
        float cx = w * 0.5F;
        float cy = h * 0.5F;
        float rMax = (float) Math.hypot(cx, cy) * 1.1F;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        softDisc(m, cx, cy, h * 0.6F, 78, 14, 120, 0.50F * master);

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float swirl = t * 0.0009F;
        for (int i = 0; i < STREAKS; i++) {
            float u = frac(S_PHASE[i] + t * S_SPEED[i] * 0.0155F);
            float head = rMax * (float) Math.pow(u, 2.3F);
            float tail = rMax * (float) Math.pow(Math.max(0F, u - S_LEN[i]), 2.3F);
            if (head - tail < 1F) continue;
            float ang = S_ANG[i] + swirl;
            float dx = Mth.cos(ang);
            float dy = Mth.sin(ang);
            float px = -dy;
            float py = dx;
            float widthHead = S_WIDTH[i] * (0.5F + 3.2F * u * u);
            float widthTail = widthHead * 0.12F;
            float a = Mth.clamp(S_ALPHA[i] * (0.25F + u * 1.2F), 0F, 1F) * master;
            if (u < 0.08F) a *= u / 0.08F;
            int c = S_COLOR[i];
            int r = (c >> 16) & 255;
            int gr = (c >> 8) & 255;
            int b = c & 255;
            // The streak brightens towards the viewer; the outer end also goes whiter.
            int hr = Math.min(255, r + (int) (70 * u));
            int hg = Math.min(255, gr + (int) (70 * u));
            int hb = Math.min(255, b + (int) (70 * u));
            quadVertex(bb, m, cx + dx * tail + px * widthTail, cy + dy * tail + py * widthTail, r, gr, b, 0);
            quadVertex(bb, m, cx + dx * tail - px * widthTail, cy + dy * tail - py * widthTail, r, gr, b, 0);
            quadVertex(bb, m, cx + dx * head - px * widthHead, cy + dy * head - py * widthHead, hr, hg, hb, (int) (a * 255));
            quadVertex(bb, m, cx + dx * head + px * widthHead, cy + dy * head + py * widthHead, hr, hg, hb, (int) (a * 255));
        }
        Tesselator.getInstance().end();

        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < DOTS; i++) {
            float u = frac(D_PHASE[i] + t * D_SPEED[i] * 0.011F);
            float r = rMax * (float) Math.pow(u, 1.7F);
            float x = cx + Mth.cos(D_ANG[i] + swirl) * r;
            float y = cy + Mth.sin(D_ANG[i] + swirl) * r;
            float size = D_SIZE[i] * (0.4F + u * 1.4F);
            int a = (int) (Mth.clamp(u * 1.6F, 0F, 0.9F) * master * 255);
            quadVertex(bb, m, x - size, y - size, 255, 255, 255, a);
            quadVertex(bb, m, x - size, y + size, 255, 255, 255, a);
            quadVertex(bb, m, x + size, y + size, 255, 255, 255, a);
            quadVertex(bb, m, x + size, y - size, 255, 255, 255, a);
        }
        Tesselator.getInstance().end();
    }

    /** Disc that fades to nothing at its rim. Needs the position-colour shader to be set. */
    private static void softDisc(Matrix4f m, float cx, float cy, float r, int cr, int cg, int cb, float a) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, cx, cy, 0F).color(cr, cg, cb, (int) (Mth.clamp(a, 0F, 1F) * 255)).endVertex();
        for (int i = 0; i <= 20; i++) {
            float th = i * Mth.TWO_PI / 20F;
            bb.vertex(m, cx + Mth.cos(th) * r, cy + Mth.sin(th) * r, 0F).color(cr, cg, cb, 0).endVertex();
        }
        Tesselator.getInstance().end();
    }

    /** Disc with a hard rim. */
    private static void softDiscSolid(Matrix4f m, float cx, float cy, float r, int cr, int cg, int cb, float a) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        int alpha = (int) (Mth.clamp(a, 0F, 1F) * 255);
        bb.vertex(m, cx, cy, 0F).color(cr, cg, cb, alpha).endVertex();
        for (int i = 0; i <= 48; i++) {
            float th = i * Mth.TWO_PI / 48F;
            bb.vertex(m, cx + Mth.cos(th) * r, cy + Mth.sin(th) * r, 0F).color(cr, cg, cb, alpha).endVertex();
        }
        Tesselator.getInstance().end();
    }

    private static void quadVertex(BufferBuilder bb, Matrix4f m, float x, float y, int r, int g, int b, int a) {
        bb.vertex(m, x, y, 0F).color(r, g, b, Mth.clamp(a, 0, 255)).endVertex();
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    private static int argb(float alpha, int r, int g, int b) {
        return (Mth.clamp((int) (alpha * 255F), 0, 255) << 24) | (r << 16) | (g << 8) | b;
    }
}
