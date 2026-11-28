package com.jujutsuenhancement.client.ui;

import net.minecraft.client.Minecraft;

/** The client's copy of the addon's HUD state (see {@link com.jujutsuenhancement.ui.HudSync}). */
public final class HudClient {
    public static String title = "";
    public static int[] nums = new int[12];
    private static long received = Long.MIN_VALUE / 2;

    private HudClient() {}

    public static void receive(String t, int[] n) {
        title = t;
        nums = n.length >= 12 ? n : new int[12];
        Minecraft mc = Minecraft.getInstance();
        received = mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** the server has sent the state recently (a title stays 20 ticks in the addon; a missed beat is tolerated) */
    public static boolean fresh() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() - received < 40;
    }

    public static int flags() {
        return fresh() ? nums[5] : 0;
    }

    public static boolean hotbarHidden() {
        return (flags() & 16) != 0;
    }
}
