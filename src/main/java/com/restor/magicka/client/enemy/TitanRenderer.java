package com.restor.magicka.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.restor.magicka.Element;
import com.restor.magicka.ElementalTitan;
import com.restor.magicka.Elementals;
import com.restor.magicka.Magicka;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import java.util.EnumMap;
import java.util.Map;

public class TitanRenderer extends EntityRenderer<ElementalTitan, ElementalState> {
    private static final float DEPTH = 7;
    private final Map<Element, ElementalModel> models = new EnumMap<>(Element.class);

    public TitanRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
        for (Element e : Element.values()) {
            String id = Elementals.id(e, "titan");
            models.put(e, new ElementalModel(context.bakeLayer(EnemyRenderer.layer(id)), id));
        }
    }
    @Override public ElementalState createRenderState() { return new ElementalState(); }
    @Override public boolean shouldRender(ElementalTitan titan, Frustum frustum, double x, double y, double z) { return frustum.isVisible(titan.getBoundingBox().inflate(4, 2, 4)); }
    @Override public void extractRenderState(ElementalTitan titan, ElementalState state, float partial) {
        super.extractRenderState(titan, state, partial);
        ElementalRenderer.extract(titan, state, partial);
        state.titan = true;
        state.element = titan.element();
        state.emergence = titan.emergence(partial);
        state.bodyRot = Mth.rotLerp(partial, titan.yRotO, titan.getYRot());
        state.texture = Identifier.fromNamespaceAndPath(Magicka.ID, "textures/entity/" + titan.modelId() + ".png");
        if (ElementalRenderer.glows(titan.element())) state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        else state.lightCoords = LightCoordsUtil.pack(Math.max(10, LightCoordsUtil.block(state.lightCoords)), LightCoordsUtil.sky(state.lightCoords));
    }
    @Override public void submit(ElementalState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.emergence <= 0) return;
        pose.pushPose();
        pose.translate(0, -(1 - state.emergence) * DEPTH, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180 - state.bodyRot));
        pose.scale(-ElementalTitan.SCALE, -ElementalTitan.SCALE, ElementalTitan.SCALE);
        pose.translate(0, -1.501, 0);
        collector.submitModel(models.get(state.element), state, pose, RenderTypes.entityCutout(state.texture), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, 0, null);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
