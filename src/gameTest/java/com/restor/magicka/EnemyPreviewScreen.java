package com.restor.magicka;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.restor.magicka.client.enemy.EnemyLayers;
import com.restor.magicka.client.enemy.EnemyRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Draws every specialist enemy or elemental model in its rest pose for automated visual inspection. */
public final class EnemyPreviewScreen extends Screen {
    public enum Kind { ENEMIES, ELEMENTALS, TITANS }
    private final float yaw;
    private final Kind kind;
    public EnemyPreviewScreen(float yaw, Kind kind) { super(Component.literal("Enemies")); this.yaw = yaw; this.kind = kind; }
    private boolean shows(String id) {
        return switch (kind) { case ELEMENTALS -> id.endsWith("_elemental"); case TITANS -> id.endsWith("_titan"); default -> !id.endsWith("_elemental") && !id.endsWith("_titan"); };
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0xFF2A3038);
        var ids = EnemyLayers.ALL.keySet().stream().filter(this::shows).sorted().toList();
        int cols = 4, rows = (ids.size() + cols - 1) / cols, cellW = width / cols, cellH = (height - 20) / rows;
        var buffers = minecraft.renderBuffers().bufferSource();
        Lighting.setupForEntityInInventory();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int cx = cellW * (i % cols) + cellW / 2, cy = 14 + cellH * (i / cols) + cellH - 16;
            var root = minecraft.getEntityModels().bakeLayer(EnemyRenderer.layer(id));
            g.flush();
            var pose = g.pose(); pose.pushPose();
            pose.translate(cx, cy, 200);
            float scale = cellH / (kind == Kind.TITANS ? 3.3f : 2.6f);
            pose.scale(scale, scale, -scale);
            pose.mulPose(Axis.XP.rotationDegrees(15)); pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(0, -1.5, 0);
            var tex = ResourceLocation.fromNamespaceAndPath(Magicka.ID, "textures/entity/" + id + ".png");
            root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            buffers.endBatch();
            pose.popPose();
            Component label = Component.translatable("entity.magicka." + id);
            float fit = Math.min(1, (cellW - 6) / (float) font.width(label));
            g.pose().pushPose();
            g.pose().translate(cx, cy + 4, 0);
            g.pose().scale(fit, fit, 1);
            g.drawCenteredString(font, label, 0, 0, 0xFFFFDCA0);
            g.pose().popPose();
        }
        Lighting.setupFor3DItems();
    }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float partial) {}
}
