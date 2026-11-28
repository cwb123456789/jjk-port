package com.jujutsuenhancement.client.ui;

import com.jujutsuenhancement.JujutsuEnhancement;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The addon's HUD (resource pack ui/hud_screen.json, ui/fuga/flames_bar.json). The server sends the title
 * {@code [a:grade3 a:exp_bar5 a:1ce_bar12 a:health_bar24 a:evasive_bar24]}; each bar is an image whose frame is chosen by the
 * pack's "variables" list: every entry whose token is a substring of the title applies, in order, so the last one wins
 * ({@code a:1ce_bar1} is also a substring of {@code a:1ce_bar12}). Sizes and offsets are the pack's (offset from the centre of a
 * panel that is centred on x = W/2 + 75, y = H - 6); the textures are two times the size they are drawn at.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HudOverlay {
    public static final IGuiOverlay OVERLAY = HudOverlay::render;
    public static final IGuiOverlay HOTBAR = HudOverlay::hotbar;

    /** tex, texW, texH, rowH, regionW, destW, destH, offX, offY, prefix, first frame token index (row = n - firstN) */
    private record Bar(String tex, int texW, int texH, int rowH, int regW, double w, double h, double ox, double oy, String prefix, int minN, int maxN, String extra) {}

    private static final String H = "textures/ui/hud/";
    private static final List<Bar> BARS = List.of(
            new Bar("fugabar", 230, 850, 34, 230, 115, 17, -261, -31, "a:flames_bar", 0, 25, null),
            new Bar("feverbar", 230, 475, 19, 230, 115, 9.5, -266, -17, "a:fever", 0, 25, null),
            new Bar("heatbar", 211, 750, 30, 211, 105.5, 15, -269, -17, "a:heat", 0, 25, null),
            new Bar("healthbar", 389, 500, 20, 389, 194.5, 10, -75, -22, "a:health_bar", 0, 25, null),
            new Bar("cebar", 373, 450, 18, 373, 186.5, 9, -75, -31.5, "a:1ce_bar", 0, 25, null),
            new Bar("cebar_sukuna", 373, 450, 18, 373, 186.5, 9, -75, -31.5, "a:3ce_bar", 0, 25, null),
            new Bar("cebar_yuta", 373, 450, 18, 373, 186.5, 9, -75, -31.5, "a:4ce_bar", 0, 25, null),
            // the pack's first entry for this bar names 1ce_bar0 (a typo that is part of the real thing)
            new Bar("cebar_hakari", 373, 450, 18, 373, 186.5, 9, -75, -31.5, "a:2ce_bar", 1, 25, "a:1ce_bar0"),
            new Bar("cebar_hr", 373, 18, 18, 373, 186.5, 9, -75, -31.5, "a:hrce_bar", -1, -1, null),
            new Bar("expbar", 153, 250, 10, 153, 76.5, 5, 51.5, -1.5, "a:exp_bar", 0, 25, null),
            new Bar("gradebar", 153, 60, 12, 153, 76.5, 6, 51.5, -7.5, "a:grade", 1, 5, null),
            new Bar("cebar_curse", 373, 450, 18, 373, 186.5, 9, -75, -31.5, "a:5ce_bar", 0, 25, null),
            new Bar("evasive", 187, 575, 23, 187, 93.5, 11.5, -75, -41, "a:evasive_bar", 0, 25, null));

    private HudOverlay() {}

    private static boolean uiOn() {
        Minecraft mc = Minecraft.getInstance();
        return HudClient.fresh() && !mc.options.hideGui && mc.player != null;
    }

    @SubscribeEvent
    public static void onPre(RenderGuiOverlayEvent.Pre e) {
        if (!HudClient.fresh()) return;
        var id = e.getOverlay();
        // hud hide health / horse_health / armor / status_effects / progress_bar
        if (id == VanillaGuiOverlay.PLAYER_HEALTH.type() || id == VanillaGuiOverlay.MOUNT_HEALTH.type() || id == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || id == VanillaGuiOverlay.POTION_ICONS.type() || id == VanillaGuiOverlay.EXPERIENCE_BAR.type()) e.setCanceled(true);
        // the pack's own hotbar replaces the vanilla one
        if (id == VanillaGuiOverlay.HOTBAR.type()) e.setCanceled(true);
        if (HudClient.hotbarHidden() && (id == VanillaGuiOverlay.FOOD_LEVEL.type() || id == VanillaGuiOverlay.AIR_LEVEL.type()
                || id == VanillaGuiOverlay.ITEM_NAME.type() || id == VanillaGuiOverlay.CROSSHAIR.type() && (HudClient.flags() & 32) != 0)) e.setCanceled(true);
    }

    private static int frame(String prefix, int minN, int maxN, String extra) {
        String t = HudClient.title;
        int found = Integer.MIN_VALUE;
        if (extra != null && t.contains(extra)) found = 0;
        if (minN < 0) return t.contains(prefix) ? 0 : Integer.MIN_VALUE;
        for (int n = minN; n <= maxN; n++) if (t.contains(prefix + n)) found = n;
        return found;
    }

    private static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        if (!uiOn() || HudClient.title.isEmpty()) return;
        if (HudClient.hotbarHidden() && (HudClient.flags() & 32) != 0) return;
        double cx = w / 2.0 + 75, cy = h - 6;
        for (Bar b : BARS) {
            int n = frame(b.prefix, b.minN, b.maxN, b.extra);
            if (n == Integer.MIN_VALUE) continue;
            int row = b.prefix.equals("a:grade") ? n - 1 : Math.max(n, 0);
            int v = row * b.rowH;
            if (v + b.rowH > b.texH) continue;
            double sx = b.w / b.regW, sy = b.h / b.rowH;
            double x = cx + b.ox - b.w / 2, y = cy + b.oy - b.h / 2;
            var ps = g.pose();
            ps.pushPose();
            ps.translate(x, y, 0);
            ps.scale((float) sx, (float) sy, 1f);
            g.blit(new ResourceLocation(JujutsuEnhancement.MODID, H + b.tex + ".png"), 0, 0, 0, v, b.regW, b.rowH, b.texW, b.texH);
            ps.popPose();
        }
        if ((HudClient.flags() & 64) != 0) return;
        if (!HudClient.title.contains("a:health_bar")) return;
        numbers(g, w, h);
    }

    private static void centered(GuiGraphics g, Component text, double cx, double cy, float scale) {
        Font f = Minecraft.getInstance().font;
        int tw = f.width(text);
        var ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0);
        ps.scale(scale, scale, 1f);
        g.drawString(f, text, -tw / 2, -4, 0xFFFFFF, true);
        ps.popPose();
    }

    /** the action bar of bardisplay(): 14 blank lines, "Energy/EnergyCap", a blank line, "health/health_max"; the text is the pack's small font */
    private static void numbers(GuiGraphics g, int w, int h) {
        int[] n = HudClient.nums;
        int flags = n[5];
        float s = 0.55f;
        double cx = w / 2.0;
        boolean hr = (flags & 1) != 0;
        if (!hr) centered(g, Component.literal("§f" + n[0] + "/" + n[1]), cx, h - 37.5, s);
        centered(g, Component.literal("§f" + n[2] + "/" + n[3]), cx, h - 28, s);
        if ((flags & 2) != 0 && !hr)
            centered(g, Component.translatable("text.stored_energy_2.ui").append(Component.literal(n[4] + "%")), cx, h - 37.5 - 4 * 9 * s, s);
    }

    private static void hotbar(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!uiOn() || HudClient.hotbarHidden()) return;
        if (!(mc.getCameraEntity() instanceof Player p) || mc.gameMode == null || mc.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.SPECTATOR) return;
        int x0 = w / 2 - 91, y0 = h - 22;
        for (int i = 0; i < 9; i++) {
            slot(g, "textures/ui/hotbar_" + i + ".png", x0 + 1 + i * 20, y0);
        }
        // selected slot highlight
        slot(g, "textures/ui/selected_hotbar_slot.png", x0 + 1 + p.getInventory().selected * 20, y0);
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().items.get(i);
            if (st.isEmpty()) continue;
            int ix = x0 + 1 + i * 20 + 3, iy = y0 + 3;
            g.renderItem(st, ix, iy, i + 1);
            g.renderItemDecorations(mc.font, st, ix, iy);
        }
    }

    private static void slot(GuiGraphics g, String tex, int x, int y) {
        var ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale(20f / 41f, 22f / 43f, 1f);
        g.blit(new ResourceLocation(JujutsuEnhancement.MODID, tex), 0, 0, 0, 0, 41, 43, 41, 43);
        ps.popPose();
    }
}
