package com.jujutsuenhancement.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jujutsuenhancement.JujutsuEnhancement;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Which effect timelines are cutscenes (they carry a camera shot) and how long they last, read from the same data the clients play. */
final class CgIndex {
    private static final Map<String, Integer> CACHE = new HashMap<>();
    private static JsonObject movesJson;
    private static JsonObject fxIndex;

    private CgIndex() {}

    private static JsonObject read(String path) {
        try (var in = CgIndex.class.getResourceAsStream("/assets/" + JujutsuEnhancement.MODID + "/" + path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** Ticks of the cutscene {@code key} (a moves.json key or "fx:folder/function#counter"), or 0 when it is no cutscene. */
    static synchronized int ticks(String key) {
        Integer c = CACHE.get(key);
        if (c != null) return c;
        int n = 0;
        JsonObject entry = null;
        if (key.startsWith("fx:")) {
            if (fxIndex == null) fxIndex = read("bedrock/fx/index.json");
            if (fxIndex != null && fxIndex.has(key.substring(3))) {
                JsonObject file = read("bedrock/fx/" + fxIndex.get(key.substring(3)).getAsString() + ".json");
                if (file != null && file.has(key.substring(3))) entry = file.getAsJsonObject(key.substring(3));
            }
        } else if (!key.contains(":")) {
            if (movesJson == null) movesJson = read("bedrock/gojo/moves.json");
            if (movesJson != null && movesJson.has(key)) entry = movesJson.getAsJsonObject(key);
        }
        if (entry != null && entry.has("events")) {
            boolean camera = false;
            for (JsonElement ev : entry.getAsJsonArray("events")) {
                var row = ev.getAsJsonArray();
                if (row.size() > 1 && "c".equals(row.get(1).getAsString())) {
                    camera = true;
                    break;
                }
            }
            if (camera) n = entry.has("n") ? entry.get("n").getAsInt() : 100;
        }
        CACHE.put(key, n);
        return n;
    }
}
