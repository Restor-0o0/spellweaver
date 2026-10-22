package com.restor.magicka.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.restor.magicka.Element;
import com.restor.magicka.Elemental;
import com.restor.magicka.Elementals;
import com.restor.magicka.Magicka;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import java.util.EnumMap;
import java.util.Map;

public class ElementalRenderer extends MobRenderer<Elemental, ElementalState, ElementalModel> {
    private final Map<Element, ElementalModel> models = new EnumMap<>(Element.class);

    public ElementalRenderer(EntityRendererProvider.Context context) {
        super(context, new ElementalModel(context.bakeLayer(EnemyRenderer.layer("fire_elemental")), "fire_elemental"), .4f);
        for (Element e : Element.values()) {
            String id = Elementals.id(e, "elemental");
            models.put(e, new ElementalModel(context.bakeLayer(EnemyRenderer.layer(id)), id));
        }
    }
    static boolean glows(Element e) { return e == Element.FIRE || e == Element.LIGHTNING || e == Element.ARCANE || e == Element.DEATH; }
    static void extract(com.restor.magicka.ElementalBody mob, ElementalState state, float partial) {
        state.action = mob.action();
        state.previousAction = mob.previousAction();
        state.actionTime = mob.actionTime(partial);
    }
    @Override public Identifier getTextureLocation(ElementalState state) { return state.texture; }
    @Override public ElementalState createRenderState() { return new ElementalState(); }
    @Override protected int getBlockLightLevel(Elemental mob, BlockPos pos) { return glows(mob.element()) ? 15 : super.getBlockLightLevel(mob, pos); }
    @Override public void extractRenderState(Elemental mob, ElementalState state, float partial) {
        super.extractRenderState(mob, state, partial);
        extract(mob, state, partial);
        state.element = mob.element();
        state.texture = Identifier.fromNamespaceAndPath(Magicka.ID, "textures/entity/" + mob.modelId() + ".png");
    }
    @Override public void submit(ElementalState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        model = models.get(state.element);
        super.submit(state, pose, collector, camera);
    }
}
