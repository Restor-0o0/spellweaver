package com.restor.magicka;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.Set;
import java.util.Optional;

public class SpellScrollLoot extends LootModifier {
    public static final MapCodec<SpellScrollLoot> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).and(i.group(
            Codec.doubleRange(0, 1).fieldOf("chance").forGetter(m -> m.chance),
            Codec.intRange(0, 100).optionalFieldOf("ultimate_weight", 1).forGetter(m -> m.ultimateWeight),
            Codec.intRange(0, 100).optionalFieldOf("weight", 4).forGetter(m -> m.weight))).apply(i, SpellScrollLoot::new));
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Magicka.ID);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<SpellScrollLoot>> TYPE = MODIFIERS.register("spell_scrolls", () -> CODEC);

    private final double chance;
    private final int ultimateWeight, weight;

    public SpellScrollLoot(Optional<Holder<LootItemCondition>> condition, int priority, double chance, int ultimateWeight, int weight) {
        super(condition, priority);
        this.chance = chance;
        this.ultimateWeight = ultimateWeight;
        this.weight = weight;
    }

    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!context.getQueriedLootTableId().getPath().startsWith("chests/") || context.getRandom().nextDouble() >= chance) return loot;
        SpellDefinition spell = ScrollDrops.pick(context.getRandom(), Set.of(), ultimateWeight, weight);
        if (spell != null) loot.add(SpellScrollItem.of(spell));
        return loot;
    }

    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return TYPE.get(); }
}
