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

    private ModNet() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, PlayAnimPacket.class, PlayAnimPacket::encode, PlayAnimPacket::decode, PlayAnimPacket::handle);
        CHANNEL.registerMessage(id++, SwingPacket.class, SwingPacket::encode, SwingPacket::decode, SwingPacket::handle);
    }

    /** playanimation: every player that can see {@code player}, and the player, plays {@code anim}. */
    public static void playAnim(ServerPlayer player, String anim, int idleBlock) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PlayAnimPacket(player.getId(), anim, idleBlock));
    }
}
