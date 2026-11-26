package com.jujutsuenhancement.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The glowing 六眼 eyes of the Bedrock player (geometry.sixeyes, texture sixeyes, additive material, hidden in the first person): two
 * thin planes on the face (bone "head", pivot 0,24,0, plane z = -4.035). Called with the pose stack in the head bone's frame, so the
 * same planes sit on the vanilla head and on the head bone of a worn skin.
 */
public final class SixEyes {
    private static final ResourceLocation TEX = new ResourceLocation("jujutsuenhancement", "textures/glow/sixeyes.png");
    /** per plane: x0, x1, top, bottom (model space of vanilla, y down, relative to the head pivot) and z. */
    private static final float[][] PLANES = {
            {-2.40782F, -0.57656F, 24F - 27.82344F, 24F - 25.20782F, -4.03516F},
            {0.57656F, 2.40782F, 24F - 27.82344F, 24F - 25.20782F, -4.03516F},
    };
    private static final Map<Integer, Integer> BITS = new ConcurrentHashMap<>();

    private SixEyes() {}

    public static void set(int entityId, int bits) {
        if (bits == 0) BITS.remove(entityId);
        else BITS.put(entityId, bits);
    }

    public static boolean has(int entityId) {
        return BITS.getOrDefault(entityId, 0) != 0;
    }

    /** Draws the eyes; {@code ps} must be at the head bone. */
    public static void draw(PoseStack ps, MultiBufferSource buffers) {
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(TEX));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        for (float[] q : PLANES) {
            float x0 = q[0] / 16F, x1 = q[1] / 16F, y0 = q[2] / 16F, y1 = q[3] / 16F, z = q[4] / 16F;
            v(vc, m, n, x0, y0, z, 0, 0, -1);
            v(vc, m, n, x1, y0, z, 1, 0, -1);
            v(vc, m, n, x1, y1, z, 1, 1, -1);
            v(vc, m, n, x0, y1, z, 0, 1, -1);
            // the same plane the other way round, so that the face shows whichever way the winding ends up
            v(vc, m, n, x0, y1, z, 0, 1, 1);
            v(vc, m, n, x1, y1, z, 1, 1, 1);
            v(vc, m, n, x1, y0, z, 1, 0, 1);
            v(vc, m, n, x0, y0, z, 0, 0, 1);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float vv, float nz) {
        vc.vertex(m, x, y, z).color(1F, 1F, 1F, 1F).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(n, 0F, 0F, nz).endVertex();
    }
}
