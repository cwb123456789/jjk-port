package com.jujutsuenhancement.client;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.net.InputPacket;
import com.jujutsuenhancement.net.ModNet;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tells the server when the jump key goes down / up. */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class InputSender {
    private static boolean last;

    private InputSender() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            last = false;
            return;
        }
        boolean now = mc.screen == null && mc.options.keyJump.isDown();
        if (now != last) {
            last = now;
            ModNet.CHANNEL.sendToServer(new InputPacket(now));
        }
    }
}
