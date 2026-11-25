package com.jujutsuenhancement.skin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The Bedrock "emissive alpha" of the skin textures: a pixel whose alpha is below 255 (but not 0) is opaque and glows, the less alpha the
 * brighter. The skin mods draw those pixels as nearly invisible, so the add-on makes two textures of every skin texture: the base one
 * (those pixels opaque, normally lit) and the glow one (only those pixels, drawn with full brightness over the base).
 */
final class SkinTextures {
    record Pair(ResourceLocation base, ResourceLocation glow) {}

    private static final Map<ResourceLocation, Pair> CACHE = new HashMap<>();

    private SkinTextures() {}

    static void clear() {
        CACHE.clear();
    }

    static Pair get(ResourceLocation src) {
        if (src == null) return null;
        Pair hit = CACHE.get(src);
        if (hit != null) return hit.base() == null ? null : hit;
        Pair made = make(src);
        CACHE.put(src, made == null ? new Pair(null, null) : made);
        return made;
    }

    private static Pair make(ResourceLocation src) {
        Minecraft mc = Minecraft.getInstance();
        try {
            var res = mc.getResourceManager().getResource(src);
            if (res.isEmpty()) return null;
            NativeImage in;
            try (InputStream is = res.get().open()) {
                in = NativeImage.read(is);
            }
            int w = in.getWidth(), h = in.getHeight();
            NativeImage base = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            NativeImage glow = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            boolean any = false;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int px = in.getPixelRGBA(x, y);   // ABGR
                    int a = px >>> 24;
                    if (a > 0 && a < 255) {
                        any = true;
                        int r = px & 0xFF, g = (px >> 8) & 0xFF, b = (px >> 16) & 0xFF;
                        base.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
                        float k = (255 - a) / 255.0F;
                        int rr = Math.round(r * k), gg = Math.round(g * k), bb = Math.round(b * k);
                        glow.setPixelRGBA(x, y, 0xFF000000 | (bb << 16) | (gg << 8) | rr);
                    } else {
                        base.setPixelRGBA(x, y, px);
                        glow.setPixelRGBA(x, y, 0);
                    }
                }
            }
            in.close();
            String stem = src.getNamespace() + "/" + src.getPath().replaceAll("[^a-z0-9_/.-]", "_");
            ResourceLocation b = new ResourceLocation("jujutsuenhancement", "skin_cache/" + stem + "/base");
            mc.getTextureManager().register(b, new DynamicTexture(base));
            ResourceLocation g = null;
            if (any) {
                g = new ResourceLocation("jujutsuenhancement", "skin_cache/" + stem + "/glow");
                mc.getTextureManager().register(g, new DynamicTexture(glow));
            } else {
                glow.close();
            }
            return new Pair(b, g);
        } catch (Exception e) {
            LogUtils.getLogger().warn("[skin add-on] cannot process the texture {}: {}", src, e.toString());
            return null;
        }
    }
}
