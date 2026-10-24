package com.restor.magicka;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;

public final class MagicPotions {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Magicka.ID);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, Magicka.ID);
    public static final int DURATION = 3600, LONG_DURATION = 9600, CAST_SPEED_BONUS = 25, DAMAGE_BONUS = 20;
    public static final float WARD = .6f;

    public static final Map<Element, DeferredHolder<MobEffect, MobEffect>> WARDS = new EnumMap<>(Element.class);
    static {
        for (Element element : List.of(Element.FIRE, Element.COLD, Element.LIGHTNING, Element.ARCANE, Element.DEATH))
            WARDS.put(element, effect(element.name().toLowerCase(Locale.ROOT) + "_ward", element.color));
    }
    public static final DeferredHolder<MobEffect, MobEffect> SWIFT_CASTING = effect("swift_casting", 0xFFFFDC84);
    public static final DeferredHolder<MobEffect, MobEffect> SPELL_FOCUS = effect("spell_focus", 0xFFC79ADB);
    public static final Map<String, DeferredHolder<Potion, Potion>> ALL = new LinkedHashMap<>();
    static {
        WARDS.values().forEach(MagicPotions::potions);
        potions(SWIFT_CASTING);
        potions(SPELL_FOCUS);
    }

    private static final Set<UUID> DIRTY = new HashSet<>();

    private static final class Effect extends MobEffect {
        Effect(int color) { super(MobEffectCategory.BENEFICIAL, color & 0xFFFFFF); }
    }

    private static DeferredHolder<MobEffect, MobEffect> effect(String name, int color) {
        return EFFECTS.register(name, () -> new Effect(color));
    }

    private static void potions(DeferredHolder<MobEffect, MobEffect> effect) {
        String name = effect.getId().getPath();
        ALL.put(name, POTIONS.register(name, () -> new Potion("magicka_" + name, new MobEffectInstance(effect, DURATION))));
        ALL.put("long_" + name, POTIONS.register("long_" + name, () -> new Potion("magicka_" + name, new MobEffectInstance(effect, LONG_DURATION))));
    }

    public static float ward(LivingEntity entity, Element element, float amount) {
        var ward = element == null ? null : WARDS.get(element);
        return ward != null && entity.hasEffect(ward) ? amount * WARD : amount;
    }

    public static void bonus(LivingEntity entity, int[] deltas) {
        var swift = entity.getEffect(SWIFT_CASTING);
        if (swift != null) deltas[MagicStats.Stat.CAST_SPEED.ordinal()] += CAST_SPEED_BONUS * (swift.getAmplifier() + 1);
        var focus = entity.getEffect(SPELL_FOCUS);
        if (focus != null) deltas[MagicStats.Stat.DAMAGE.ordinal()] += DAMAGE_BONUS * (focus.getAmplifier() + 1);
    }

    private static boolean scaling(Holder<MobEffect> effect) {
        return effect != null && (effect.is(SWIFT_CASTING.getId()) || effect.is(SPELL_FOCUS.getId()));
    }

    private static void changed(LivingEntity entity, Holder<MobEffect> effect) {
        if (entity instanceof ServerPlayer player && scaling(effect)) DIRTY.add(player.getUUID());
    }

    @SubscribeEvent public void added(MobEffectEvent.Added event) { changed(event.getEntity(), event.getEffectInstance().getEffect()); }
    @SubscribeEvent public void removed(MobEffectEvent.Remove event) { changed(event.getEntity(), event.getEffect()); }
    @SubscribeEvent public void expired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null) changed(event.getEntity(), event.getEffectInstance().getEffect());
    }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && DIRTY.remove(player.getUUID())) MagicStats.invalidate(player);
    }

    @SubscribeEvent public void brewing(net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent event) {
        var reagents = Map.<String, net.minecraft.world.item.Item>of("fire_ward", Items.MAGMA_BLOCK, "cold_ward", Items.PACKED_ICE, "lightning_ward", Items.LIGHTNING_ROD,
                "arcane_ward", Items.AMETHYST_SHARD, "death_ward", Items.BONE_BLOCK, "swift_casting", Items.GLOW_INK_SAC, "spell_focus", Items.LAPIS_LAZULI);
        reagents.forEach((name, reagent) -> {
            event.getBuilder().addMix(Potions.AWKWARD, reagent, ALL.get(name));
            event.getBuilder().addMix(ALL.get(name), Items.REDSTONE, ALL.get("long_" + name));
        });
    }

    public MagicPotions() {}
}
