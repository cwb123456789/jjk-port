package com.jujutsuenhancement.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** The jump key of the client (the server never learns it otherwise): the add-on's detect_jump controller reads query.is_jumping. */
public record InputPacket(boolean jump) {
    public static void encode(InputPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.jump);
    }

    public static InputPacket decode(FriendlyByteBuf buf) {
        return new InputPacket(buf.readBoolean());
    }

    public static void handle(InputPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) com.jujutsuenhancement.bfn.PlayerInput.setJump(player.getUUID(), p.jump);
        });
        ctx.get().setPacketHandled(true);
    }
}
