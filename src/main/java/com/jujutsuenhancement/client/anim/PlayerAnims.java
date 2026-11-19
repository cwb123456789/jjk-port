package com.jujutsuenhancement.client.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.client.bedrock.FxShow;
import com.jujutsuenhancement.item.KatanaItem;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Which Bedrock animation every player plays on this client, and the particle events of those animations. */
public final class PlayerAnims {
    private static final class Play {
        BedrockAnim anim;
        int start;
        int idleAt;
        int nextEvent;
    }

    private static Map<String, BedrockAnim> library;
    private static final Map<Integer, Play> PLAYING = new HashMap<>();
    /** The tick after which the idle animation may show again, per player (the addon's Disabled / BasicM1s gating). */
    private static final Map<Integer, Integer> IDLE_AT = new HashMap<>();
    private static final Map<Integer, float[]> ITEM_ROT = new HashMap<>();

    private PlayerAnims() {}

    private static Map<String, BedrockAnim> library() {
        if (library != null) return library;
        Map<String, BedrockAnim> out = new HashMap<>();
        try {
            var res = Minecraft.getInstance().getResourceManager()
                    .getResource(new ResourceLocation(JujutsuEnhancement.MODID, "bedrock/player_anims.json"));
            if (res.isPresent()) {
                try (Reader r = res.get().openAsReader()) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                        out.put(e.getKey(), new BedrockAnim(e.getKey(), e.getValue().getAsJsonObject()));
                    }
                }
            }
        } catch (Exception ex) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not read the player animations: {}", ex.toString());
        }
        library = out;
        return out;
    }

    public static void reload() {
        library = null;
    }

    public static BedrockAnim get(String name) {
        return library().get("animation." + name);
    }

    public static void start(int entityId, String name, int idleBlock) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        BedrockAnim anim = get(name);
        if (e == null || anim == null) return;
        Play p = new Play();
        p.anim = anim;
        p.start = e.tickCount;
        p.idleAt = e.tickCount + Math.max(idleBlock, (int) Math.ceil(anim.length * 20));
        PLAYING.put(entityId, p);
        IDLE_AT.put(entityId, p.idleAt);
    }

    /** The animation the player plays now, with its time in seconds, or null. */
    public static BedrockAnim current(Player player, float ageInTicks, float[] timeOut) {
        Play p = PLAYING.get(player.getId());
        if (p != null) {
            float t = (ageInTicks - p.start) / 20.0F;
            if (t <= p.anim.length) {
                timeOut[0] = Math.max(t, 0);
                return p.anim;
            }
        }
        // The idle grip is shown whenever the katana is held (also while sneaking and in the gaps between two
        // animations): without it the arm hangs and the blade, which lies across the hand, goes through the body.
        if (player.getMainHandItem().getItem() instanceof KatanaItem) {
            BedrockAnim idle = get("katana.idle");
            if (idle != null) {
                timeOut[0] = 0;
                return idle;
            }
        }
        return null;
    }

    public static void setItemRot(int id, float[] rot) {
        if (rot == null) ITEM_ROT.remove(id);
        else ITEM_ROT.put(id, rot);
    }

    /** The rotation (degrees, Bedrock's) the current animation gives to the held item's bone, or null. */
    public static float[] itemRot(int id) {
        return ITEM_ROT.get(id);
    }

    /** Fires the particle events that the playing animations have reached. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PLAYING.clear();
            IDLE_AT.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Play>> it = PLAYING.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Play p = en.getValue();
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null) {
                it.remove();
                continue;
            }
            float t = (e.tickCount - p.start) / 20.0F;
            while (p.nextEvent < p.anim.events.size() && p.anim.events.get(p.nextEvent).time() <= t + 0.001F) {
                BedrockAnim.Event ev = p.anim.events.get(p.nextEvent++);
                FxShow.animParticle(ev.effect(), e, ev.locator());
            }
            if (t > p.anim.length) it.remove();
        }
    }
}
