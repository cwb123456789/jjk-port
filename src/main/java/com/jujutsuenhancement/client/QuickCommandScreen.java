package com.jujutsuenhancement.client;

import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.net.QuickCommandPacket;
import com.jujutsuenhancement.quickmenu.QuickMenu;
import com.jujutsuenhancement.text.BedrockText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The quick-command forms of the addon "咒术觉醒快捷指令" (ActionFormData): a titled window with a text line and a column of buttons;
 * the main form lists the categories, a category form runs a function per button and has a "back" button at the end.
 */
public class QuickCommandScreen extends Screen {
    private static final int W = 230, BTN_H = 20, GAP = 4, HEAD = 44;

    private final int category; // -1: the main form
    private final List<Button> buttons = new ArrayList<>();
    private int scroll;
    private int top, bottom;

    public QuickCommandScreen(int category) {
        super(Component.literal(category < 0 ? QuickMenu.MAIN_TITLE : QuickMenu.CATEGORIES.get(category).title()));
        this.category = category;
    }

    private List<String> labels() {
        List<String> l = new ArrayList<>();
        if (category < 0) for (var c : QuickMenu.CATEGORIES) l.add(c.title());
        else {
            l.addAll(QuickMenu.CATEGORIES.get(category).buttons());
            l.add(QuickMenu.BACK);
        }
        return l;
    }

    @Override
    protected void init() {
        buttons.clear();
        List<String> labels = labels();
        int x = (width - W + 16) / 2;
        top = Math.max(8, (height - Math.min(height - 16, HEAD + labels.size() * (BTN_H + GAP) + 16)) / 2);
        bottom = Math.min(height - 8, top + HEAD + labels.size() * (BTN_H + GAP) + 16);
        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            Button b = Button.builder(BedrockText.parse(labels.get(i)), btn -> press(idx)).bounds(x, 0, W - 16, BTN_H).build();
            buttons.add(b);
            addRenderableWidget(b);
        }
        layout();
    }

    private void layout() {
        int y0 = top + HEAD;
        int viewH = bottom - 8 - y0;
        int total = buttons.size() * (BTN_H + GAP);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - viewH)));
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            int y = y0 + i * (BTN_H + GAP) - scroll;
            b.setY(y);
            b.visible = y >= y0 - 1 && y + BTN_H <= bottom - 6;
        }
    }

    private void press(int idx) {
        if (category < 0) {
            minecraft.setScreen(new QuickCommandScreen(idx));
            return;
        }
        if (idx == QuickMenu.CATEGORIES.get(category).buttons().size()) {
            minecraft.setScreen(new QuickCommandScreen(-1));
            return;
        }
        ModNet.CHANNEL.sendToServer(new QuickCommandPacket(category, idx));
        minecraft.setScreen(null);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) (delta * 18);
        layout();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x0 = (width - W) / 2;
        g.fill(x0 - 1, top - 1, x0 + W + 1, bottom + 1, 0xFF000000);
        g.fill(x0, top, x0 + W, bottom, 0xF0252525);
        g.fill(x0, top, x0 + W, top + 22, 0xFF3A3A3A);
        g.drawCenteredString(font, title, width / 2, top + 7, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal(category < 0 ? QuickMenu.MAIN_BODY : QuickMenu.SUB_BODY), width / 2, top + 30, 0xC0C0C0);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
