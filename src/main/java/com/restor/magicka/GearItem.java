package com.restor.magicka;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.*;

public class GearItem extends Item {
    public enum Kind { FOCUS, ROBE, RING, TRINKET, TOOL }
    public enum Grade { COMMON, UNCOMMON, RARE, LEGENDARY, WEIRD }
    public record Attr(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount) {}
    public interface Action { void run(net.minecraft.server.level.ServerPlayer player); }

    public final String id;
    public final Kind kind;
    public final Grade grade;
    public final boolean named;
    private final Map<MagicStats.Stat, Integer> stats;
    private final Set<MagicGear.Trait> traits;
    private final Map<SpellFormula, JsonObject> patches;
    private final List<GearRule> rules;
    private final List<Attr> attributes;
    private final double[] castBias;
    private final Action action;
    private final int cooldown;

    private GearItem(Builder builder) {
        super(new Properties().stacksTo(1).rarity(builder.grade == Grade.LEGENDARY ? net.minecraft.world.item.Rarity.EPIC
                : builder.grade == Grade.RARE ? net.minecraft.world.item.Rarity.RARE
                : builder.grade == Grade.UNCOMMON ? net.minecraft.world.item.Rarity.UNCOMMON : net.minecraft.world.item.Rarity.COMMON));
        this.id = builder.id;
        this.kind = builder.kind;
        this.grade = builder.grade;
        this.named = builder.named;
        this.stats = Map.copyOf(builder.stats);
        this.traits = Set.copyOf(builder.traits);
        this.patches = Map.copyOf(builder.patches);
        this.rules = List.copyOf(builder.rules);
        this.attributes = List.copyOf(builder.attributes);
        this.castBias = builder.castBias;
        this.action = builder.action;
        this.cooldown = builder.cooldown;
    }

    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (action == null) return net.minecraft.world.InteractionResultHolder.pass(stack);
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            action.run(server);
            player.getCooldowns().addCooldown(this, cooldown);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public Map<MagicStats.Stat, Integer> stats() { return stats; }
    public Set<MagicGear.Trait> traits() { return traits; }
    public Map<SpellFormula, JsonObject> patches() { return patches; }
    public List<GearRule> rules() { return rules; }
    public List<Attr> attributes() { return attributes; }
    public double castBias(int size) { return castBias == null || size < 1 || size > 5 ? 1 : castBias[size - 1]; }

    @Override public Component getName(ItemStack stack) {
        Component name = Component.translatable("gear.magicka." + id);
        return named ? name.copy().withStyle(ChatFormatting.GOLD) : name;
    }

    @Override public boolean canGrindstoneRepair(ItemStack stack) { return !GearBonus.of(stack).charms().isEmpty(); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("gear.magicka.kind." + kind.name().toLowerCase(Locale.ROOT))
                .append(" · ").append(Component.translatable("gear.magicka.grade." + grade.name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.DARK_GRAY));
        if (named) lines.add(Component.translatable("gear.magicka.named").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        GearBonus bonus = GearBonus.of(stack);
        Map<MagicStats.Stat, Integer> total = new EnumMap<>(MagicStats.Stat.class);
        total.putAll(stats);
        bonus.statMap().forEach((stat, delta) -> total.merge(stat, delta, Integer::sum));
        total.entrySet().stream().filter(e -> e.getValue() != 0).sorted(Comparator.comparingInt(e -> e.getKey().ordinal())).forEach(e -> lines.add(
                Component.literal(e.getValue() > 0 ? "+" + e.getValue() + "% " : e.getValue() + "% ")
                        .append(Component.translatable("book.magicka.stat." + e.getKey().key()))
                        .withStyle(e.getValue() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
        for (Attr attr : attributes) lines.add(Component.literal(format(attr) + " ")
                .append(Component.translatable(attr.attribute().value().getDescriptionId()))
                .withStyle(attr.amount() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        Set<MagicGear.Trait> allTraits = EnumSet.noneOf(MagicGear.Trait.class);
        allTraits.addAll(traits);
        allTraits.addAll(bonus.traitSet());
        allTraits.stream().sorted().forEach(trait -> lines.add(Component.translatable("gear.magicka.trait." + trait.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.AQUA)));
        for (SpellFormula formula : patches.keySet().stream().sorted(Comparator.comparingInt(SpellFormula::size)).toList())
            lines.add(Component.translatable("gear.magicka.replaces", RecipeRegistry.clientResolve(formula).displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!rules.isEmpty()) lines.add(Component.translatable("gear.magicka." + id + ".effect").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!bonus.rules().isEmpty()) lines.add(Component.translatable("gear.magicka.extra_rules", bonus.rules().size()).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (castBias != null) lines.add(Component.translatable("gear.magicka." + id + ".bias").withStyle(ChatFormatting.GRAY));
        if (action != null) lines.add(Component.translatable("gear.magicka.active", cooldown / 20).withStyle(ChatFormatting.YELLOW));
        lines.add(Component.translatable("gear.magicka." + id + ".desc").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String format(Attr attr) {
        double amount = attr.operation() == AttributeModifier.Operation.ADD_VALUE ? attr.amount() : attr.amount() * 100;
        String suffix = attr.operation() == AttributeModifier.Operation.ADD_VALUE ? "" : "%";
        return (amount > 0 ? "+" : "") + (amount == Math.floor(amount) ? Long.toString((long) amount) : String.format(Locale.ROOT, "%.1f", amount)) + suffix;
    }

    public static Builder of(String id, Kind kind, Grade grade) { return new Builder(id, kind, grade); }

    public static final class Builder {
        private final String id; private final Kind kind; private final Grade grade;
        private final Map<MagicStats.Stat, Integer> stats = new EnumMap<>(MagicStats.Stat.class);
        private final Set<MagicGear.Trait> traits = EnumSet.noneOf(MagicGear.Trait.class);
        private final Map<SpellFormula, JsonObject> patches = new HashMap<>();
        private final List<Attr> attributes = new ArrayList<>();
        private final List<GearRule> rules = new ArrayList<>();
        private double[] castBias;
        private Action action;
        private int cooldown = 40;
        private boolean named;
        private Builder(String id, Kind kind, Grade grade) { this.id = id; this.kind = kind; this.grade = grade; }
        public Builder action(int cooldown, Action action) { this.cooldown = cooldown; this.action = action; return this; }
        public Builder stat(MagicStats.Stat stat, int delta) { stats.merge(stat, delta, Integer::sum); return this; }
        public Builder trait(MagicGear.Trait trait) { traits.add(trait); return this; }
        public Builder patch(SpellFormula formula, JsonObject patch) { patches.put(formula, patch); return this; }
        public Builder rule(String json) { rules.add(GearRule.parse(json)); return this; }
        public Builder named() { named = true; return this; }
        public Builder attribute(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount) { attributes.add(new Attr(attribute, operation, amount)); return this; }
        public Builder bias(double one, double two, double three, double four, double five) { castBias = new double[]{one, two, three, four, five}; return this; }
        public GearItem build() { return new GearItem(this); }
    }
}
