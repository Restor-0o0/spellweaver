package com.restor.magicka;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;

public record GearBonus(Map<String, Integer> stats, List<String> traits, List<String> rules, List<String> charms) {
    public static final GearBonus EMPTY = new GearBonus(Map.of(), List.of(), List.of(), List.of());
    public static final Codec<GearBonus> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stats", Map.of()).forGetter(GearBonus::stats),
            Codec.STRING.listOf().optionalFieldOf("traits", List.of()).forGetter(GearBonus::traits),
            Codec.STRING.listOf().optionalFieldOf("rules", List.of()).forGetter(GearBonus::rules),
            Codec.STRING.listOf().optionalFieldOf("charms", List.of()).forGetter(GearBonus::charms)
    ).apply(i, GearBonus::new));
    public static final StreamCodec<ByteBuf, GearBonus> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Magicka.ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GearBonus>> TYPE = COMPONENTS.registerComponentType("gear_bonus",
            builder -> builder.persistent(CODEC).networkSynchronized(STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.resources.ResourceLocation>> SCROLL = COMPONENTS.registerComponentType("scroll_spell",
            builder -> builder.persistent(net.minecraft.resources.ResourceLocation.CODEC).networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC));

    public static GearBonus of(ItemStack stack) { return stack.getOrDefault(TYPE.get(), EMPTY); }

    public Map<MagicStats.Stat, Integer> statMap() {
        Map<MagicStats.Stat, Integer> result = new EnumMap<>(MagicStats.Stat.class);
        stats.forEach((key, value) -> {
            for (MagicStats.Stat stat : MagicStats.Stat.values()) if (stat.key().equals(key)) result.merge(stat, value, Integer::sum);
        });
        return result;
    }

    public Set<MagicGear.Trait> traitSet() {
        Set<MagicGear.Trait> result = EnumSet.noneOf(MagicGear.Trait.class);
        for (String key : traits) try { result.add(MagicGear.Trait.valueOf(key.toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) {}
        return result;
    }

    public List<GearRule> ruleList() {
        List<GearRule> result = new ArrayList<>();
        for (String json : rules) try { result.add(GearRule.parse(json)); } catch (RuntimeException ignored) {}
        return result;
    }

    public List<CharmItem> charmList() {
        List<CharmItem> result = new ArrayList<>();
        for (String id : charms) { CharmItem charm = CharmItem.byId(id); if (charm != null) result.add(charm); }
        return result;
    }

    public void collect(int[] deltas, Set<MagicGear.Trait> traitsOut, List<GearRule> rulesOut) {
        statMap().forEach((stat, delta) -> deltas[stat.ordinal()] += delta);
        traitsOut.addAll(traitSet());
        rulesOut.addAll(ruleList());
        for (CharmItem charm : charmList()) {
            charm.stats().forEach((stat, delta) -> deltas[stat.ordinal()] += delta);
            traitsOut.addAll(charm.traits());
            rulesOut.addAll(charm.rules());
        }
    }

    public GearBonus withCharm(String id) {
        List<String> next = new ArrayList<>(charms);
        next.add(id);
        return new GearBonus(stats, traits, rules, List.copyOf(next));
    }

    public GearBonus withoutCharms() { return new GearBonus(stats, traits, rules, List.of()); }

    public boolean isEmpty() { return stats.isEmpty() && traits.isEmpty() && rules.isEmpty() && charms.isEmpty(); }
}
