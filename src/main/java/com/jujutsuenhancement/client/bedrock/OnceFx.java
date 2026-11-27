package com.jujutsuenhancement.client.bedrock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The client side of {@link com.jujutsuenhancement.net.FxOncePacket}. */
public final class OnceFx {
    private OnceFx() {}

    public static void run(String op, double[] d, String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        switch (op) {
            case "P" -> FxShow.particleAt(s, d[0], d[1], d[2], d[3]);
            case "S" -> {
                double vol = d[3];
                boolean global = vol >= 100.0D;
                mc.getSoundManager().play(new SimpleSoundInstance(new ResourceLocation(s), SoundSource.HOSTILE, global ? 1.0F : (float) vol, (float) d[4],
                        RandomSource.create(), false, 0, global ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR, d[0], d[1], d[2], global));
            }
            case "PB" -> {
                for (int i = 0; i + 2 < d.length; i += 3) FxShow.particleAt(s, d[i], d[i + 1], d[i + 2], 0.0F);
            }
            case "L" -> FxShow.lineAt(s, d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d[9], d[10]);
            case "K" -> FxShow.shakeFor(d[0], (int) d[1]);
            case "F" -> MoveFx.fade(d[0], d[1], d[2], (int) d[3], (int) d[4], (int) d[5]);
            case "C" -> CgCamera.setAbs(d[0], s, d[1], d[2], d[3], d[4], d[5]);
            case "X" -> CgCamera.clear();
            case "I" -> {
                if ((int) d[0] == 0) ImpactFrames.clear();
                else ImpactFrames.show((int) d[0]);
            }
            case "A" -> mc.gui.setOverlayMessage(Component.literal(s), false);
            case "M" -> mc.player.sendSystemMessage(Component.literal(s));
            default -> { }
        }
    }
}
