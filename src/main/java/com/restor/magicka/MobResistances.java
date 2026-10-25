package com.restor.magicka;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class MobResistances {
    private static final Map<Element, TagKey<EntityType<?>>> RESISTS = tags("resists"), WEAK = tags("weak");

    private static Map<Element, TagKey<EntityType<?>>> tags(String kind) {
        var map = new EnumMap<Element, TagKey<EntityType<?>>>(Element.class);
        for (Element e : Element.values()) map.put(e, TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Magicka.ID, kind + "/" + e.name().toLowerCase(Locale.ROOT))));
        return map;
    }

    private static boolean is(LivingEntity entity, TagKey<EntityType<?>> tag) { return entity.getType().builtInRegistryHolder().is(tag); }

    public static boolean resists(LivingEntity entity, Element element) {
        if (is(entity, RESISTS.get(element))) return true;
        return switch (element) {
            case FIRE -> entity.fireImmune();
            case COLD -> is(entity, EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
            case WATER -> is(entity, EntityTypeTags.AQUATIC);
            case DEATH -> is(entity, EntityTypeTags.UNDEAD);
            default -> false;
        };
    }

    public static boolean weak(LivingEntity entity, Element element) {
        if (is(entity, WEAK.get(element))) return true;
        return switch (element) {
            case FIRE -> is(entity, EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES) && !entity.fireImmune();
            case COLD, WATER -> entity.fireImmune() && !is(entity, EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
            case LIGHTNING -> is(entity, EntityTypeTags.AQUATIC);
            case LIFE -> is(entity, EntityTypeTags.UNDEAD);
            default -> false;
        };
    }

    public static double multiplier(LivingEntity target, String element) {
        if (!MagicConfig.MOB_RESISTANCES.get() || target instanceof Player) return 1;
        Element e;
        try { e = Element.valueOf(element.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { return 1; }
        boolean resists = resists(target, e), weak = weak(target, e);
        if (resists == weak) return 1;
        return resists ? MagicConfig.RESIST_MULTIPLIER.get() : MagicConfig.WEAK_MULTIPLIER.get();
    }

    private MobResistances() {}
}
