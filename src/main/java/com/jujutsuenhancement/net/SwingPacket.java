package com.jujutsuenhancement.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** The client pressed the attack key (the addon's "punch" detection) while holding a weapon of the addon. */
public record SwingPacket() {
    public static void encode(SwingPacket p, FriendlyByteBuf buf) {}

    public static SwingPacket decode(FriendlyByteBuf buf) {
        return new SwingPacket();
    }

    public static void handle(SwingPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) {
                com.jujutsuenhancement.combat.KatanaCombat.onLeftClick(player);
                com.jujutsuenhancement.combat.YujiSkills.onLeftClick(player);
                com.jujutsuenhancement.combat.SukunaSkills.onLeftClick(player);
                com.jujutsuenhancement.combat.SukunaFuga.onLeftClick(player);
                com.jujutsuenhancement.combat.SukunaFists.onLeftClick(player);
                com.jujutsuenhancement.combat.SukunaCleave.onLeftClick(player);
                com.jujutsuenhancement.combat.SukunaFour.onLeftClick(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
