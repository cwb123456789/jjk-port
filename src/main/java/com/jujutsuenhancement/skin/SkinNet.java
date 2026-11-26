package com.jujutsuenhancement.skin;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Packets of the add-on: open the skin screen on the client (S2C) and tell the server that it was closed (C2S). */
final class SkinNet {
    private static final String VERSION = "1";
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("jujutsuenhancement", "skin"),
            () -> VERSION, v -> true, v -> true);

    record Open(boolean replayAfter, int[] others) {}

    record Done() {}

    private SkinNet() {}

    static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, Open.class, (m, b) -> {
            b.writeBoolean(m.replayAfter());
            b.writeVarIntArray(m.others());
        }, b -> new Open(b.readBoolean(), b.readVarIntArray()), SkinNet::onOpen);
        CHANNEL.registerMessage(id++, Done.class, (m, b) -> {}, b -> new Done(), SkinNet::onDone);
    }

    static void open(ServerPlayer p, boolean replayAfter, int[] others) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Open(replayAfter, others));
    }

    private static void onOpen(Open m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkinClient.openScreen(m.replayAfter(), m.others())));
        ctx.get().setPacketHandled(true);
    }

    private static void onDone(Done m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) com.jujutsuenhancement.combat.CgSessions.resume(p);
        });
        ctx.get().setPacketHandled(true);
    }
}
