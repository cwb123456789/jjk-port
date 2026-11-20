package com.jujutsuenhancement.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** A player's Unlimited Void state for the clients: -1 none, 0 full cast, 1 quick cast (same values as GojoAwakenedEntity's DOMAIN_FX). */
public record DomainFxPacket(int entityId, int fx) {
    public static void encode(DomainFxPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeVarInt(p.fx + 2);
    }

    public static DomainFxPacket decode(FriendlyByteBuf buf) {
        return new DomainFxPacket(buf.readVarInt(), buf.readVarInt() - 2);
    }

    public static void handle(DomainFxPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (p.fx < 0) com.jujutsuenhancement.client.bedrock.VoidShow.PLAYER_FX.remove(p.entityId);
            else com.jujutsuenhancement.client.bedrock.VoidShow.PLAYER_FX.put(p.entityId, p.fx);
        }));
        ctx.get().setPacketHandled(true);
    }
}
