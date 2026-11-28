package com.jujutsuenhancement.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** What the player chose in a form: kind 0 stats (a = stat, b = points; a -1 = closed), 1 settings (a = button), 2 wheel (a = slot, -1 = cancelled). */
public record FormResultPacket(int kind, int a, int b) {
    public static void encode(FormResultPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.kind);
        buf.writeInt(p.a);
        buf.writeInt(p.b);
    }

    public static FormResultPacket decode(FriendlyByteBuf buf) {
        return new FormResultPacket(buf.readVarInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(FormResultPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sp = ctx.get().getSender();
            if (sp != null) com.jujutsuenhancement.ui.Forms.result(sp, p.kind, p.a, p.b);
        });
        ctx.get().setPacketHandled(true);
    }
}
