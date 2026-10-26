package com.restor.magicka.client;

import com.restor.magicka.MagicGear;
import com.restor.magicka.MagicNetwork;
import com.restor.magicka.PotionBelt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PotionWheelScreen extends Screen {
    private static final int RADIUS = 46;
    final int button;
    private int hovered = -1;

    PotionWheelScreen(int button) {
        super(Component.translatable("key.magicka.potion_" + (button + 1)));
        this.button = button;
    }

    private int slotAt(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy < 14 * 14) return -1;
        double angle = Math.atan2(dx, -dy);
        return Math.floorMod((int) Math.round(angle / (Math.PI * 2 / MagicGear.BELT)), MagicGear.BELT);
    }

    private int x(int slot) { return width / 2 + (int) Math.round(Math.sin(slot * Math.PI * 2 / MagicGear.BELT) * RADIUS); }
    private int y(int slot) { return height / 2 - (int) Math.round(Math.cos(slot * Math.PI * 2 / MagicGear.BELT) * RADIUS); }

    void confirm() {
        if (hovered >= 0) MagicNetwork.sendToServer(new MagicNetwork.BeltAction(button, hovered));
        onClose();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hovered = slotAt(mouseX, mouseY);
        var belt = PotionBelt.client();
        int color = GearScreen.BUTTON_COLORS[button];
        MagicUi.small(g, getTitle(), width / 2 - (int) (font.width(getTitle()) * .375f), height / 2 - 3, color);
        for (int i = 0; i < MagicGear.BELT; i++) {
            int x = x(i) - 10, y = y(i) - 10;
            boolean selected = PotionBelt.clientSelected(button) == i;
            g.fill(x, y, x + 20, y + 20, i == hovered ? 0xE0504060 : 0xC0151320);
            int edge = selected ? color : i == hovered ? MagicUi.GOLD : 0xFF3A3445;
            g.fill(x, y, x + 20, y + 1, edge); g.fill(x, y + 19, x + 20, y + 20, edge);
            g.fill(x, y, x + 1, y + 20, edge); g.fill(x + 19, y, x + 20, y + 20, edge);
            if (!belt.get(i).isEmpty()) g.renderItem(belt.get(i), x + 2, y + 2);
        }
        if (hovered >= 0 && !belt.get(hovered).isEmpty())
            g.renderTooltip(font, belt.get(hovered).getHoverName(), mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        hovered = slotAt(mouseX, mouseY);
        if (hovered >= 0) { confirm(); return true; }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g) {}

    static void open(int button) { Minecraft.getInstance().setScreen(new PotionWheelScreen(button)); }
}
