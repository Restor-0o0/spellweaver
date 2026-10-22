package com.restor.magicka.client.enemy;

import com.restor.magicka.Magicka;
import com.restor.magicka.enemy.MagicEnemies;
import com.restor.magicka.enemy.SpecialEnemy;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.Map;

@EventBusSubscriber(modid = Magicka.ID, value = Dist.CLIENT)
public final class EnemyClient {
    private static final Map<String, Float> SHADOWS = Map.ofEntries(
            Map.entry("cave_skitter", .45f), Map.entry("shield_beetle", .8f), Map.entry("stone_crawler", .9f), Map.entry("mushroom_nurse", .45f),
            Map.entry("element_binder", .4f), Map.entry("burrower", .55f), Map.entry("cave_wisp", .35f), Map.entry("stone_eater", .75f),
            Map.entry("flood_caster", .5f), Map.entry("countermage", .4f), Map.entry("spark_imp", .25f),
            Map.entry("goblin_warrior", .4f), Map.entry("goblin_archer", .4f), Map.entry("goblin_mage", .4f),
            Map.entry("goblin_frost_mage", .4f), Map.entry("goblin_storm_mage", .4f), Map.entry("goblin_bomber", .4f), Map.entry("goblin_shaman", .4f));

    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        EnemyLayers.ALL.forEach((id, layer) -> event.registerLayerDefinition(EnemyRenderer.layer(id), layer));
    }
    @SuppressWarnings("unchecked")
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MagicEnemies.BOLT.get(), NoopRenderer::new);
        event.registerEntityRenderer(com.restor.magicka.Elementals.COMPANION.get(), ElementalRenderer::new);
        event.registerEntityRenderer(com.restor.magicka.Elementals.TITAN.get(), TitanRenderer::new);
        event.registerEntityRenderer(MagicEnemies.GOBLIN_BOMB.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        for (var holder : MagicEnemies.TYPES.getEntries()) {
            String id = holder.getId().getPath();
            if (!EnemyLayers.ALL.containsKey(id)) continue;
            var type = (EntityType<SpecialEnemy>)((DeferredHolder<EntityType<?>, ?>)holder).get();
            event.registerEntityRenderer(type, context -> new EnemyRenderer<>(context, id, SHADOWS.getOrDefault(id, .5f)));
        }
    }
    private EnemyClient() {}
}
