package com.jujutsuenhancement.stats;

import com.jujutsuenhancement.JujutsuEnhancement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps the stats of a player alive across death and applies them. */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StatEvents {
    private StatEvents() {}

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        PlayerStats.copy(event.getOriginal(), event.getEntity());
        PlayerStats.applyDamageAdd(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerStats.applyDamageAdd(event.getEntity());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 40 == 0) PlayerStats.applyDamageAdd(player);
    }
}
