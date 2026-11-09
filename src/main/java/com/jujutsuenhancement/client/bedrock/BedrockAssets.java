package com.jujutsuenhancement.client.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jujutsuenhancement.JujutsuEnhancement;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Loads the Bedrock particle definitions and textures that tools/build_bedrock_assets.py copied into the mod. */
public final class BedrockAssets {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, ParticleDef> DEFS = new HashMap<>();
    private static final Map<ParticleDef, ResourceLocation> TEXTURES = new HashMap<>();

    private BedrockAssets() {}

    /** The definition called {@code name}, or null if the pack does not contain it. */
    public static ParticleDef get(String name) {
        if (DEFS.containsKey(name)) return DEFS.get(name);
        ParticleDef def = null;
        ResourceLocation id = new ResourceLocation(JujutsuEnhancement.MODID, "bedrock/particles/" + name + ".json");
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isPresent()) {
                try (Reader reader = res.get().openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    def = ParticleDef.parse(json);
                }
            } else {
                LOGGER.warn("Bedrock particle {} is not in the pack", name);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not read Bedrock particle {}: {}", name, e.toString());
        }
        DEFS.put(name, def);
        return def;
    }

    /** Drops everything so that a resource reload picks up changes. */
    public static void clear() {
        DEFS.clear();
        TEXTURES.clear();
    }

    public static ResourceLocation texture(ParticleDef def) {
        ResourceLocation loc = TEXTURES.get(def);
        if (loc == null) {
            String path = def.texture.startsWith("textures/") ? def.texture.substring("textures/".length()) : def.texture;
            loc = new ResourceLocation(JujutsuEnhancement.MODID, "textures/bedrock/" + path + ".png");
            try {
                AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(loc);
                // Bedrock samples particle textures with linear filtering.
                tex.setFilter(true, false);
            } catch (Exception e) {
                LOGGER.warn("Could not prepare Bedrock texture {}: {}", loc, e.toString());
            }
            TEXTURES.put(def, loc);
        }
        return loc;
    }
}
