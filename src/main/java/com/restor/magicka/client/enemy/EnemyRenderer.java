package com.restor.magicka.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.restor.magicka.Magicka;
import com.restor.magicka.enemy.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class EnemyRenderer<T extends SpecialEnemy> extends MobRenderer<T, EnemyModel<T>> {
    private final ResourceLocation texture;

    public EnemyRenderer(EntityRendererProvider.Context context, String id, float shadow) {
        super(context, new EnemyModel<>(context.bakeLayer(layer(id)), id), shadow);
        texture = ResourceLocation.fromNamespaceAndPath(Magicka.ID, "textures/entity/" + id + ".png");
    }
    public static ModelLayerLocation layer(String id) { return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Magicka.ID, id), "main"); }
    @Override public ResourceLocation getTextureLocation(T mob) { return texture; }
    @Override public void render(T mob, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (mob instanceof Burrower b && b.burrowed()) return;
        super.render(mob, yaw, partial, pose, buffers, light);
    }
}
