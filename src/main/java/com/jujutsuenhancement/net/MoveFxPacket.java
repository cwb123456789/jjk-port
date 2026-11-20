package com.jujutsuenhancement.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** A limitless move began on an entity: the clients play its effect timeline (bedrock/gojo/moves.json). */
public record MoveFxPacket(int entityId, String key) {
    public static void encode(MoveFxPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeUtf(p.key);
    }

    public static MoveFxPacket decode(FriendlyByteBuf buf) {
        return new MoveFxPacket(buf.readVarInt(), buf.readUtf());
    }

    public static void handle(MoveFxPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.jujutsuenhancement.client.bedrock.MoveFx.start(p.entityId, p.key)));
        ctx.get().setPacketHandled(true);
    }
}
