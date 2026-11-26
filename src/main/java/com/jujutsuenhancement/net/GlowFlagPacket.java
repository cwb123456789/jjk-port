package com.jujutsuenhancement.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The glowing parts a player has (bit 1 = 六眼 eyes, tag "sixeyes"), for every client that sees the player. */
public record GlowFlagPacket(int entityId, int bits) {
    public static void encode(GlowFlagPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeVarInt(p.bits);
    }

    public static GlowFlagPacket decode(FriendlyByteBuf buf) {
        return new GlowFlagPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(GlowFlagPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.jujutsuenhancement.client.render.SixEyes.set(p.entityId, p.bits)));
        ctx.get().setPacketHandled(true);
    }
}
