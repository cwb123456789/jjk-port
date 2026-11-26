package com.jujutsuenhancement.net;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Entity tags are not sent to clients: the glowing-part tags are mirrored to every client that sees the player. */
@Mod.EventBusSubscriber(modid = com.jujutsuenhancement.JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GlowFlagSync {
    private static final Map<Integer, Integer> LAST = new HashMap<>();

    private GlowFlagSync() {}

    public static int bits(ServerPlayer p) {
        return p.getTags().contains("sixeyes") ? 1 : 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        int b = bits(p);
        Integer old = LAST.get(p.getId());
        if (old == null ? b == 0 : old == b) return;
        LAST.put(p.getId(), b);
        ModNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new GlowFlagPacket(p.getId(), b));
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer viewer) {
            int b = bits(t);
            if (b != 0) ModNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new GlowFlagPacket(t.getId(), b));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) LAST.remove(p.getId());
    }
}
