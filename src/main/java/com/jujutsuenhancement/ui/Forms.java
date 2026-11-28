package com.jujutsuenhancement.ui;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.bfn.BfnRuntime;
import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.net.OpenFormPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The addon's forms: the stats book (scripts/statsbook.js), the settings item (index.js, "ds:settings") and the cursed wheel
 * (cursed_wheel.js, "ds:cursedenergy"). Using the item opens the form on the client; the choice comes back and runs the same
 * Bedrock functions the scripts run (point_*, setting_*) or sets the wheel's scores.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Forms {
    private static final String[] STAT_OBJ = {"endurance", "strength", "agility", "energystat"};
    private static final String[] STAT_FN = {"point_endurance", "point_strength", "point_agility", "point_energy"};
    private static final String[] SETTING_FN = {null, "setting_impactframes", "setting_griefing", "setting_destruction_level", "setting_mobevents",
            "setting_nocds", "setting_menu", "setting_nomissioncds", "setting_only_one_sukuna", "setting_exp", "setting_gui",
            "setting_mahito_insta_kill", "setting_endurance_cap"};
    /** cescroll value of each wheel slot (cursed_wheel.js SEGMENTS) */
    private static final int[] WHEEL = {1, 2, 5, 4, 6, 7, 3, 0};

    private Forms() {}

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        var key = ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem());
        if (key == null || !key.getNamespace().equals(JujutsuEnhancement.MODID)) return;
        switch (key.getPath()) {
            case "stats_book" -> openStats(p);
            case "settings" -> openSettings(p);
            case "cursedenergy" -> openWheel(p);
            default -> { }
        }
    }

    private static boolean needEngine(ServerPlayer p) {
        if (BfnRuntime.isEnabled()) return false;
        p.sendSystemMessage(Component.literal("§7[咒术觉醒] 这个界面由基岩命令引擎驱动：先执行 /jje bfn on"));
        return true;
    }

    private static int cap(ServerPlayer p, String obj) {
        boolean is120 = p.getTags().contains("120percent");
        boolean mech = p.getTags().contains("heavenlyrestriction_mechimaru");
        return switch (obj) {
            case "endurance" -> mech ? (is120 ? 24 : 20) : (is120 ? 120 : 100);
            case "strength", "agility" -> mech ? (is120 ? 24 : 20) : (is120 ? 60 : 50);
            case "energystat" -> p.getTags().contains("heavenlyrestriction") ? 0 : -1;
            default -> -1;
        };
    }

    public static void openStats(ServerPlayer p) {
        if (needEngine(p)) return;
        int[] n = new int[2 + 4 + 4];
        n[0] = BfnRuntime.score(p, "invest");
        n[1] = BfnRuntime.score(p, "grade");
        for (int i = 0; i < 4; i++) {
            n[2 + i] = BfnRuntime.score(p, STAT_OBJ[i]);
            n[6 + i] = cap(p, STAT_OBJ[i]);
        }
        send(p, 0, n);
    }

    public static void openSettings(ServerPlayer p) {
        if (needEngine(p)) return;
        Integer d = BfnRuntime.fakeScore("Mode", "destructionlevel");
        int pct = d == null || d < 1 || d > 4 ? 50 : new int[] {25, 50, 75, 100}[d - 1];
        send(p, 1, new int[] {pct});
    }

    public static void openWheel(ServerPlayer p) {
        if (needEngine(p)) return;
        p.addTag("ce_wheel_open");
        if (BfnRuntime.score(p, "cescroll") != 0) BfnRuntime.setScore(p, "cescroll", 0);
        p.removeTag("itemUse:ds:cursedenergy");
        send(p, 2, new int[] {p.getTags().contains("curse") ? 1 : 0});
    }

    private static void send(ServerPlayer p, int kind, int[] nums) {
        ModNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OpenFormPacket(kind, nums));
    }

    /** the choice of a form (a -1: closed / cancelled) */
    public static void result(ServerPlayer p, int kind, int a, int b) {
        if (!BfnRuntime.isEnabled()) return;
        switch (kind) {
            case 0 -> {
                if (a < 0 || a >= 4 || b <= 0) return;
                // statsbook.js: the slider is bounded by invest and by the cap
                int invest = BfnRuntime.score(p, "invest");
                int cap = cap(p, STAT_OBJ[a]);
                int room = cap < 0 ? invest : Math.max(0, cap - BfnRuntime.score(p, STAT_OBJ[a]));
                int max = Math.min(invest, room);
                for (int i = 0; i < Math.min(b, max); i++) BfnRuntime.runCommandAs(p, "function " + STAT_FN[a]);
                openStats(p);
            }
            case 1 -> {
                if (a < 0 || a >= SETTING_FN.length) return;
                if (a == 0) BfnRuntime.runCommandAs(p, "clear @s ds:settings");
                else BfnRuntime.runCommandAs(p, "function " + SETTING_FN[a]);
            }
            case 2 -> {
                p.removeTag("ce_wheel_open");
                p.removeTag("itemUse:ds:cursedenergy");
                if (BfnRuntime.score(p, "cescroll") != 0) BfnRuntime.setScore(p, "cescroll", 0);
                if (a < 0 || a >= WHEEL.length) return;
                BfnRuntime.setScore(p, "cescroll", WHEEL[a]);
                p.addTag("itemUse:ds:cursedenergy");
                BfnRuntime.afterTicks(1, () -> {
                    p.removeTag("itemUse:ds:cursedenergy");
                    BfnRuntime.setScore(p, "cescroll", 0);
                });
            }
            default -> { }
        }
    }
}
