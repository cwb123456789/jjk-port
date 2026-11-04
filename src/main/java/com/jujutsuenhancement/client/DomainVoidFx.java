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
 * Full screen sequence shown to everyone inside an awakened Gojo's Unlimited Void, rebuilt from a
 * recording of the original JujutsuCraft domain:
 * <pre>
 *   0.0 s  white light fades in over the world
 *   4.0 s  red / blue / violet light-streak tunnel on black (8 s)
 *  12.0 s  tunnel washes out to white
 *  16.0 s  pure white (4 s)
 *  20.0 s  white clears: black sky of white ink blots with a golden ring
 *  22.0 s  the sky drifts, a blue nebula band and a violet orb come into view
 *  ...     everything fades out together with the lock (45 s after the start)
 * </pre>
 * The sky is anchored to the direction the player was facing and keeps turning with the camera, so
 * looking around works like in the original. It is a screen effect, not blocks: other mobs are not
 * drawn while it is opaque.
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
    private static final float DRIFT_START = 440F;
    private static final float DRIFT_TICKS = 380F;
    private static final float NEBULA_START = 520F;

    // ----------------------------------------------------------------- client state
    private static int tickCounter;
    private static int activeId = -1;
    private static int startTick;
    private static int skip;
    private static int fadeOut;
    private static boolean lastOpaque;
    private static boolean anchored;
    private static float anchorAz;
    private static float anchorEl;
    private static Boolean splatsAvailable;

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

    private static final int SPLATS = 96;
    private static final float[] P_AZ = new float[SPLATS];
    private static final float[] P_EL = new float[SPLATS];
    private static final float[] P_SIZE = new float[SPLATS];
    private static final float[] P_ROT = new float[SPLATS];
    private static final float[] P_SHADE = new float[SPLATS];
    private static final int[] P_TEX = new int[SPLATS];

    private static final int STARS = 170;
    private static final float[] T_AZ = new float[STARS];
    private static final float[] T_EL = new float[STARS];
    private static final float[] T_PHASE = new float[STARS];

    private static final ResourceLocation[] SPLAT_TEX = new ResourceLocation[8];

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
        for (int i = 0; i < SPLATS; i++) {
            P_AZ[i] = (rng.nextFloat() * 2F - 1F) * Mth.PI;
            P_EL[i] = (rng.nextFloat() * 2F - 0.75F) * 0.75F;
            P_SIZE[i] = 0.22F + rng.nextFloat() * rng.nextFloat() * 0.95F;
            P_ROT[i] = rng.nextFloat() * 360F;
            P_SHADE[i] = rng.nextInt(10) < 6 ? 1.0F : 0.5F + rng.nextFloat() * 0.3F;
            P_TEX[i] = rng.nextInt(8);
        }
        for (int i = 0; i < STARS; i++) {
            T_AZ[i] = (rng.nextFloat() * 2F - 1F) * Mth.PI;
            T_EL[i] = (rng.nextFloat() * 2F - 1F) * 1.3F;
            T_PHASE[i] = rng.nextFloat() * Mth.TWO_PI;
        }
        for (int i = 0; i < 8; i++) {
            SPLAT_TEX[i] = new ResourceLocation("jujutsucraft", "textures/particle/particle_unlimited_void_" + (i + 1) + ".png");
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
                anchored = false;
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
        anchored = false;
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
        boolean sky = false;
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
            sky = true;
            white = e < WHITE_HOLD_END + WHITE_CLEAR ? 1F - (e - WHITE_HOLD_END) / WHITE_CLEAR : 0F;
        }
        lastOpaque = black;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        if (black) g.fill(0, 0, w, h, argb(master, 0, 0, 0));
        if (tunnel) drawTunnel(g, w, h, e, master);
        if (sky) drawSky(mc, g, w, h, e, master);
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

    // ------------------------------------------------------------------- ink sky

    private static void drawSky(Minecraft mc, GuiGraphics g, int w, int h, float e, float master) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!anchored) {
            anchored = true;
            anchorAz = camera.getYRot() * Mth.DEG_TO_RAD;
            anchorEl = -camera.getXRot() * Mth.DEG_TO_RAD;
        }
        float drift = Mth.clamp(e - DRIFT_START, 0F, DRIFT_TICKS) * 0.0087F;
        float fov = (float) Math.toRadians(mc.options.fov().get());
        float focal = (h * 0.5F) / (float) Math.tan(fov * 0.5F);
        Matrix4f m = g.pose().last().pose();
        float[] scratch = new float[3];

        // distant stars
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < STARS; i++) {
            if (!project(camera, focal, w, h, anchorAz + T_AZ[i] - drift, T_EL[i], scratch)) continue;
            float twinkle = 0.55F + 0.45F * Mth.sin(e * 0.08F + T_PHASE[i]);
            int a = (int) (twinkle * 0.85F * master * 255);
            float s = 1.1F;
            quadVertex(bb, m, scratch[0] - s, scratch[1] - s, 255, 255, 255, a);
            quadVertex(bb, m, scratch[0] - s, scratch[1] + s, 255, 255, 255, a);
            quadVertex(bb, m, scratch[0] + s, scratch[1] + s, 255, 255, 255, a);
            quadVertex(bb, m, scratch[0] + s, scratch[1] - s, 255, 255, 255, a);
        }
        Tesselator.getInstance().end();

        // nebula band and the violet orb that come into view while the sky turns
        float nebula = Mth.clamp((e - NEBULA_START + 200F) / 120F, 0F, 1F);
        if (nebula > 0F) {
            for (int k = 0; k < 42; k++) {
                float azOff = 0.55F + k * 0.095F;
                float elOff = 0.14F * Mth.sin(k * 0.27F) - 0.06F;
                if (!project(camera, focal, w, h, anchorAz + azOff - drift, elOff, scratch)) continue;
                float r = 0.34F * focal / Math.max(scratch[2], 0.35F);
                float core = 1F - Math.abs(k - 21) / 24F;
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                softDisc(m, scratch[0], scratch[1], r, 70 + (int) (60 * core), 105 + (int) (70 * core), 235, (0.07F + 0.10F * core) * nebula * master);
            }
            if (project(camera, focal, w, h, anchorAz + 1.95F - drift, 0.12F, scratch)) {
                float pulse = 0.85F + 0.15F * Mth.sin(e * 0.2F);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                softDisc(m, scratch[0], scratch[1], 46F * pulse, 150, 60, 255, 0.35F * nebula * master);
                softDisc(m, scratch[0], scratch[1], 18F * pulse, 215, 170, 255, 0.85F * nebula * master);
                softDisc(m, scratch[0], scratch[1], 6F, 255, 255, 255, 1.0F * nebula * master);
            }
        }

        // ink blots
        boolean textured = splatsAvailable();
        for (int i = 0; i < SPLATS; i++) {
            if (!project(camera, focal, w, h, anchorAz + P_AZ[i] - drift, P_EL[i], scratch)) continue;
            float size = P_SIZE[i] * focal / Math.max(scratch[2], 0.4F);
            if (scratch[0] < -size || scratch[0] > w + size || scratch[1] < -size || scratch[1] > h + size) continue;
            float shade = P_SHADE[i];
            if (textured) {
                g.pose().pushPose();
                g.pose().translate(scratch[0], scratch[1], 0F);
                g.pose().mulPose(Axis.ZP.rotationDegrees(P_ROT[i] + e * 0.02F * (i % 2 == 0 ? 1F : -1F)));
                RenderSystem.setShaderColor(shade, shade, shade, master);
                int half = (int) (size * 0.5F);
                g.blit(SPLAT_TEX[P_TEX[i]], -half, -half, half * 2, half * 2, 0F, 0F, 64, 64, 64, 64);
                g.pose().popPose();
            } else {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                int c = (int) (shade * 255);
                softDisc(m, scratch[0], scratch[1], size * 0.5F, c, c, c, 0.9F * master);
            }
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        // the golden ring sits where the player was looking when the white cleared
        if (project(camera, focal, w, h, anchorAz + 0.05F - drift, Mth.clamp(anchorEl, -0.9F, 0.9F) + 0.03F, scratch)) {
            float radius = 0.33F * focal / Math.max(scratch[2], 0.3F);
            drawRing(g, scratch[0], scratch[1], radius, e, master);
        }
    }

    private static void drawRing(GuiGraphics g, float cx, float cy, float radius, float e, float master) {
        Matrix4f m = g.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        // dark centre
        softDiscSolid(m, cx, cy, radius * 0.97F, 6, 8, 20, 0.62F * master);
        // blue halo, golden band, bright arc
        ringStrip(m, cx, cy, radius * 0.93F, radius * 0.09F, e, 0.32F * master, 120, 170, 255, 0.0F, 1.0F);
        ringStrip(m, cx, cy, radius * 1.00F, radius * 0.035F, e, 0.95F * master, 255, 205, 92, 0.2F, 1.4F);
        ringStrip(m, cx, cy, radius * 1.04F, radius * 0.07F, e, 0.28F * master, 255, 170, 70, 0.0F, 0.8F);
        // thin swirling inner rings
        ringStrip(m, cx, cy, radius * 0.78F, radius * 0.012F, e * 1.6F, 0.35F * master, 190, 215, 255, 0.5F, 0.9F);
        ringStrip(m, cx, cy, radius * 0.62F, radius * 0.010F, -e * 1.2F, 0.25F * master, 190, 215, 255, 0.5F, 0.9F);
    }

    private static void ringStrip(Matrix4f m, float cx, float cy, float radius, float halfWidth, float e,
                                  float alpha, int r, int gr, int b, float floor, float boost) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int segments = 96;
        float hot = 2.5F + e * 0.004F; // the thick bright side slowly creeps round
        for (int i = 0; i <= segments; i++) {
            float th = i * Mth.TWO_PI / segments;
            float facing = 0.5F + 0.5F * Mth.cos(th - hot);
            float widthHere = halfWidth * (0.55F + boost * facing);
            float a = Mth.clamp(alpha * (floor + (1F - floor) * (0.35F + 0.65F * facing)), 0F, 1F);
            float dx = Mth.cos(th);
            float dy = Mth.sin(th);
            bb.vertex(m, cx + dx * (radius - widthHere), cy + dy * (radius - widthHere), 0F).color(r, gr, b, (int) (a * 255)).endVertex();
            bb.vertex(m, cx + dx * (radius + widthHere), cy + dy * (radius + widthHere), 0F).color(r, gr, b, (int) (a * 255)).endVertex();
        }
        Tesselator.getInstance().end();
    }

    // ------------------------------------------------------------------- helpers

    /** Projects a direction (azimuth like Minecraft's yaw, elevation up) onto the screen. */
    private static boolean project(Camera camera, float focal, int w, int h, float az, float el, float[] out) {
        float ce = Mth.cos(el);
        float dx = -Mth.sin(az) * ce;
        float dy = Mth.sin(el);
        float dz = Mth.cos(az) * ce;
        var look = camera.getLookVector();
        var up = camera.getUpVector();
        var left = camera.getLeftVector();
        float vz = dx * look.x() + dy * look.y() + dz * look.z();
        if (vz < 0.06F) return false;
        float vx = -(dx * left.x() + dy * left.y() + dz * left.z());
        float vy = dx * up.x() + dy * up.y() + dz * up.z();
        out[0] = w * 0.5F + vx / vz * focal;
        out[1] = h * 0.5F - vy / vz * focal;
        out[2] = vz;
        return true;
    }

    private static boolean splatsAvailable() {
        if (splatsAvailable == null) {
            splatsAvailable = Minecraft.getInstance().getResourceManager().getResource(SPLAT_TEX[0]).isPresent();
        }
        return splatsAvailable;
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
