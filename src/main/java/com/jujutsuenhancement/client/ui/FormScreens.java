package com.jujutsuenhancement.client.ui;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.net.FormResultPacket;
import com.jujutsuenhancement.net.ModNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The addon's forms drawn from the resource pack's ui/server_form.json and ui/heroic/stats.json (textures heroic/*, wheel/*). */
public final class FormScreens {
    private FormScreens() {}

    private static ResourceLocation tex(String p) {
        return new ResourceLocation(JujutsuEnhancement.MODID, p);
    }

    public static void open(int kind, int[] n) {
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case 0 -> mc.setScreen(new StatsScreen(n));
            case 1 -> mc.setScreen(new SettingsScreen(n));
            case 2 -> mc.setScreen(new WheelScreen(n));
            default -> { }
        }
    }

    /** a nine-slice drawn with its corners at the texture's own size, like the pack's nineslice_size */
    static void nine(GuiGraphics g, ResourceLocation t, int x, int y, int w, int h, int c, int ts) {
        int mid = ts - 2 * c;
        int mw = w - 2 * c, mh = h - 2 * c;
        g.blit(t, x, y, c, c, 0, 0, c, c, ts, ts);
        g.blit(t, x + w - c, y, c, c, ts - c, 0, c, c, ts, ts);
        g.blit(t, x, y + h - c, c, c, 0, ts - c, c, c, ts, ts);
        g.blit(t, x + w - c, y + h - c, c, c, ts - c, ts - c, c, c, ts, ts);
        g.blit(t, x + c, y, mw, c, c, 0, mid, c, ts, ts);
        g.blit(t, x + c, y + h - c, mw, c, c, ts - c, mid, c, ts, ts);
        g.blit(t, x, y + c, c, mh, 0, c, c, mid, ts, ts);
        g.blit(t, x + w - c, y + c, c, mh, ts - c, c, c, mid, ts, ts);
        g.blit(t, x + c, y + c, mw, mh, c, c, mid, mid, ts, ts);
    }

    static void scaled(GuiGraphics g, ResourceLocation t, double x, double y, double w, double h, int tw, int th) {
        var ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale((float) (w / tw), (float) (h / th), 1f);
        g.blit(t, 0, 0, 0, 0, tw, th, tw, th);
        ps.popPose();
    }

    // ---------------------------------------------------------------------------------------------------------------- stats

    /** heroic/stats.json: a 250x90 panel with the title hanging off its top, a hint bar, a close button and four 60x60 buttons */
    static final class StatsScreen extends Screen {
        private final int[] n;   // invest, grade, 4 stats, 4 caps (-1 = none)
        private static final String[] ICON = {"endurance", "strength", "speed", "cursed"};
        private static final String[] TITLE = {"text.upgrade_endurance2.ui", "text.upgrade_strength.ui", "text.upgrade_agility.ui", "text.upgrade_energy.ui"};
        private static final String[] LABEL = {"text.endurance_label.statsbook", "text.strength_label.statsbook", "text.agility_label.statsbook", "text.energy_label.statsbook"};
        private int px, py;

        StatsScreen(int[] n) {
            super(Component.literal("stats"));
            this.n = n;
        }

        @Override
        protected void init() {
            px = width / 2 - 125;
            py = height / 2 - 45;
        }

        private int bx(int i) {
            return px + 5 + i * 60;
        }

        private int by() {
            return height / 2 - 20;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g);
            nine(g, tex("textures/ui/heroic/background.png"), px, py, 250, 90, 3, 32);
            // hanging title
            Component title = Component.literal("Stats");
            int tw = (int) (font.width(title) * 1.2f);
            nine(g, tex("textures/ui/heroic/background.png"), width / 2 - (tw + 10) / 2, py - 9, tw + 10, 18, 3, 32);
            var ps = g.pose();
            ps.pushPose();
            ps.translate(width / 2.0, py - 4, 0);
            ps.scale(1.2f, 1.2f, 1f);
            g.drawString(font, title, -font.width(title) / 2, -4, 0xFFFFFF, true);
            ps.popPose();
            // hint
            g.fill(width / 2 - 110, py + 10, width / 2 + 110, py + 20, 0x4D000000);
            ps.pushPose();
            ps.translate(width / 2.0, py + 15, 0);
            ps.scale(0.8f, 0.8f, 1f);
            Component hint = Component.translatable("text.upgrade.ui");
            g.drawString(font, hint, -font.width(hint) / 2, -4, 0xFFFFFF, true);
            ps.popPose();
            // close
            boolean overClose = mx >= px + 250 - 12 + 4 && mx < px + 250 + 4 && my >= py - 4 && my < py + 8;
            scaled(g, tex("textures/ui/heroic/" + (overClose ? "close_hover" : "close") + ".png"), px + 250 - 8, py - 4, 12, 12, 16, 16);
            Component hover = null;
            for (int i = 0; i < 4; i++) {
                int x = bx(i), y = by();
                boolean over = mx >= x && mx < x + 60 && my >= y && my < y + 60;
                nine(g, tex("textures/ui/heroic/" + (over ? "button_hover" : "button") + ".png"), x + 2, y + 2, 56, 56, 6, 16);
                scaled(g, tex("textures/ui/forms/" + ICON[i] + ".png"), x + 14, y + 8, 32, 32, ICON_W[i], ICON_H[i]);
                ps.pushPose();
                ps.translate(x + 30, y + 52, 0);
                ps.scale(0.8f, 0.8f, 1f);
                Component t = Component.translatable(TITLE[i]);
                g.drawString(font, t, -font.width(t) / 2, -8, 0xFFFFFF, true);
                ps.popPose();
                if (over) hover = Component.translatable(LABEL[i]);
            }
            if (hover != null) g.renderTooltip(font, hover, mx, my);
            super.render(g, mx, my, pt);
        }

        private static final int[] ICON_W = {512, 200, 512, 340};
        private static final int[] ICON_H = {512, 200, 512, 440};

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (mx >= px + 242 && mx < px + 254 && my >= py - 4 && my < py + 8) {
                onClose();
                return true;
            }
            for (int i = 0; i < 4; i++) {
                if (mx >= bx(i) && mx < bx(i) + 60 && my >= by() && my < by() + 60) {
                    pick(i);
                    return true;
                }
            }
            return super.mouseClicked(mx, my, b);
        }

        private void pick(int i) {
            int invest = n[0], cur = n[2 + i], cap = n[6 + i];
            if (invest <= 0) {
                Minecraft.getInstance().player.sendSystemMessage(Component.translatable("text.noinvest_available"));
                return;
            }
            int room = cap < 0 ? invest : Math.max(0, cap - cur);
            int max = Math.min(invest, room);
            if (max <= 0) {
                Minecraft.getInstance().player.sendSystemMessage(Component.translatable("text.atpeak1.statsbook").append(Component.translatable(LABEL[i])).append(Component.translatable("text.atpeak2.statsbook")));
                return;
            }
            Component label = Component.translatable("text.slider_invest.statsbook").append(Component.translatable(LABEL[i]))
                    .append(cap < 0 ? Component.translatable("text.slider_nocap.statsbook", String.valueOf(cur), String.valueOf(invest))
                            : Component.translatable("text.slider.statsbook", String.valueOf(cur), String.valueOf(cap), String.valueOf(invest)));
            Minecraft.getInstance().setScreen(new SliderScreen(this, i, max, Component.translatable(LABEL[i]), label));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    /** the stat's modal form: a slider 0..max and a Submit button (the form's closing returns to the stats menu) */
    static final class SliderScreen extends Screen {
        private final Screen back;
        private final int stat, max;
        private final Component label;
        private int value;

        SliderScreen(Screen back, int stat, int max, Component title, Component label) {
            super(title);
            this.back = back;
            this.stat = stat;
            this.max = max;
            this.label = label;
        }

        @Override
        protected void init() {
            int x = width / 2 - 100, y = height / 2;
            addRenderableWidget(new AbstractSliderButton(x, y, 200, 20, Component.literal("0"), 0) {
                @Override
                protected void updateMessage() {
                    setMessage(Component.literal(String.valueOf(value)));
                }

                @Override
                protected void applyValue() {
                    value = (int) Math.round(this.value * max);
                }
            });
            addRenderableWidget(Button.builder(Component.translatable("gui.submit"), b -> {
                ModNet.CHANNEL.sendToServer(new FormResultPacket(0, stat, value));
                onClose();
            }).bounds(x, y + 30, 200, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g);
            g.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF);
            g.drawWordWrap(font, label, width / 2 - 100, height / 2 - 30, 200, 0xFFFFFF);
            super.render(g, mx, my, pt);
        }

        @Override
        public void onClose() {
            Minecraft.getInstance().setScreen(null);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- settings

    /** index.js "ds:settings": 13 buttons in a scrolling long form */
    static final class SettingsScreen extends Screen {
        private final int pct;
        private double scroll;

        SettingsScreen(int[] n) {
            super(Component.translatable("text.setting.ui"));
            this.pct = n.length > 0 ? n[0] : 50;
        }

        private List<Component> labels() {
            String col = switch (pct) {
                case 25 -> "§a";
                case 50 -> "§e";
                case 75 -> "§6";
                case 100 -> "§c";
                default -> "§f";
            };
            List<Component> l = new ArrayList<>();
            for (String k : new String[] {"removeitem", "impactframes", "griefing"}) l.add(Component.translatable("text.setting_" + k + ".ui"));
            l.add(Component.literal(col).append(Component.translatable("text.setting_destruction.ui")).append(Component.literal(pct + "%")));
            for (String k : new String[] {"mobevents", "nocds", "menu", "nomissioncds", "only_one_sukuna", "exp", "gui", "mahito_insta_kill", "endurance_cap"})
                l.add(Component.translatable("text.setting_" + k + ".ui"));
            return l;
        }

        @Override
        protected void init() {
            int bw = 205, x = width / 2 - bw / 2;
            int top = Math.max(30, height / 2 - 100);
            int i = 0;
            for (Component c : labels()) {
                final int idx = i;
                addRenderableWidget(Button.builder(c, b -> {
                    ModNet.CHANNEL.sendToServer(new FormResultPacket(1, idx, 0));
                    onClose();
                }).bounds(x, top + 5 + i * 22, bw, 20).build());
                i++;
            }
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g);
            int bw = 225;
            int top = Math.max(30, height / 2 - 100);
            g.fill(width / 2 - bw / 2, top - 12, width / 2 + bw / 2, top + 5 + 13 * 22 + 4, 0xC0000000);
            g.drawCenteredString(font, title, width / 2, top - 8, 0xFFFFFF);
            super.render(g, mx, my, pt);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- wheel

    /** server_form.json wheel_panel: eight 55x55 icons on a circle (offsets 0/-65, 46/-46, 65/0 ...), the 28x28 cancel in the middle */
    static final class WheelScreen extends Screen {
        private static final String[] ICON = {"rct", "divfists", "domainamp", "blossom", "hwb", "brain", "simpledomain", "empty"};
        private static final int[][] OFF = {{0, -65}, {46, -46}, {65, 0}, {46, 46}, {0, 65}, {-46, 46}, {-65, 0}, {-46, -46}};
        private final boolean curse;

        WheelScreen(int[] n) {
            super(Component.literal("Cursed Wheel"));
            this.curse = n.length > 0 && n[0] == 1;
        }

        private String icon(int i, boolean sel) {
            String name = i == 0 && curse ? "cursed_healing" : ICON[i];
            return "textures/ui/wheel/icons/" + name + (sel ? "_selected" : "_unselected") + ".png";
        }

        private int hit(double mx, double my) {
            int cx = width / 2, cy = height / 2;
            for (int i = 0; i < 8; i++) {
                double x = cx + OFF[i][0] - 27.5, y = cy + OFF[i][1] - 27.5;
                if (mx >= x && mx < x + 55 && my >= y && my < y + 55) return i;
            }
            if (mx >= cx - 14 && mx < cx + 14 && my >= cy - 14 && my < cy + 14) return 8;
            return -1;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            int cx = width / 2, cy = height / 2;
            int h = hit(mx, my);
            for (int i = 0; i < 8; i++) scaled(g, tex(icon(i, h == i)), cx + OFF[i][0] - 27.5, cy + OFF[i][1] - 27.5, 55, 55, 90, 90);
            scaled(g, tex("textures/ui/wheel/icons/cancel_x" + (h == 8 ? "_hover" : "") + ".png"), cx - 14, cy - 14, 28, 28, 40, 40);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int h = hit(mx, my);
            if (h >= 0) {
                ModNet.CHANNEL.sendToServer(new FormResultPacket(2, h == 8 ? -1 : h, 0));
                Minecraft.getInstance().setScreen(null);
                return true;
            }
            return super.mouseClicked(mx, my, b);
        }

        @Override
        public void onClose() {
            ModNet.CHANNEL.sendToServer(new FormResultPacket(2, -1, 0));
            super.onClose();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
