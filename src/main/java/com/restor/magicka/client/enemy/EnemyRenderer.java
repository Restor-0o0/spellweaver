package com.restor.magicka.client.enemy;

import com.restor.magicka.Magicka;
import com.restor.magicka.enemy.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.Identifier;

public class EnemyRenderer<T extends SpecialEnemy> extends MobRenderer<T, EnemyState, EnemyModel> {
    private final Identifier texture;

    public EnemyRenderer(EntityRendererProvider.Context context, String id, float shadow) {
        super(context, new EnemyModel(context.bakeLayer(layer(id)), id), shadow);
        texture = Identifier.fromNamespaceAndPath(Magicka.ID, "textures/entity/" + id + ".png");
    }
    public static ModelLayerLocation layer(String id) { return new ModelLayerLocation(Identifier.fromNamespaceAndPath(Magicka.ID, id), "main"); }
    @Override public Identifier getTextureLocation(EnemyState state) { return texture; }
    @Override public EnemyState createRenderState() { return new EnemyState(); }
    @Override public boolean shouldRender(T mob, Frustum frustum, double x, double y, double z, float partial) {
        return !(mob instanceof Burrower b && b.burrowed()) && super.shouldRender(mob, frustum, x, y, z, partial);
    }
    @Override public void extractRenderState(T mob, EnemyState state, float partial) {
        super.extractRenderState(mob, state, partial);
        state.action = mob.action();
        state.state = mob.state();
        state.actionTime = mob.action() != 0 ? mob.actionTime(partial) : 0;
        state.attackTime = mob.getSwingAnimation(partial);
        state.targeting = mob.getTarget() != null;
    }
}
