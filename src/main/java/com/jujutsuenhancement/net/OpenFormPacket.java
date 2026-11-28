package com.jujutsuenhancement.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The server shows the player a form of the addon (scripts/statsbook.js, cursed_wheel.js, index.js settings): kind 0 stats, 1 settings, 2 wheel. */
public record OpenFormPacket(int kind, int[] nums) {
    public static void encode(OpenFormPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.kind);
        buf.writeVarInt(p.nums.length);
        for (int n : p.nums) buf.writeInt(n);
    }

    public static OpenFormPacket decode(FriendlyByteBuf buf) {
        int k = buf.readVarInt();
        int[] n = new int[buf.readVarInt()];
        for (int i = 0; i < n.length; i++) n[i] = buf.readInt();
        return new OpenFormPacket(k, n);
    }

    public static void handle(OpenFormPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.jujutsuenhancement.client.ui.FormScreens.open(p.kind, p.nums)));
        ctx.get().setPacketHandled(true);
    }
}
