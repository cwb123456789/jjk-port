package com.jujutsuenhancement.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The server tells the clients that a player plays a Bedrock animation (the addon's playanimation). */
public record PlayAnimPacket(int entityId, String anim, int idleBlock) {
    public static void encode(PlayAnimPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeUtf(p.anim);
        buf.writeVarInt(p.idleBlock);
    }

    public static PlayAnimPacket decode(FriendlyByteBuf buf) {
        return new PlayAnimPacket(buf.readVarInt(), buf.readUtf(), buf.readVarInt());
    }

    public static void handle(PlayAnimPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.jujutsuenhancement.client.anim.PlayerAnims.start(p.entityId, p.anim, p.idleBlock)));
        ctx.get().setPacketHandled(true);
    }
}
