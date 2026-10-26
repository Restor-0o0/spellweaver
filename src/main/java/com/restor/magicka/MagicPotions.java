package com.restor.magicka;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import java.util.*;

public final class MagicPotions {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Magicka.ID);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, Magicka.ID);
    public static final int DURATION = 3600, LONG_DURATION = 9600, CAST_SPEED_BONUS = 25, DAMAGE_BONUS = 20;
    public static final float WARD = .6f;

    public static final Map<Element, RegistryObject<MobEffect>> WARDS = new EnumMap<>(Element.class);
    static {
        for (Element element : List.of(Element.FIRE, Element.COLD, Element.LIGHTNING, Element.ARCANE, Element.DEATH))
            WARDS.put(element, effect(element.name().toLowerCase(Locale.ROOT) + "_ward", element.color));
    }
    public static final RegistryObject<MobEffect> SWIFT_CASTING = effect("swift_casting", 0xFFFFDC84);
    public static final RegistryObject<MobEffect> SPELL_FOCUS = effect("spell_focus", 0xFFC79ADB);
    public static final Map<String, RegistryObject<Potion>> ALL = new LinkedHashMap<>();
    static {
        WARDS.values().forEach(MagicPotions::potions);
        potions(SWIFT_CASTING);
        potions(SPELL_FOCUS);
    }

    private static final Set<UUID> DIRTY = new HashSet<>();

    private static final class Effect extends MobEffect {
        Effect(int color) { super(MobEffectCategory.BENEFICIAL, color & 0xFFFFFF); }
    }

    private static RegistryObject<MobEffect> effect(String name, int color) {
        return EFFECTS.register(name, () -> new Effect(color));
    }

    private static void potions(RegistryObject<MobEffect> effect) {
        String name = effect.getId().getPath();
        ALL.put(name, POTIONS.register(name, () -> new Potion("magicka_" + name, new MobEffectInstance(effect.get(), DURATION))));
        ALL.put("long_" + name, POTIONS.register("long_" + name, () -> new Potion("magicka_" + name, new MobEffectInstance(effect.get(), LONG_DURATION))));
    }

    public static float ward(LivingEntity entity, Element element, float amount) {
        var ward = element == null ? null : WARDS.get(element);
        return ward != null && entity.hasEffect(ward.get()) ? amount * WARD : amount;
    }

    public static void bonus(LivingEntity entity, int[] deltas) {
        var swift = entity.getEffect(SWIFT_CASTING.get());
        if (swift != null) deltas[MagicStats.Stat.CAST_SPEED.ordinal()] += CAST_SPEED_BONUS * (swift.getAmplifier() + 1);
        var focus = entity.getEffect(SPELL_FOCUS.get());
        if (focus != null) deltas[MagicStats.Stat.DAMAGE.ordinal()] += DAMAGE_BONUS * (focus.getAmplifier() + 1);
    }

    private static boolean scaling(MobEffect effect) {
        return effect != null && (effect == SWIFT_CASTING.get() || effect == SPELL_FOCUS.get());
    }

    public static ItemStack stack(Item item, Potion potion) { return PotionUtils.setPotion(new ItemStack(item), potion); }

    private static void changed(LivingEntity entity, MobEffect effect) {
        if (entity instanceof ServerPlayer player && scaling(effect)) DIRTY.add(player.getUUID());
    }

    @SubscribeEvent public void added(MobEffectEvent.Added event) { changed(event.getEntity(), event.getEffectInstance().getEffect()); }
    @SubscribeEvent public void removed(MobEffectEvent.Remove event) { changed(event.getEntity(), event.getEffect()); }
    @SubscribeEvent public void expired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null) changed(event.getEntity(), event.getEffectInstance().getEffect());
    }
    private record Mix(Potion from, Item reagent, RegistryObject<Potion> to) implements net.minecraftforge.common.brewing.IBrewingRecipe {
        @Override public boolean isInput(ItemStack input) { return PotionUtils.getPotion(input) == from; }
        @Override public boolean isIngredient(ItemStack ingredient) { return ingredient.is(reagent); }
        @Override public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            return isInput(input) && isIngredient(ingredient) ? stack(input.getItem(), to.get()) : ItemStack.EMPTY;
        }
    }

    public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) { event.enqueueWork(MagicPotions::brewing); }

    private static void brewing() {
        var reagents = Map.of("fire_ward", Items.MAGMA_BLOCK, "cold_ward", Items.PACKED_ICE, "lightning_ward", Items.LIGHTNING_ROD,
                "arcane_ward", Items.AMETHYST_SHARD, "death_ward", Items.BONE_BLOCK, "swift_casting", Items.GLOW_INK_SAC, "spell_focus", Items.LAPIS_LAZULI);
        reagents.forEach((name, reagent) -> {
            net.minecraftforge.common.brewing.BrewingRecipeRegistry.addRecipe(new Mix(Potions.AWKWARD, reagent, ALL.get(name)));
            net.minecraftforge.common.brewing.BrewingRecipeRegistry.addRecipe(new Mix(ALL.get(name).get(), Items.REDSTONE, ALL.get("long_" + name)));
        });
    }

    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && DIRTY.remove(player.getUUID())) MagicStats.invalidate(player);
    }

    public MagicPotions() {}
}
