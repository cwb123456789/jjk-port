package com.jujutsuenhancement.client.bedrock;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.GojoAwakenedEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Plays the Bedrock addon's Unlimited Void (无量空处) on the client: the camera script, the screen fades and every
 * particle, all read from the addon's own data (see {@link VoidScript} and {@link ParticleDef}).
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VoidShow {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The cinematic was recorded with a wide field of view; Bedrock's maximum is 110. */
    public static final double CINEMATIC_FOV = 110.0D;
    private static final double RANGE = 40.0D;
    private static final int BREAK_LENGTH = 55;
    private static final ResourceLocation OVERLAY_ID = new ResourceLocation(JujutsuEnhancement.MODID, "void_show");

    private enum Phase { OFF, RUN, BREAK }

    private static Phase phase = Phase.OFF;
    private static VoidScript script;
    private static final ParticleSim SIM = new ParticleSim();
    private static final Map<Integer, Integer> LAST_FX = new HashMap<>();
    private static int gojoId = -1;
    private static int e;
    private static int breakTick;
    private static boolean cameraOwned;
    private static SoundInstance track;
    private static int breakStartE;
    private static Marker marker;
    private static CameraType savedCamera;
    private static VoidScript.Pose startPose;
    private static double shakeAmp;
    private static long lastNanos;
    private static float lastPartial;
    private static final Random RNG = new Random();

    /** The custom particle shader, filled in by {@code RegisterShadersEvent}. */
    public static ShaderInstance particleShader;

    public static final IGuiOverlay OVERLAY = VoidShow::renderOverlay;

    private VoidShow() {}

    // ------------------------------------------------------------------ lifecycle

    private static void start(GojoAwakenedEntity g, int fx) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        startPose = new VoidScript.Pose();
        startPose.x = cam.getPosition().x;
        startPose.y = cam.getPosition().y;
        startPose.z = cam.getPosition().z;
        startPose.pitch = cam.getXRot();
        startPose.yaw = cam.getYRot();
        script = new VoidScript(fx == 1 ? VoidScript.Kind.QUICK : VoidScript.Kind.FULL,
                g.getX(), g.getY(), g.getZ(), g.getEyeHeight(), g.getYRot(), g.getXRot());
        SIM.clear();
        gojoId = g.getId();
        e = 0;
        breakTick = 0;
        shakeAmp = 0;
        lastNanos = System.nanoTime();
        phase = Phase.RUN;
        playAudio(fx == 1);
        script.onTick(0, VoidShow::spawn);
    }

    /**
     * The addon plays {@code music.infinitevoid2} 20 ticks into the full cast and {@code music.infinitevoidmusic} when the
     * arena appears (quick cast: {@code mob.infinitevoidfast2} at once, {@code music.infinitevoid_fast} at tick 10). Those
     * music files are not in the supplied packs, so the full cast uses a track cut from the reference recording, which
     * has the same timeline (silence until tick 0, music from tick 20, arena music at 454, domain break at ~885).
     */
    private static void playAudio(boolean quick) {
        Minecraft mc = Minecraft.getInstance();
        // the addon runs stopsound on everybody in range when the cast starts
        mc.getSoundManager().stop(null, SoundSource.MUSIC);
        stopTrack();
        String name = quick ? "bedrock.unlimited_void.quick" : "bedrock.unlimited_void.full";
        track = new SimpleSoundInstance(new ResourceLocation(JujutsuEnhancement.MODID, name),
                quick ? SoundSource.HOSTILE : SoundSource.MUSIC, 1.0F, 1.0F, RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);
        mc.getSoundManager().play(track);
    }

    private static void stopTrack() {
        if (track != null) {
            Minecraft.getInstance().getSoundManager().stop(track);
            track = null;
        }
    }

    private static void beginBreak() {
        phase = Phase.BREAK;
        breakTick = 0;
        breakStartE = e;
        releaseCamera();
    }

    private static void stop() {
        releaseCamera();
        // the recorded track covers the break itself; only cut it when the show was interrupted early
        if (breakStartE < 850) stopTrack();
        else track = null;
        SIM.clear();
        phase = Phase.OFF;
        script = null;
        gojoId = -1;
    }

    private static void spawn(String name, double x, double y, double z) {
        if (SIM.particleCount() > 20000) return;
        ParticleDef def = BedrockAssets.get(name);
        if (def != null) SIM.spawn(def, x, y, z);
    }

    // ------------------------------------------------------------------ camera

    private static boolean cinematic() {
        return phase == Phase.RUN && script != null && e < script.cinematicEnd;
    }

    private static void acquireCamera(Minecraft mc) {
        if (cameraOwned || mc.level == null) return;
        if (marker == null || marker.level() != mc.level) marker = new Marker(EntityType.MARKER, mc.level);
        savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(marker);
        cameraOwned = true;
    }

    private static void releaseCamera() {
        if (!cameraOwned) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (savedCamera != null) mc.options.setCameraType(savedCamera);
        cameraOwned = false;
    }

    private static void placeCamera(float partial) {
        if (marker == null || script == null) return;
        VoidScript.Pose p = script.camera(e + partial, startPose);
        double yaw = p.yaw, pitch = p.pitch;
        if (shakeAmp > 0.001) {
            yaw += (RNG.nextDouble() - 0.5) * 2.0 * shakeAmp * 1.6;
            pitch += (RNG.nextDouble() - 0.5) * 2.0 * shakeAmp * 1.6;
        }
        marker.setPos(p.x, p.y, p.z);
        marker.xo = marker.xOld = p.x;
        marker.yo = marker.yOld = p.y;
        marker.zo = marker.zOld = p.z;
        marker.setXRot((float) Mth.clamp(pitch, -90, 90));
        marker.setYRot((float) yaw);
        marker.xRotO = marker.getXRot();
        marker.yRotO = marker.getYRot();
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (phase != Phase.OFF) stop();
            LAST_FX.clear();
            return;
        }
        if (mc.isPaused()) return;

        // A domain starting within reach starts the show.
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof GojoAwakenedEntity g)) continue;
            int fx = g.getDomainFx();
            int prev = LAST_FX.getOrDefault(g.getId(), -1);
            LAST_FX.put(g.getId(), fx);
            if (phase == Phase.OFF && prev < 0 && fx >= 0 && g.isAlive() && g.distanceToSqr(mc.player) <= RANGE * RANGE) {
                start(g, fx);
            }
        }
        if (LAST_FX.size() > 64) LAST_FX.clear();

        if (phase == Phase.RUN) {
            Entity g = mc.level.getEntity(gojoId);
            boolean running = g instanceof GojoAwakenedEntity gojo && gojo.isAlive() && gojo.getDomainFx() >= 0;
            if (!running) {
                beginBreak();
            } else {
                e++;
                script.onTick(e, VoidShow::spawn);
                shakeAmp = Math.max(script.shake(e), shakeAmp * 0.6);
                if (cinematic()) acquireCamera(mc);
                else releaseCamera();
            }
        } else if (phase == Phase.BREAK) {
            breakTick++;
            script.onBreakTick(breakTick, VoidShow::spawn);
            if (breakTick >= BREAK_LENGTH) stop();
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        lastPartial = event.renderTickTime;
        if (phase == Phase.OFF) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNanos) / 1.0e9);
        lastNanos = now;
        if (!mc.isPaused()) SIM.step(dt);
        if (cameraOwned && phase == Phase.RUN) placeCamera(event.renderTickTime);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (cameraOwned && cinematic()) event.setFOV(CINEMATIC_FOV);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (cameraOwned && cinematic()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (cameraOwned && cinematic() && !OVERLAY_ID.equals(event.getOverlay().id())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
        LAST_FX.clear();
        marker = null;
    }

    // ------------------------------------------------------------------ screen fades

    private static void renderOverlay(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        if (phase == Phase.OFF || script == null) return;
        float[] c = phase == Phase.RUN ? script.fade(e + partialTick) : VoidScript.breakFade(breakTick + partialTick);
        if (c[3] <= 0.002F) return;
        int a = Mth.clamp((int) (c[3] * 255F), 0, 255);
        int r = Mth.clamp((int) (c[0] * 255F), 0, 255);
        int gr = Mth.clamp((int) (c[1] * 255F), 0, 255);
        int b = Mth.clamp((int) (c[2] * 255F), 0, 255);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        g.fill(0, 0, w, h, (a << 24) | (r << 16) | (gr << 8) | b);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ world rendering

    private static final class Quad {
        ParticleDef def;
        double dist;
        float[] c = new float[12];
        float u0, v0, u1, v1, r, g, b, a;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (phase == Phase.OFF || script == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean wiped = phase == Phase.RUN ? script.isWiped(e) : breakTick < 10;
        float pt = event.getPartialTick();
        Camera cam = event.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = event.getPoseStack();

        if (wiped) {
            // The domain replaces the world with black; only the people inside stay visible.
            mc.getMainRenderTarget().bindWrite(false);
            RenderSystem.clearColor(0F, 0F, 0F, 1F);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            drawEntities(mc, ps, cp, pt);
        }
        drawParticles(cam, cp, ps.last().pose());
    }

    private static void drawEntities(Minecraft mc, PoseStack ps, Vec3 cp, float pt) {
        FogRenderer.setupNoFog();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        dispatcher.setRenderShadow(false);
        boolean firstPerson = mc.getCameraEntity() == mc.player;
        boolean turned = phase == Phase.RUN && e >= script.turnTick;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living.isInvisible() || living.isSpectator()) continue;
            if (firstPerson && living == mc.player) continue;
            double dx = living.getX() - script.fx, dz = living.getZ() - script.fz;
            if (dx * dx + dz * dz > 48.0D * 48.0D) continue;
            double x = Mth.lerp(pt, living.xo, living.getX());
            double z = Mth.lerp(pt, living.zo, living.getZ());
            // everybody stands on the arena floor
            double y = script.fy + (living.onGround() ? 0.0D : Mth.lerp(pt, living.yo, living.getY()) - living.getY());
            float yaw = Mth.lerp(pt, living.yRotO, living.getYRot());
            boolean gojo = living.getId() == gojoId;
            float bodyO = living.yBodyRotO, body = living.yBodyRot, headO = living.yHeadRotO, head = living.yHeadRot;
            float rotO = living.yRotO, rot = living.getYRot();
            if (gojo && turned) {
                // the addon turns Gojo to face south once everything has been moved into the arena
                living.yBodyRotO = living.yBodyRot = 0F;
                living.yHeadRotO = living.yHeadRot = 0F;
                living.yRotO = 0F;
                living.setYRot(0F);
                yaw = 0F;
            }
            try {
                dispatcher.render(living, x - cp.x, y - cp.y, z - cp.z, yaw, pt, ps, buffers, LightTexture.FULL_BRIGHT);
            } catch (Exception ex) {
                LOGGER.debug("Could not draw {} inside the void: {}", living, ex.toString());
            }
            if (gojo && turned) {
                living.yBodyRotO = bodyO;
                living.yBodyRot = body;
                living.yHeadRotO = headO;
                living.yHeadRot = head;
                living.yRotO = rotO;
                living.setYRot(rot);
            }
        }
        buffers.endBatch();
        dispatcher.setRenderShadow(true);
    }

    private static void drawParticles(Camera cam, Vec3 cp, Matrix4f view) {
        List<Quad> quads = new ArrayList<>(2048);
        Vector3f look = cam.getLookVector();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();
        double[] camPos = {cp.x, cp.y, cp.z};
        double[] right = {-left.x(), -left.y(), -left.z()};
        double[] upv = {up.x(), up.y(), up.z()};
        double[] fwd = {look.x(), look.y(), look.z()};
        SIM.render(camPos, right, upv, fwd, (def, dist, c, u0, v0, u1, v1, r, g, b, a) -> {
            Quad q = new Quad();
            q.def = def;
            q.dist = dist;
            for (int k = 0; k < 4; k++) {
                q.c[k * 3] = (float) (c[k * 3] - cp.x);
                q.c[k * 3 + 1] = (float) (c[k * 3 + 1] - cp.y);
                q.c[k * 3 + 2] = (float) (c[k * 3 + 2] - cp.z);
            }
            q.u0 = u0;
            q.v0 = v0;
            q.u1 = u1;
            q.v1 = v1;
            q.r = r;
            q.g = g;
            q.b = b;
            q.a = a;
            quads.add(q);
        });
        if (quads.isEmpty()) return;
        quads.sort(Comparator.comparingDouble((Quad q) -> q.dist).reversed());

        FogRenderer.setupNoFog();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        ShaderInstance shader = particleShader;
        if (shader != null) RenderSystem.setShader(() -> shader);
        else RenderSystem.setShader(GameRenderer::getPositionColorTexShader);

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        ResourceLocation tex = null;
        ParticleDef.Material mat = null;
        boolean open = false;
        for (Quad q : quads) {
            ResourceLocation t = BedrockAssets.texture(q.def);
            if (!open || t != tex || q.def.material != mat) {
                if (open) Tesselator.getInstance().end();
                tex = t;
                mat = q.def.material;
                RenderSystem.setShaderTexture(0, tex);
                if (mat == ParticleDef.Material.ADD) RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                        com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                else RenderSystem.defaultBlendFunc();
                bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
                open = true;
            }
            float[] c = q.c;
            bb.vertex(view, c[0], c[1], c[2]).color(q.r, q.g, q.b, q.a).uv(q.u0, q.v1).endVertex();
            bb.vertex(view, c[3], c[4], c[5]).color(q.r, q.g, q.b, q.a).uv(q.u1, q.v1).endVertex();
            bb.vertex(view, c[6], c[7], c[8]).color(q.r, q.g, q.b, q.a).uv(q.u1, q.v0).endVertex();
            bb.vertex(view, c[9], c[10], c[11]).color(q.r, q.g, q.b, q.a).uv(q.u0, q.v0).endVertex();
        }
        if (open) Tesselator.getInstance().end();

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
