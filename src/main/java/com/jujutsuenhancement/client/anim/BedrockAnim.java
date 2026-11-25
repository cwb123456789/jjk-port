package com.jujutsuenhancement.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One animation of a Bedrock animation file (rotation / position / scale keyframes per bone and the particle events),
 * sampled the way Bedrock does: linear between the "post" of a key and the "pre" of the next, or Catmull-Rom for keys
 * that say {@code "lerp_mode": "catmullrom"}. Only plain numbers appear in the animations that are ported.
 */
public final class BedrockAnim {
    /** The keys of one channel of a bone. */
    private static final class Track {
        float[] times;
        float[][] pre;
        float[][] post;
        boolean[] spline;

        float[] sample(float t) {
            int n = times.length;
            if (n == 0) return null;
            if (t <= times[0]) return pre[0];
            if (t >= times[n - 1]) return post[n - 1];
            int a = 0;
            while (a + 1 < n && times[a + 1] <= t) a++;
            int b = a + 1;
            float u = (t - times[a]) / (times[b] - times[a]);
            float[] out = new float[3];
            if (spline[a] || spline[b]) {
                float[] p0 = post[Math.max(a - 1, 0)];
                float[] p1 = post[a];
                float[] p2 = pre[b];
                float[] p3 = pre[Math.min(b + 1, n - 1)];
                for (int i = 0; i < 3; i++) {
                    float u2 = u * u, u3 = u2 * u;
                    out[i] = 0.5F * ((2 * p1[i]) + (-p0[i] + p2[i]) * u + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * u2
                            + (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * u3);
                }
            } else {
                for (int i = 0; i < 3; i++) out[i] = post[a][i] + (pre[b][i] - post[a][i]) * u;
            }
            return out;
        }
    }

    /** What a bone does at one moment; a null channel is not animated. */
    public static final class Pose {
        public float[] rot;
        public float[] pos;
        public float[] scale;
    }

    private static final class BoneTracks {
        Track rot, pos, scale;
    }

    /** A particle effect fired at a time of the animation. */
    public record Event(float time, String effect, String locator, boolean sound) {}

    public final String id;
    public final float length;
    public final boolean loop;
    private final Map<String, BoneTracks> bones = new HashMap<>();
    public final List<Event> events = new ArrayList<>();

    public BedrockAnim(String id, JsonObject json) {
        this.id = id;
        this.length = json.has("animation_length") ? json.get("animation_length").getAsFloat() : 0.0F;
        this.loop = json.has("loop") && json.get("loop").isJsonPrimitive() && json.get("loop").getAsJsonPrimitive().isBoolean()
                && json.get("loop").getAsBoolean();
        if (json.has("bones")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("bones").entrySet()) {
                BoneTracks bt = new BoneTracks();
                JsonObject o = e.getValue().getAsJsonObject();
                bt.rot = track(o.get("rotation"));
                bt.pos = track(o.get("position"));
                bt.scale = track(o.get("scale"));
                bones.put(e.getKey(), bt);
            }
        }
        readEvents(json, "particle_effects", false);
        readEvents(json, "sound_effects", true);
        events.sort(Comparator.comparingDouble(Event::time));
    }

    private void readEvents(JsonObject json, String key, boolean sound) {
        if (!json.has(key) || !json.get(key).isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject(key).entrySet()) {
            float time = Float.parseFloat(e.getKey());
            JsonElement v = e.getValue();
            if (v.isJsonArray()) {
                for (JsonElement x : v.getAsJsonArray()) addEvent(time, x.getAsJsonObject(), sound);
            } else {
                addEvent(time, v.getAsJsonObject(), sound);
            }
        }
    }

    private void addEvent(float time, JsonObject o, boolean sound) {
        if (!o.has("effect")) return;
        events.add(new Event(time, o.get("effect").getAsString(), o.has("locator") ? o.get("locator").getAsString() : null, sound));
    }

    private static float[] vec(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static Track track(JsonElement e) {
        if (e == null) return null;
        Track t = new Track();
        if (e.isJsonArray()) {
            float[] v = vec(e);
            t.times = new float[] {0};
            t.pre = new float[][] {v};
            t.post = new float[][] {v};
            t.spline = new boolean[] {false};
            return t;
        }
        List<Map.Entry<String, JsonElement>> keys = new ArrayList<>(e.getAsJsonObject().entrySet());
        keys.sort(Comparator.comparingDouble(k -> Double.parseDouble(k.getKey())));
        int n = keys.size();
        t.times = new float[n];
        t.pre = new float[n][];
        t.post = new float[n][];
        t.spline = new boolean[n];
        for (int i = 0; i < n; i++) {
            t.times[i] = Float.parseFloat(keys.get(i).getKey());
            JsonElement v = keys.get(i).getValue();
            if (v.isJsonArray()) {
                t.pre[i] = vec(v);
                t.post[i] = t.pre[i];
            } else {
                JsonObject o = v.getAsJsonObject();
                t.post[i] = vec(o.get("post"));
                t.pre[i] = o.has("pre") ? vec(o.get("pre")) : t.post[i];
                t.spline[i] = o.has("lerp_mode") && "catmullrom".equals(o.get("lerp_mode").getAsString());
            }
        }
        return t;
    }

    public boolean has(String bone) {
        return bones.containsKey(bone);
    }

    /** The pose of {@code bone} at {@code time} seconds, or null when the animation does not touch the bone. */
    public Pose sample(String bone, float time) {
        BoneTracks bt = bones.get(bone);
        if (bt == null) return null;
        Pose p = new Pose();
        if (bt.rot != null) p.rot = bt.rot.sample(time);
        if (bt.pos != null) p.pos = bt.pos.sample(time);
        if (bt.scale != null) p.scale = bt.scale.sample(time);
        return p;
    }
}
