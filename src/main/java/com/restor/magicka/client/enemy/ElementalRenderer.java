package com.restor.magicka.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.restor.magicka.Element;
import com.restor.magicka.Elemental;
import com.restor.magicka.Elementals;
import com.restor.magicka.Magicka;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.EnumMap;
import java.util.Map;

public class ElementalRenderer extends MobRenderer<Elemental, ElementalModel<Elemental>> {
    private final Map<Element, ElementalModel<Elemental>> models = new EnumMap<>(Element.class);

    public ElementalRenderer(EntityRendererProvider.Context context) {
        super(context, new ElementalModel<>(context.bakeLayer(EnemyRenderer.layer("fire_elemental")), "fire_elemental"), .4f);
        for (Element e : Element.values()) {
            String id = Elementals.id(e, "elemental");
            models.put(e, new ElementalModel<>(context.bakeLayer(EnemyRenderer.layer(id)), id));
        }
    }
    static boolean glows(Element e) { return e == Element.FIRE || e == Element.LIGHTNING || e == Element.ARCANE || e == Element.DEATH; }
    @Override public ResourceLocation getTextureLocation(Elemental mob) { return ResourceLocation.fromNamespaceAndPath(Magicka.ID, "textures/entity/" + mob.modelId() + ".png"); }
    @Override protected int getBlockLightLevel(Elemental mob, BlockPos pos) { return glows(mob.element()) ? 15 : super.getBlockLightLevel(mob, pos); }
    @Override public void render(Elemental mob, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        model = models.get(mob.element());
        super.render(mob, yaw, partial, pose, buffers, light);
    }
}
