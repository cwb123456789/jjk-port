package com.jujutsuenhancement.skin;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Chooses which installed skin pack to open (when several skin mods are installed). */
final class PackSelectScreen extends Screen {
    private final List<SkinBridge> packs;

    PackSelectScreen(List<SkinBridge> packs) {
        super(Component.literal("选择皮肤包"));
        this.packs = packs;
    }

    @Override
    protected void init() {
        int y = this.height / 2 - packs.size() * 12;
        for (SkinBridge b : packs) {
            addRenderableWidget(Button.builder(Component.literal(b.display), btn -> {
                onClose();
                SkinClient.openFrom(b);
            }).bounds(this.width / 2 - 100, y, 200, 20).build());
            y += 24;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - packs.size() * 12 - 20, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }
}
