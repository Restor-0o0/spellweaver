package com.restor.magicka;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.*;

public class CharmItem extends Item {
    private static final Map<String, CharmItem> BY_ID = new HashMap<>();

    public final String id;
    private final Map<MagicStats.Stat, Integer> stats;
    private final Set<MagicGear.Trait> traits;
    private final List<GearRule> rules;

    private CharmItem(Builder builder) {
        super(new Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(Magicka.ID, builder.id))).stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON));
        this.id = builder.id;
        this.stats = Map.copyOf(builder.stats);
        this.traits = Set.copyOf(builder.traits);
        this.rules = List.copyOf(builder.rules);
        BY_ID.put(id, this);
    }

    public static CharmItem byId(String id) { return BY_ID.get(id); }

    public Map<MagicStats.Stat, Integer> stats() { return stats; }
    public Set<MagicGear.Trait> traits() { return traits; }
    public List<GearRule> rules() { return rules; }

    public static int sockets(ItemStack stack) {
        if (stack.getItem() instanceof GearItem gear) return switch (gear.grade) {
            case COMMON -> 1;
            case UNCOMMON, WEIRD -> 2;
            case RARE, LEGENDARY -> 3;
        };
        return stack.getItem() instanceof CharmItem || !stack.isDamageableItem() ? 0 : 1;
    }

    public static ItemStack socket(ItemStack base, ItemStack charm) {
        if (!(charm.getItem() instanceof CharmItem item) || base.isEmpty() || base.getCount() != 1) return ItemStack.EMPTY;
        GearBonus bonus = GearBonus.of(base);
        if (bonus.charms().contains(item.id) || bonus.charms().size() >= sockets(base)) return ItemStack.EMPTY;
        ItemStack result = base.copy();
        result.set(GearBonus.TYPE.get(), bonus.withCharm(item.id));
        return result;
    }

    public static ItemStack strip(ItemStack base) {
        GearBonus bonus = GearBonus.of(base);
        if (bonus.charms().isEmpty()) return ItemStack.EMPTY;
        ItemStack result = base.copy();
        GearBonus rest = bonus.withoutCharms();
        if (rest.isEmpty()) result.remove(GearBonus.TYPE.get());
        else result.set(GearBonus.TYPE.get(), rest);
        return result;
    }

    public static void describe(ItemStack stack, java.util.function.Consumer<Component> lines) {
        List<CharmItem> charms = GearBonus.of(stack).charmList();
        if (charms.isEmpty()) return;
        lines.accept(Component.translatable("charm.magicka.sockets", charms.size(), sockets(stack)).withStyle(ChatFormatting.DARK_PURPLE));
        for (CharmItem charm : charms) lines.accept(Component.literal(" ◆ ").append(Component.translatable("item.magicka." + charm.id))
                .append(Component.literal(": ")).append(Component.translatable("charm.magicka." + charm.id + ".effect")).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("charm.magicka." + id + ".effect").withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.accept(Component.translatable("charm.magicka.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static Builder of(String id) { return new Builder(id); }

    public static final class Builder {
        private final String id;
        private final Map<MagicStats.Stat, Integer> stats = new EnumMap<>(MagicStats.Stat.class);
        private final Set<MagicGear.Trait> traits = EnumSet.noneOf(MagicGear.Trait.class);
        private final List<GearRule> rules = new ArrayList<>();
        private Builder(String id) { this.id = id; }
        public Builder stat(MagicStats.Stat stat, int delta) { stats.merge(stat, delta, Integer::sum); return this; }
        public Builder trait(MagicGear.Trait trait) { traits.add(trait); return this; }
        public Builder rule(String json) { rules.add(GearRule.parse(json)); return this; }
        public CharmItem build() { return new CharmItem(this); }
    }
}
