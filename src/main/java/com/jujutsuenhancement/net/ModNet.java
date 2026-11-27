package com.jujutsuenhancement.net;

import com.jujutsuenhancement.JujutsuEnhancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNet {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(JujutsuEnhancement.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private static final java.util.Map<java.util.UUID, Object[]> LAST_ANIM = new java.util.concurrent.ConcurrentHashMap<>();

    private ModNet() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, PlayAnimPacket.class, PlayAnimPacket::encode, PlayAnimPacket::decode, PlayAnimPacket::handle);
        CHANNEL.registerMessage(id++, DomainFxPacket.class, DomainFxPacket::encode, DomainFxPacket::decode, DomainFxPacket::handle);
        CHANNEL.registerMessage(id++, MoveFxPacket.class, MoveFxPacket::encode, MoveFxPacket::decode, MoveFxPacket::handle);
        CHANNEL.registerMessage(id++, QuickCommandPacket.class, QuickCommandPacket::encode, QuickCommandPacket::decode, QuickCommandPacket::handle);
        CHANNEL.registerMessage(id++, GlowFlagPacket.class, GlowFlagPacket::encode, GlowFlagPacket::decode, GlowFlagPacket::handle);
        CHANNEL.registerMessage(id++, SwingPacket.class, SwingPacket::encode, SwingPacket::decode, SwingPacket::handle);
        CHANNEL.registerMessage(id++, FxOncePacket.class, FxOncePacket::encode, FxOncePacket::decode, FxOncePacket::handle);
    }

    /** playanimation: every player that can see {@code player}, and the player, plays {@code anim}. */
    public static void playAnim(ServerPlayer player, String anim, int idleBlock) {
        if (!anim.isEmpty()) {
            long now = player.level().getGameTime();
            var cg = com.jujutsuenhancement.combat.CgSessions.current(player);
            if (cg != null && cg.anim == null && now - cg.startTick <= 10) {
                cg.anim = anim;
                cg.idle = idleBlock;
            } else {
                LAST_ANIM.put(player.getUUID(), new Object[] {anim, idleBlock, now});
            }
        }
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PlayAnimPacket(player.getId(), anim, idleBlock));
    }

    /** The domain state of a casting player, for every player that can see it. */
    public static void domainFx(ServerPlayer player, int fx) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new DomainFxPacket(player.getId(), fx));
    }

    /** The effect timeline of a limitless move (moves.json) for everybody who sees the player. */
    public static void moveFx(ServerPlayer player, String key) {
        int cgTicks = key.equals("cg:stop") ? 0 : CgIndex.ticks(key);
        if (cgTicks > 0) {
            var s = com.jujutsuenhancement.combat.CgSessions.begin(player, key, cgTicks, null, null);
            Object[] last = LAST_ANIM.get(player.getUUID());
            if (last != null && player.level().getGameTime() - (Long) last[2] <= 10) {
                s.anim = (String) last[0];
                s.idle = (Integer) last[1];
            }
        }
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new MoveFxPacket(player.getId(), key));
    }

    /** 咒术替换: stops the client side of the running CG (camera, animation). */
    public static void stopCg(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new MoveFxPacket(player.getId(), "cg:stop"));
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new PlayAnimPacket(player.getId(), "", 0));
    }

    /** Plays the CG that {@code s} describes again from its first tick (animation and effect timeline). */
    public static void replayCg(ServerPlayer player, com.jujutsuenhancement.combat.CgSessions.Session s) {
        if (s.anim != null) CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new PlayAnimPacket(player.getId(), s.anim, s.idle));
        moveFx(player, s.id);
        var cur = com.jujutsuenhancement.combat.CgSessions.current(player);
        if (cur != null) cur.anim = s.anim;
        if (cur != null) cur.idle = s.idle;
    }

    /** camerashake add: the client of {@code player} shakes its camera. */
    public static void shake(ServerPlayer player, double amp, int ticks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MoveFxPacket(player.getId(), "shake:" + amp + ":" + ticks));
    }
}
