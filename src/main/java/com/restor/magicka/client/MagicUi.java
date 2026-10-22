package com.restor.magicka.client;

import com.restor.magicka.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared ink, brass and vanilla pictograms for the grimoire and HUD. */
public final class MagicUi {
    public static final int GOLD = 0xFFD4B77A, INK = 0xFF302B37, MUTED = 0xFFAFA6B9;
    private static final Item[] ICONS = {Items.BLAZE_POWDER, Items.WATER_BUCKET, Items.SNOWBALL,
        Items.GLOWSTONE_DUST, Items.CLAY_BALL, Items.OAK_SAPLING, Items.AMETHYST_SHARD, Items.SKELETON_SKULL};
    private static final ItemStack[] STACKS = new ItemStack[ICONS.length];
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x60000000);
        g.fill(x, y, x + w, y + h, 0xDF141820);
        g.outline(x, y, w, h, 0xFF494454);
        for (int dx : new int[]{0, w - 5}) for (int dy : new int[]{0, h - 1}) g.fill(x + dx, y + dy, x + dx + 5, y + dy + 1, GOLD);
    }
    public static void icon(GuiGraphicsExtractor g, Element e, int x, int y) {
        int i = e.ordinal();
        if (STACKS[i] == null) {
            if (!ICONS[i].builtInRegistryHolder().areComponentsBound()) return;
            STACKS[i] = new ItemStack(ICONS[i]);
        }
        g.item(STACKS[i], x, y);
    }
    public static void socket(GuiGraphicsExtractor g, Element e, int x, int y, boolean lit) {
        g.fill(x, y, x + 24, y + 24, 0xFF0E1119);
        g.outline(x, y, 24, 24, lit && e != null ? e.color : 0xFF423E4C);
        if (e != null) {
            icon(g, e, x + 4, y + 3);
            g.fill(x + 6, y + 21, x + 18, y + 22, e.color);
        } else {
            g.fill(x + 11, y + 10, x + 13, y + 14, 0xFF514957);
            g.fill(x + 10, y + 11, x + 14, y + 13, 0xFF514957);
        }
    }
    public static void text(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        g.text(Minecraft.getInstance().font, text, x, y, color, false);
    }
    public static void small(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(0.75F, 0.75F);
        text(g, text, 0, 0, color); g.pose().popMatrix();
    }
    private MagicUi() {}
}
