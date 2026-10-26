package com.restor.magicka.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class GuiGraphics extends GuiComponent {
    private final PoseStack pose;

    public GuiGraphics(PoseStack pose) { this.pose = pose; }

    public PoseStack pose() { return pose; }
    public int guiWidth() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
    public int guiHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }

    public void fill(int x1, int y1, int x2, int y2, int color) { GuiComponent.fill(pose, x1, y1, x2, y2, color); }
    public void fillGradient(int x1, int y1, int x2, int y2, int from, int to) { fillGradient(pose, x1, y1, x2, y2, from, to); }
    public void renderOutline(int x, int y, int w, int h, int color) {
        fill(x, y, x + w, y + 1, color);
        fill(x, y + h - 1, x + w, y + h, color);
        fill(x, y + 1, x + 1, y + h - 1, color);
        fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }
    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }
    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }
    public void drawCenteredString(Font font, Component text, int x, int y, int color) { GuiComponent.drawCenteredString(pose, font, text, x, y, color); }
    public void drawCenteredString(Font font, String text, int x, int y, int color) { GuiComponent.drawCenteredString(pose, font, text, x, y, color); }
    public void blit(net.minecraft.resources.ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        RenderSystem.setShaderTexture(0, texture);
        blit(pose, x, y, u, v, w, h);
    }
    public void blit(net.minecraft.resources.ResourceLocation texture, int x, int y, float u, float v, int w, int h, int texWidth, int texHeight) {
        RenderSystem.setShaderTexture(0, texture);
        blit(pose, x, y, u, v, w, h, texWidth, texHeight);
    }
    public void setColor(float r, float g, float b, float a) { RenderSystem.setShaderColor(r, g, b, a); }
    public void drawWordWrap(Font font, FormattedText text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) { font.draw(pose, line, x, y, color); y += font.lineHeight; }
    }

    public void renderItem(ItemStack stack, int x, int y) {
        var view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y);
        view.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    public void renderTooltip(Font font, Component text, int x, int y) {
        if (Minecraft.getInstance().screen != null) Minecraft.getInstance().screen.renderTooltip(pose, text, x, y);
    }
    public void renderTooltip(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        if (Minecraft.getInstance().screen != null) Minecraft.getInstance().screen.renderTooltip(pose, lines, x, y);
    }
}
