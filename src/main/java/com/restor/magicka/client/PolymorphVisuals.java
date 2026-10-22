package com.restor.magicka.client;

import com.restor.magicka.Magicka;
import com.restor.magicka.MagicNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.HashMap;
import java.util.Map;

/** Draws a polymorphed player as the animal the server named, without touching the player entity itself. */
@EventBusSubscriber(modid = Magicka.ID, value = Dist.CLIENT)
public final class PolymorphVisuals {
    private record Shape(String id, LivingEntity dummy) {}
    private static final Map<Integer, Shape> SHAPES = new HashMap<>();

    public static void clear() { SHAPES.clear(); }

    @SubscribeEvent public static void render(RenderPlayerEvent.Pre<?> event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(event.getRenderState().id) instanceof Player player)) return;
        String id = MagicNetwork.clientShapes.get(player.getId());
        if (id == null) return;
        LivingEntity dummy = dummy(player, id);
        if (dummy == null) return;
        event.setCanceled(true);
        copy(player, dummy);
        var renderer = renderer(dummy);
        var state = renderer.createRenderState(dummy, event.getPartialTick());
        var pose = event.getPoseStack();
        pose.pushPose();
        renderer.submit(state, pose, event.getSubmitNodeCollector(), mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState);
        pose.popPose();
    }

    @SubscribeEvent public static void hand(RenderHandEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && MagicNetwork.clientShapes.containsKey(player.getId())) event.setCanceled(true);
    }

    private static LivingEntity dummy(Player player, String id) {
        if (SHAPES.size() > MagicNetwork.clientShapes.size())
            SHAPES.keySet().removeIf(key -> !MagicNetwork.clientShapes.containsKey(key));
        Shape shape = SHAPES.get(player.getId());
        if (shape != null && shape.id.equals(id) && shape.dummy.level() == player.level()) return shape.dummy;
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(id)).orElse(null);
        if (type == null || !(type.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD) instanceof LivingEntity created)) return null;
        SHAPES.put(player.getId(), new Shape(id, created));
        return created;
    }

    private static void copy(Player player, LivingEntity dummy) {
        dummy.setPos(player.getX(), player.getY(), player.getZ());
        dummy.xo = player.xo; dummy.yo = player.yo; dummy.zo = player.zo;
        dummy.xOld = player.xOld; dummy.yOld = player.yOld; dummy.zOld = player.zOld;
        dummy.setYRot(player.getYRot()); dummy.yRotO = player.yRotO;
        dummy.setXRot(player.getXRot()); dummy.xRotO = player.xRotO;
        dummy.yBodyRot = player.yBodyRot; dummy.yBodyRotO = player.yBodyRotO;
        dummy.yHeadRot = player.yHeadRot; dummy.yHeadRotO = player.yHeadRotO;
        dummy.setDeltaMovement(player.getDeltaMovement());
        dummy.setOnGround(player.onGround());
        dummy.setInvisible(player.isInvisible());
        dummy.setSprinting(player.isSprinting());
        dummy.setShiftKeyDown(player.isShiftKeyDown());
        boolean self = Minecraft.getInstance().getCameraEntity() == player;
        dummy.setCustomName(self ? null : player.getDisplayName());
        dummy.setCustomNameVisible(!self);
        // The walk cycle advances once per client tick, not once per frame.
        if (dummy.tickCount != player.tickCount) {
            dummy.tickCount = player.tickCount;
            dummy.walkAnimation.update(player.walkAnimation.speed(), 1, 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static EntityRenderer<Entity, EntityRenderState> renderer(Entity entity) {
        return (EntityRenderer<Entity, EntityRenderState>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
    }

    private PolymorphVisuals() {}
}
