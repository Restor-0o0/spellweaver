package com.restor.magicka;

import com.restor.magicka.enemy.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import java.util.*;

public final class ScrollDrops {
    public static final double SPECIALIST_CHANCE = .04, CASTER_CHANCE = .1;
    public static final int BOSS_ULTIMATE_WEIGHT = 3;

    public static boolean boss(LivingEntity entity) { return entity.getType().builtInRegistryHolder().is(Tags.EntityTypes.BOSSES); }

    public static double chance(LivingEntity entity) {
        if (boss(entity)) return 1;
        if (entity instanceof Countermage || entity instanceof ElementBinder || entity instanceof GoblinMage || entity instanceof GoblinShaman) return CASTER_CHANCE;
        return entity instanceof SpecialEnemy ? SPECIALIST_CHANCE : 0;
    }

    public static SpellDefinition pick(RandomSource random, Set<String> known, int ultimateWeight, int weight) {
        var locked = RecipeRegistry.recipes().stream().filter(SpellScrolls::locked).sorted(Comparator.comparing(s -> s.id().toString())).toList();
        var fresh = locked.stream().filter(s -> !known.contains(s.id().toString())).toList();
        SpellDefinition spell = roll(random, fresh, ultimateWeight, weight);
        return spell != null ? spell : roll(random, locked, ultimateWeight, weight);
    }

    private static SpellDefinition roll(RandomSource random, List<SpellDefinition> pool, int ultimateWeight, int weight) {
        int total = pool.stream().mapToInt(s -> s.ultimate() ? ultimateWeight : weight).sum();
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (SpellDefinition spell : pool) {
            roll -= spell.ultimate() ? ultimateWeight : weight;
            if (roll < 0) return spell;
        }
        return null;
    }

    public static SpellDefinition drop(LivingEntity entity, ServerPlayer killer) {
        return pick(entity.getRandom(), SpellScrolls.known(killer), boss(entity) ? BOSS_ULTIMATE_WEIGHT : 0, 1);
    }

    @SubscribeEvent public void drops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || entity.getRandom().nextDouble() >= chance(entity)) return;
        SpellDefinition spell = drop(entity, killer);
        if (spell != null) event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY() + .5, entity.getZ(), SpellScrollItem.of(spell)));
    }

    public ScrollDrops() {}
}
