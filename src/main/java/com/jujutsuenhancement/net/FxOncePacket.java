package com.jujutsuenhancement.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One effect the command engine ran on the server ({@code particle}, {@code playsound}, {@code camera}, {@code camerashake}, {@code title}...),
 * already resolved to absolute numbers. ops: P particle (s name; x y z yaw), S sound (s name; x y z volume pitch), K shake (amp ticks),
 * F fade (in hold out r g b), C camera shot (s ease; seconds x y z yaw pitch), X camera clear, I impact frame (codepoint, 0 = clear),
 * A action bar text (s), M chat text (s).
 */
public record FxOncePacket(String op, double[] d, String s) {
    public static void encode(FxOncePacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.op);
        buf.writeVarInt(p.d.length);
        for (double v : p.d) buf.writeDouble(v);
        buf.writeUtf(p.s, 32767);
    }

    public static FxOncePacket decode(FriendlyByteBuf buf) {
        String op = buf.readUtf();
        double[] d = new double[buf.readVarInt()];
        for (int i = 0; i < d.length; i++) d[i] = buf.readDouble();
        return new FxOncePacket(op, d, buf.readUtf(32767));
    }

    public static void handle(FxOncePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.jujutsuenhancement.client.bedrock.OnceFx.run(p.op, p.d, p.s)));
        ctx.get().setPacketHandled(true);
    }
}
