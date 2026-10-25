package com.restor.magicka;

import com.restor.magicka.enemy.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import java.util.*;

public final class ScrollDrops {
    public static final TagKey<EntityType<?>> CASTERS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Magicka.ID, "spellcasters"));
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Magicka.ID, "bosses"));
    public static final TagKey<EntityType<?>> NO_DROPS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Magicka.ID, "no_scroll_drops"));

    private static boolean is(LivingEntity entity, TagKey<EntityType<?>> tag) { return entity.getType().builtInRegistryHolder().is(tag); }

    public static boolean boss(LivingEntity entity) {
        if (is(entity, Tags.EntityTypes.BOSSES) || is(entity, BOSSES)) return true;
        int threshold = MagicConfig.BOSS_HEALTH.get();
        return threshold > 0 && entity instanceof Enemy && entity.getMaxHealth() >= threshold;
    }

    public static boolean caster(LivingEntity entity) {
        return entity instanceof Countermage || entity instanceof ElementBinder || entity instanceof GoblinMage || entity instanceof GoblinShaman || is(entity, CASTERS);
    }

    public static double chance(LivingEntity entity) {
        if (is(entity, NO_DROPS)) return 0;
        if (boss(entity)) return MagicConfig.BOSS_SCROLL_CHANCE.get();
        if (entity instanceof SpecialEnemy) return caster(entity) ? MagicConfig.SPECIALIST_CASTER_SCROLL_CHANCE.get() : MagicConfig.SPECIALIST_SCROLL_CHANCE.get();
        if (!MagicConfig.ALL_MOB_SCROLLS.get() || !(entity instanceof Enemy)) return 0;
        return caster(entity) ? MagicConfig.CASTER_SCROLL_CHANCE.get() : MagicConfig.MOB_SCROLL_CHANCE.get();
    }

    public static int bias(Level level, SpellDefinition spell) {
        if (level == null || !MagicConfig.DIMENSION_SCROLLS.get()) return 1;
        if (level.dimension() == Level.NETHER) return spell.formula().fire() > 0 || spell.ultimate() ? 3 : 1;
        if (level.dimension() == Level.END) return spell.formula().arcane() > 0 || spell.ultimate() ? 3 : 1;
        return 1;
    }

    public static SpellDefinition pick(RandomSource random, Set<String> known, int ultimateWeight, int weight) { return pick(random, known, ultimateWeight, weight, null); }

    public static SpellDefinition pick(RandomSource random, Set<String> known, int ultimateWeight, int weight, Level level) {
        var locked = RecipeRegistry.recipes().stream().filter(SpellScrolls::locked).sorted(Comparator.comparing(s -> s.id().toString())).toList();
        var fresh = locked.stream().filter(s -> !known.contains(s.id().toString())).toList();
        SpellDefinition spell = roll(random, fresh, ultimateWeight, weight, level);
        return spell != null ? spell : roll(random, locked, ultimateWeight, weight, level);
    }

    private static SpellDefinition roll(RandomSource random, List<SpellDefinition> pool, int ultimateWeight, int weight, Level level) {
        int total = pool.stream().mapToInt(s -> weight(s, ultimateWeight, weight, level)).sum();
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (SpellDefinition spell : pool) {
            roll -= weight(spell, ultimateWeight, weight, level);
            if (roll < 0) return spell;
        }
        return null;
    }

    private static int weight(SpellDefinition spell, int ultimateWeight, int weight, Level level) {
        return (spell.ultimate() ? ultimateWeight : weight) * bias(level, spell);
    }

    public static SpellDefinition drop(LivingEntity entity, ServerPlayer killer) {
        return pick(entity.getRandom(), SpellScrolls.known(killer), boss(entity) ? MagicConfig.BOSS_ULTIMATE_WEIGHT.get() : 0, 1, entity.level());
    }

    @SubscribeEvent public void drops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || entity.getRandom().nextDouble() >= chance(entity)) return;
        SpellDefinition spell = drop(entity, killer);
        if (spell != null) event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY() + .5, entity.getZ(), SpellScrollItem.of(spell)));
    }

    public ScrollDrops() {}
}
