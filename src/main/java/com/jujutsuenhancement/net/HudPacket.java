package com.jujutsuenhancement.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * The state of the addon's heads-up display (scripts/index.js bardisplay / olduidisplay): the title with the "a:" tokens that
 * the resource pack's flames_bar.json matches, and the numbers of the action bar text.
 * nums: energy, cap, health, healthMax, absorption, flags, exp, expMax, level, evade, overheat, fever.
 */
public record HudPacket(String title, int[] nums) {
    public static void encode(HudPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.title, 512);
        buf.writeVarInt(p.nums.length);
        for (int n : p.nums) buf.writeInt(n);
    }

    public static HudPacket decode(FriendlyByteBuf buf) {
        String t = buf.readUtf(512);
        int[] n = new int[buf.readVarInt()];
        for (int i = 0; i < n.length; i++) n[i] = buf.readInt();
        return new HudPacket(t, n);
    }

    public static void handle(HudPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.jujutsuenhancement.client.ui.HudClient.receive(p.title, p.nums)));
        ctx.get().setPacketHandled(true);
    }
}
