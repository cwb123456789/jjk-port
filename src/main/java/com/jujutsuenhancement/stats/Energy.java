package com.jujutsuenhancement.stats;

import com.jujutsuenhancement.JujutsuEnhancement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 咒力 (the scoreboards Energy and EnergyCap of the Bedrock addon).
 * <ul>
 *   <li>A new player gets EnergyCap 100 ({@code add_scores.mcfunction}: "players set @a[scores={start=0}] EnergyCap 100");
 *       the Gojo clan (tag {@code gojo}) adds 100 ({@code menu_finalscreen.mcfunction}).</li>
 *   <li>Every second a player regains {@code ceil(EnergyCap / 90)} ({@code scripts/index.js}, handleTimerCycle), unless it
 *       has the tag {@code heavenlyrestriction} or {@code domainclashing}, is in the middle of a move (move &gt;= 1)
 *       or has a domain open.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Energy {
    private static final int REGEN_INTERVAL = 20;
    private static final int FULL_REGEN_SECONDS = 90;

    private Energy() {}

    /** The cap: the stat plus the Gojo clan's bonus; 0 for the heavenly restriction (no cursed energy). */
    public static int cap(Player p) {
        if (p.getTags().contains("heavenlyrestriction")) return 0;
        int base = PlayerStats.get(p, "start") == 0 ? 100 : PlayerStats.get(p, "EnergyCap");
        return base + (p.getTags().contains("gojo") ? 100 : 0);
    }

    public static int get(Player p) {
        return PlayerStats.get(p, "Energy");
    }

    public static void set(Player p, int value) {
        PlayerStats.set(p, "Energy", Math.max(0, Math.min(value, Math.max(cap(p), 0))));
    }

    public static void setBase(Player p, int cap) {
        PlayerStats.set(p, "start", 1);
        PlayerStats.set(p, "EnergyCap", cap);
    }

    /** Whether the player is in the middle of a technique ({@code move >= 1}); set by the technique code. */
    public interface Busy {
        boolean isBusy(ServerPlayer p);
    }

    private static Busy busy = p -> false;

    public static void setBusy(Busy b) {
        busy = b;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (PlayerStats.get(p, "start") == 0) {
            PlayerStats.set(p, "start", 1);
            PlayerStats.set(p, "EnergyCap", 100);
        }
        if (p.tickCount % REGEN_INTERVAL != 0) return;
        int cap = cap(p);
        if (cap <= 0 || p.getTags().contains("domainclashing") || busy.isBusy(p)) return;
        int energy = get(p);
        if (energy > cap) PlayerStats.set(p, "Energy", cap);
        else if (energy < cap) set(p, energy + (int) Math.ceil(cap / (double) FULL_REGEN_SECONDS));
    }
}
