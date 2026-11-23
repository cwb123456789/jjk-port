package com.jujutsuenhancement.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** A button of the quick-command menu was pressed (category and index of {@link com.jujutsuenhancement.quickmenu.QuickMenu}). */
public record QuickCommandPacket(int category, int index) {
    public static void encode(QuickCommandPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.category);
        buf.writeVarInt(p.index);
    }

    public static QuickCommandPacket decode(FriendlyByteBuf buf) {
        return new QuickCommandPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(QuickCommandPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null && player.getMainHandItem().getItem() == com.jujutsuenhancement.registry.ModItems.QUICK_COMMAND.get())
                com.jujutsuenhancement.quickmenu.QuickCommands.run(player, p.category, p.index);
        });
        ctx.get().setPacketHandled(true);
    }
}
