package com.restor.magicka.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.restor.magicka.Element;
import com.restor.magicka.ElementalTitan;
import com.restor.magicka.Elementals;
import com.restor.magicka.Magicka;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import java.util.EnumMap;
import java.util.Map;

public class TitanRenderer extends EntityRenderer<ElementalTitan> {
    private static final float DEPTH = 7;
    private final Map<Element, ElementalModel<ElementalTitan>> models = new EnumMap<>(Element.class);

    public TitanRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
        for (Element e : Element.values()) {
            String id = Elementals.id(e, "titan");
            models.put(e, new ElementalModel<>(context.bakeLayer(EnemyRenderer.layer(id)), id));
        }
    }
    @Override public ResourceLocation getTextureLocation(ElementalTitan titan) { return ResourceLocation.fromNamespaceAndPath(Magicka.ID, "textures/entity/" + titan.modelId() + ".png"); }
    @Override public boolean shouldRender(ElementalTitan titan, Frustum frustum, double x, double y, double z) { return frustum.isVisible(titan.getBoundingBox().inflate(4, 2, 4)); }
    @Override public void render(ElementalTitan titan, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float up = titan.emergence(partial);
        if (up <= 0) return;
        var model = models.get(titan.element());
        pose.pushPose();
        pose.translate(0, -(1 - up) * DEPTH, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, titan.yRotO, titan.getYRot())));
        pose.scale(-ElementalTitan.SCALE, -ElementalTitan.SCALE, ElementalTitan.SCALE);
        pose.translate(0, -1.501, 0);
        model.setupAnim(titan, 0, 0, titan.tickCount + partial, 0, 0);
        int shade = ElementalRenderer.glows(titan.element()) ? LightTexture.FULL_BRIGHT : LightTexture.pack(Math.max(10, LightTexture.block(light)), LightTexture.sky(light));
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(titan))), shade, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(titan, yaw, partial, pose, buffers, light);
    }
}
