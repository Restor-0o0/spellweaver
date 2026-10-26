package com.restor.magicka;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Set;

public class SpellScrollLoot extends LootModifier {
    public static final Codec<SpellScrollLoot> CODEC = RecordCodecBuilder.create(i -> codecStart(i)
            .and(Codec.doubleRange(0, 1).fieldOf("chance").forGetter(m -> m.chance))
            .and(Codec.intRange(0, 100).optionalFieldOf("ultimate_weight", 1).forGetter(m -> m.ultimateWeight))
            .and(Codec.intRange(0, 100).optionalFieldOf("weight", 4).forGetter(m -> m.weight)).apply(i, SpellScrollLoot::new));
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> MODIFIERS = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Magicka.ID);
    public static final RegistryObject<Codec<SpellScrollLoot>> TYPE = MODIFIERS.register("spell_scrolls", () -> CODEC);

    private final double chance;
    private final int ultimateWeight, weight;

    public SpellScrollLoot(LootItemCondition[] conditions, double chance, int ultimateWeight, int weight) {
        super(conditions);
        this.chance = chance;
        this.ultimateWeight = ultimateWeight;
        this.weight = weight;
    }

    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!chest(context.getQueriedLootTableId().getPath()) || context.getRandom().nextDouble() >= chance * MagicConfig.CHEST_SCROLLS.get()) return loot;
        SpellDefinition spell = ScrollDrops.pick(context.getRandom(), Set.of(), ultimateWeight, weight, context.getLevel());
        if (spell != null) loot.add(SpellScrollItem.of(spell));
        return loot;
    }

    public static boolean chest(String path) {
        if (path.startsWith("blocks/") || path.startsWith("entities/") || path.startsWith("gameplay/") || path.startsWith("archaeology/")) return false;
        for (String part : path.split("/")) if (part.startsWith("chest") || part.endsWith("_chest") || part.endsWith("_chests")) return true;
        return false;
    }

    @Override public Codec<? extends IGlobalLootModifier> codec() { return TYPE.get(); }
}
