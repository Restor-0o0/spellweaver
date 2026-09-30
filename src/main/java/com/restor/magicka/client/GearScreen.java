package com.restor.magicka.client;

import com.restor.magicka.GearItem;
import com.restor.magicka.GearMenu;
import com.restor.magicka.MagicGear;
import com.restor.magicka.MagicStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import java.util.*;

public final class GearScreen extends AbstractContainerScreen<GearMenu> {
    private static final String[] ROW_LABELS = {"equipment", "rings", "trinkets"};
    private static final int[] ROW_Y = {21, 53, 85};

    public GearScreen(GearMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 214;
        imageHeight = 222;
        titleLabelX = 10;
        titleLabelY = 7;
        inventoryLabelX = 26;
        inventoryLabelY = 127;
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        MagicUi.panel(g, leftPos, topPos, imageWidth, imageHeight);
        MagicUi.panel(g, leftPos + 18, topPos + 16, 104, 102);
        MagicUi.panel(g, leftPos + 126, topPos + 16, 80, 102);
        for (int row = 0; row < ROW_LABELS.length; row++)
            MagicUi.small(g, Component.translatable("screen.magicka.row." + ROW_LABELS[row]), leftPos + 25, topPos + ROW_Y[row], MagicUi.GOLD);
        for (MagicGear.Slot slot : MagicGear.Slot.values()) {
            int[] position = GearMenu.POSITIONS[slot.ordinal()];
            socket(g, leftPos + position[0] - 1, topPos + position[1] - 1, menu.slots.get(slot.ordinal()).hasItem());
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            socket(g, leftPos + 25 + column * 18, topPos + 136 + row * 18, false);
        for (int column = 0; column < 9; column++) socket(g, leftPos + 25 + column * 18, topPos + 194, false);
        summary(g);
    }

    private static void socket(GuiGraphics g, int x, int y, boolean filled) {
        g.fill(x, y, x + 18, y + 18, 0xFF0E1119);
        g.renderOutline(x, y, 18, 18, filled ? MagicUi.GOLD : 0xFF423E4C);
    }

    private void summary(GuiGraphics g) {
        int[] deltas = new int[MagicStats.COUNT];
        Set<MagicGear.Trait> traits = EnumSet.noneOf(MagicGear.Trait.class);
        int replaced = 0;
        for (int index = 0; index < MagicGear.SLOTS; index++) {
            if (!(menu.slots.get(index).getItem().getItem() instanceof GearItem gear)) continue;
            gear.stats().forEach((stat, delta) -> deltas[stat.ordinal()] += delta);
            traits.addAll(gear.traits());
            replaced += gear.patches().size();
        }
        int x = leftPos + 132, y = topPos + 22;
        MagicUi.text(g, Component.translatable("screen.magicka.summary"), x, y, MagicUi.GOLD);
        y += 11;
        List<String> roles = roles(deltas, traits);
        if (roles.isEmpty()) {
            MagicUi.small(g, Component.translatable("screen.magicka.role.none"), x, y, MagicUi.MUTED);
            y += 8;
        } else for (String role : roles) {
            MagicUi.small(g, Component.translatable("screen.magicka.role." + role), x, y, 0xFF8FD4A8);
            y += 8;
        }
        y += 4;
        for (MagicStats.Stat stat : MagicStats.Stat.values()) {
            if (deltas[stat.ordinal()] == 0 || y > topPos + 104) continue;
            int delta = deltas[stat.ordinal()];
            MagicUi.small(g, Component.literal((delta > 0 ? "+" : "") + delta + "% ")
                    .append(Component.translatable("book.magicka.stat." + stat.key())), x, y, delta > 0 ? 0xFF9AD68F : 0xFFD68F8F);
            y += 8;
        }
        if (replaced > 0 && y <= topPos + 104)
            MagicUi.small(g, Component.translatable("screen.magicka.replaced", replaced), x, y, 0xFFC79ADB);
    }

    private static List<String> roles(int[] deltas, Set<MagicGear.Trait> traits) {
        List<String> roles = new ArrayList<>();
        if (deltas[MagicStats.Stat.HEALING.ordinal()] + deltas[MagicStats.Stat.LIFE.ordinal()] >= 30) roles.add("heal");
        if (deltas[MagicStats.Stat.SUMMONS.ordinal()] >= 30) roles.add("summon");
        if (deltas[MagicStats.Stat.CONSTRUCTION.ordinal()] >= 30) roles.add("build");
        if (deltas[MagicStats.Stat.EARTH.ordinal()] >= 30 || traits.contains(MagicGear.Trait.STONEHEART)) roles.add("tank");
        if (deltas[MagicStats.Stat.LIGHTNING.ordinal()] + deltas[MagicStats.Stat.CHAINS.ordinal()] >= 30) roles.add("storm");
        if (deltas[MagicStats.Stat.COLD.ordinal()] >= 30) roles.add("frost");
        if (deltas[MagicStats.Stat.FIRE.ordinal()] >= 30) roles.add("pyro");
        if (traits.contains(MagicGear.Trait.GLASS_HEART) || traits.contains(MagicGear.Trait.CHAOS_LENS)
                || traits.contains(MagicGear.Trait.BLOOD_FOCUS) || traits.contains(MagicGear.Trait.FIRE_FRAIL)) roles.add("risk");
        return roles;
    }

    @Override protected void renderTooltip(GuiGraphics g, int x, int y) {
        Slot slot = hoveredSlot;
        if (slot instanceof GearMenu.GearSlot gear && !slot.hasItem())
            g.renderTooltip(font, Component.translatable("screen.magicka.slot." + gear.gear.key()), x, y);
        else super.renderTooltip(g, x, y);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MagicUi.text(g, title, titleLabelX, titleLabelY, MagicUi.GOLD);
        MagicUi.text(g, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MagicUi.MUTED);
    }
}
