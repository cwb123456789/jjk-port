package com.jujutsuenhancement.ui;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.bfn.BfnRuntime;
import com.jujutsuenhancement.net.HudPacket;
import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.stats.Energy;
import com.jujutsuenhancement.stats.PlayerStats;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * scripts/index.js bardisplay(): every 5 ticks each player gets the title
 * {@code [a:grade.. a:exp_bar.. a:<barcolor>ce_bar.. a:health_bar.. a:evasive_bar..]} (+ fever / heat / flames variants), and the action bar
 * numbers. The title is built here exactly as the script builds it; the client matches the same tokens.
 * <p>
 * The values come from the command engine's scoreboard while {@code /jje bfn on}, else from the Java stats (Energy, health; the rest
 * as the quick-command menu sets it).
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HudSync {
    private static final Map<UUID, Object[]> LAST = new ConcurrentHashMap<>();

    private HudSync() {}

    private interface Src {
        int get(String obj);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 5 != 0) return;
        Src s = source(p);
        var tags = p.getTags();
        boolean hr = tags.contains("heavenlyrestriction");
        boolean absorptionTag = tags.contains("absorption");
        boolean off = tags.contains("noui") || tags.contains("oldui") || tags.contains("nogui") || tags.contains("abilitying") || tags.contains("menu");
        int health = s.get("detect_health");
        int healthMax = s.get("health_max");
        int energy = s.get("Energy");
        int cap = s.get("EnergyCap");
        if (energy > cap) energy = cap;
        int evade = s.get("Evade");
        int grade = s.get("grade");
        int exp = s.get("Exp");
        int expMax = s.get("ExpMax");
        int finger = s.get("finger");
        int fuga = s.get("fuga");
        int fever = s.get("fever");
        int heat = s.get("overheat");
        int absorption = s.get("absorption");
        int barcolor = s.get("barcolor");
        boolean sukuna = tags.contains("sukuna");
        boolean lovetrain = tags.contains("lovetrain");
        boolean discharge = tags.contains("discharge");

        String title = "";
        if (!off) {
            StringBuilder t = new StringBuilder("[a:grade").append(grade);
            if (hr) {
                t.append(" a:exp_bar").append(n(exp, expMax, 1)).append(" a:hrce_bar");
            } else {
                if (discharge) t.append(" a:heat").append(n(heat, 100, 1));
                else if (lovetrain) t.append(" a:fever").append(n(fever, 100, 1));
                if (sukuna && finger >= 15) t.append(" a:flames_bar").append(n(fuga, 500, 1));
                t.append(" a:exp_bar").append(n(exp, expMax, 1)).append(" a:").append(barcolor).append("ce_bar").append(n(energy, cap, 1));
            }
            t.append(" a:health_bar").append(n(health, healthMax, 1)).append(" a:evasive_bar").append(n(evade, 100, 1)).append(']');
            title = t.toString();
        } else if (sukuna && finger >= 15 && !tags.contains("noui") && tags.contains("oldui") && !tags.contains("nogui") && !tags.contains("abilitying") && !tags.contains("menu")) {
            title = "[a:flames_bar" + n(fuga, 500, 1) + "]";
        }

        int flags = 0;
        if (hr) flags |= 1;
        if (absorptionTag) flags |= 2;
        if (discharge) flags |= 4;
        if (lovetrain) flags |= 8;
        if (s.get("cutscene") >= 1 || tags.contains("menu")) flags |= 16;
        if (tags.contains("menu")) flags |= 32;
        if (tags.contains("oldui") && !tags.contains("noui") && !tags.contains("nogui") && !tags.contains("abilitying") && !tags.contains("menu")) flags |= 64;
        if (tags.contains("spectator") || tags.contains("heian_awakening") || tags.contains("yuji_cutscene") || tags.contains("plusultra")
                || tags.contains("plusultra_victim") || tags.contains("plusultra_brother") || tags.contains("delusional_cutscene")
                || tags.contains("stats") || tags.contains("menu")) flags |= 128;
        int level = s.get("level");
        int[] nums = {energy, cap, health, healthMax, absorption, flags, exp, expMax, level, evade, heat, fever};
        Object[] last = LAST.get(p.getUUID());
        long now = p.level().getGameTime();
        if (last != null && last[0].equals(title) && Arrays.equals((int[]) last[1], nums) && now - (Long) last[2] < 20) return;
        LAST.put(p.getUUID(), new Object[] {title, nums, now});
        ModNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HudPacket(title, nums));
    }

    /** {@code Math.ceil((x / max) * 24)} of the script; NaN / Infinity (max 0) match no frame. */
    private static String n(int x, int max, int unused) {
        if (max == 0) return x == 0 ? "NaN" : (x > 0 ? "Infinity" : "-Infinity");
        return Long.toString((long) Math.ceil((double) x / max * 24));
    }

    private static Src source(ServerPlayer p) {
        if (BfnRuntime.isEnabled()) return obj -> BfnRuntime.score(p, obj);
        return obj -> switch (obj) {
            case "detect_health" -> p.isAlive() ? Math.round(p.getHealth()) : 0;
            case "health_max" -> Math.round(p.getMaxHealth());
            case "Energy" -> Energy.get(p);
            case "EnergyCap" -> Energy.cap(p);
            case "Evade" -> 100;
            case "ExpMax" -> Math.max(1, PlayerStats.get(p, "ExpMax"));
            case "barcolor" -> Math.max(1, PlayerStats.get(p, "barcolor"));
            case "level", "grade", "Exp", "finger", "fuga", "fever", "overheat", "absorption" -> PlayerStats.get(p, obj);
            default -> 0;
        };
    }
}
