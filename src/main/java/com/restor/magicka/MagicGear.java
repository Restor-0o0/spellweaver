package com.restor.magicka;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

public final class MagicGear {
    public enum Trait {
        SHOCK_WARD, FROST_WARD, LIVING_REGEN, FIRE_FRAIL, STONEHEART, GLASS_HEART, BLOOD_FOCUS,
        GRAVE_BELL, MIRROR_PRISM, SECOND_TONGUE, CHAOS_LENS, SELF_CONDUCTIVE, QUICKENING, DESPERATE_MENDING,
        SHARED_BLESSING, RESONANCE, ADAPTATION
    }

    public enum Slot {
        FOCUS(GearItem.Kind.FOCUS), ROBE(GearItem.Kind.ROBE),
        RING_1(GearItem.Kind.RING), RING_2(GearItem.Kind.RING),
        TRINKET_1(GearItem.Kind.TRINKET), TRINKET_2(GearItem.Kind.TRINKET), TRINKET_3(GearItem.Kind.TRINKET),
        TOOL(GearItem.Kind.TOOL);
        public final GearItem.Kind kind;
        Slot(GearItem.Kind kind) { this.kind = kind; }
        public String key() { return name().toLowerCase(Locale.ROOT); }
        public boolean accepts(ItemStack stack) {
            if (!(stack.getItem() instanceof GearItem gear)) return false;
            return gear.kind == kind || (kind == GearItem.Kind.TRINKET && gear.kind == GearItem.Kind.RING);
        }
    }

    public static final int SLOTS = Slot.values().length, BELT = 6, MENU_SIZE = SLOTS + BELT;
    private static final String TAG = "magicka_gear";
    private static final List<Holder<Attribute>> MANAGED = List.of(Attributes.MAX_HEALTH, Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE, Attributes.MOVEMENT_SPEED);

    private record Profile(int revision, List<GearItem> items, int[] deltas, Set<Trait> traits,
                           Map<SpellFormula, JsonObject> patches, List<GearRule> rules, double[] bias,
                           Map<Element, Element> swaps, Set<Element> infusions) {}
    private record Adaptation(Element element, long until) {}
    public static final int ADAPT_TICKS = 160, ADAPT_BONUS = 30;
    private static final Map<UUID, Profile> PROFILES = new HashMap<>();
    private static final Map<UUID, Integer> REVISIONS = new HashMap<>();
    private static final Map<UUID, Long> ECHOES = new HashMap<>();
    private static final Set<UUID> QUICKENED = new HashSet<>();
    private static final Map<UUID, Integer> RESONANCE = new HashMap<>();
    private static final Map<UUID, Adaptation> ADAPTED = new HashMap<>();

    public static final class Loadout extends SimpleContainer {
        private final Player owner;
        private boolean loading = true;
        public Loadout(Player owner) {
            super(MENU_SIZE);
            this.owner = owner;
            CompoundTag tag = owner.getPersistentData().getCompoundOrEmpty(TAG);
            for (Slot slot : Slot.values()) if (tag.contains(slot.key()))
                setItem(slot.ordinal(), parse(owner, tag.getCompoundOrEmpty(slot.key())));
            for (int i = 0; i < BELT; i++) if (tag.contains(beltKey(i)))
                setItem(SLOTS + i, parse(owner, tag.getCompoundOrEmpty(beltKey(i))));
            loading = false;
        }
        @Override public void setChanged() {
            super.setChanged();
            if (!loading) store(owner, this);
        }
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, holder) -> new GearMenu(id, inventory, new Loadout(holder)),
                Component.translatable("screen.magicka.loadout")));
    }

    private static void store(Player player, Container loadout) {
        CompoundTag tag = new CompoundTag();
        for (Slot slot : Slot.values()) {
            ItemStack stack = loadout.getItem(slot.ordinal());
            if (!stack.isEmpty()) tag.put(slot.key(), encode(player, stack));
        }
        for (int i = 0; i < BELT; i++) {
            ItemStack stack = loadout.getItem(SLOTS + i);
            if (!stack.isEmpty()) tag.put(beltKey(i), encode(player, stack));
        }
        player.getPersistentData().put(TAG, tag);
        if (player instanceof ServerPlayer server) {
            invalidate(server);
            PotionBelt.sync(server);
        }
    }

    private static String beltKey(int index) { return "belt_" + index; }

    private static net.minecraft.nbt.Tag encode(Player player, ItemStack stack) {
        return ItemStack.OPTIONAL_CODEC.encodeStart(player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack).getOrThrow();
    }

    public static ItemStack belt(Player player, int index) {
        CompoundTag tag = player.getPersistentData().getCompoundOrEmpty(TAG);
        return index >= 0 && index < BELT && tag.contains(beltKey(index)) ? parse(player, tag.getCompoundOrEmpty(beltKey(index))) : ItemStack.EMPTY;
    }

    public static void setBelt(ServerPlayer player, int index, ItemStack stack) {
        CompoundTag tag = player.getPersistentData().getCompoundOrEmpty(TAG);
        if (stack.isEmpty()) tag.remove(beltKey(index)); else tag.put(beltKey(index), encode(player, stack));
        player.getPersistentData().put(TAG, tag);
        PotionBelt.sync(player);
    }

    private static ItemStack parse(Player player, CompoundTag tag) {
        return ItemStack.OPTIONAL_CODEC.parse(player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag).result().orElse(ItemStack.EMPTY);
    }

    public static ItemStack item(Player player, Slot slot) {
        CompoundTag tag = player.getPersistentData().getCompoundOrEmpty(TAG);
        return tag.contains(slot.key())
                ? parse(player, tag.getCompoundOrEmpty(slot.key()))
                : ItemStack.EMPTY;
    }

    public static int revision(Player player) { return REVISIONS.getOrDefault(player.getUUID(), 0); }

    public static void invalidate(ServerPlayer player) {
        REVISIONS.merge(player.getUUID(), 1, Integer::sum);
        PROFILES.remove(player.getUUID());
        attributes(player);
        MagicStats.invalidate(player);
    }

    private static Profile profile(Player player) {
        Profile cached = PROFILES.get(player.getUUID());
        int revision = revision(player);
        if (cached != null && cached.revision == revision) return cached;
        List<GearItem> items = new ArrayList<>();
        int[] deltas = new int[MagicStats.COUNT];
        Set<Trait> traits = EnumSet.noneOf(Trait.class);
        List<GearRule> rules = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            ItemStack stack = item(player, slot);
            if (!(stack.getItem() instanceof GearItem gear)) continue;
            items.add(gear);
            rules.addAll(gear.rules());
            GearBonus.of(stack).collect(deltas, traits, rules);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof GearItem)) GearBonus.of(stack).collect(deltas, traits, rules);
        }
        Map<SpellFormula, JsonObject> patches = new HashMap<>();
        double[] bias = {1, 1, 1, 1, 1};
        Map<Element, Element> swaps = new EnumMap<>(Element.class);
        Set<Element> infusions = EnumSet.noneOf(Element.class);
        for (GearItem gear : items) {
            swaps.putAll(gear.swaps());
            infusions.addAll(gear.infusions());
            gear.stats().forEach((stat, delta) -> deltas[stat.ordinal()] += delta);
            traits.addAll(gear.traits());
            gear.patches().forEach((formula, patch) -> merge(patches.computeIfAbsent(formula, key -> new JsonObject()), patch));
            for (int size = 1; size <= 5; size++) bias[size - 1] *= gear.castBias(size);
        }
        Profile built = new Profile(revision, List.copyOf(items), deltas, traits, patches, List.copyOf(rules), bias, swaps, infusions);
        PROFILES.put(player.getUUID(), built);
        return built;
    }

    public static List<GearItem> equipped(Player player) { return profile(player).items; }
    public static int[] deltas(Player player) {
        int[] deltas = profile(player).deltas.clone();
        Element adapted = adapted(player);
        if (adapted != null) deltas[MagicStats.Stat.valueOf(adapted.name()).ordinal()] += ADAPT_BONUS;
        MagicPotions.bonus(player, deltas);
        MageLevel.bonus(player, deltas);
        return deltas;
    }
    public static Element adapted(Player player) {
        Adaptation adaptation = ADAPTED.get(player.getUUID());
        return adaptation != null && adaptation.until > player.level().getGameTime() ? adaptation.element : null;
    }
    public static boolean has(Player player, Trait trait) { return profile(player).traits.contains(trait); }
    public static double castBias(Player player, int size) {
        double quick = QUICKENED.contains(player.getUUID()) ? .15 : 1;
        return size < 1 || size > 5 ? quick : profile(player).bias[size - 1] * quick;
    }
    public static boolean quickened(Player player) { return QUICKENED.contains(player.getUUID()); }

    public static int[] total(Player player) {
        int[] values = MagicStats.get(player), deltas = deltas(player);
        for (MagicStats.Stat stat : MagicStats.Stat.values())
            values[stat.ordinal()] = stat.clamp(values[stat.ordinal()] + deltas[stat.ordinal()]);
        return values;
    }

    public static SpellFormula transmute(Player player, SpellFormula formula) {
        Profile profile = profile(player);
        if (profile.swaps.isEmpty() && profile.infusions.isEmpty() || formula.size() == 0) return formula;
        SpellDefinition base = RecipeRegistry.resolve(formula);
        if (base == null || base.ultimate()) return formula;
        int[] counts = new int[Element.values().length];
        for (Element element : Element.values()) counts[profile.swaps.getOrDefault(element, element).ordinal()] += formula.count(element);
        for (Element element : profile.infusions)
            if (counts[element.ordinal()] == 0 && Arrays.stream(counts).sum() < 5) counts[element.ordinal()]++;
        return new SpellFormula(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6], counts[7]);
    }

    public static void frail(LivingEntity mob, double health) {
        if (health == 1) return;
        AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);
        if (max == null) return;
        max.addOrReplacePermanentModifier(new AttributeModifier(Identifier.fromNamespaceAndPath(Magicka.ID, "gear/summon_health"),
                Math.clamp(health, .1, 10) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        mob.setHealth(mob.getMaxHealth());
    }

    public static SpellDefinition patch(Player player, SpellDefinition base) {
        Profile profile = profile(player);
        JsonObject patch = profile.patches.get(base.formula());
        List<GearRule> rules = profile.rules.stream().filter(rule -> rule.matches(base)).toList();
        if (patch == null && rules.isEmpty()) return base;
        try {
            JsonObject json = base.source().deepCopy();
            if (patch != null) merge(json, patch);
            for (GearRule rule : rules) rule.apply(json, base);
            return SpellDefinition.parse(base.id(), json);
        } catch (RuntimeException e) {
            return base;
        }
    }

    static void merge(JsonObject target, JsonObject patch) {
        for (Map.Entry<String, JsonElement> field : patch.entrySet()) {
            JsonElement value = field.getValue();
            if (value.isJsonNull()) target.remove(field.getKey());
            else if (value.isJsonObject() && target.has(field.getKey()) && target.get(field.getKey()).isJsonObject())
                merge(target.getAsJsonObject(field.getKey()), value.getAsJsonObject());
            else target.add(field.getKey(), value.deepCopy());
        }
    }

    public static void cast(ServerPlayer player, SpellDefinition recipe, boolean self) {
        if (recipe == null) return;
        if (QUICKENED.remove(player.getUUID())) MagicStats.invalidate(player);
        Profile profile = profile(player);
        if (profile.traits.contains(Trait.SELF_CONDUCTIVE) && recipe.formula().lightning() > 0)
            MagicStatuses.charge(player, .12 * recipe.formula().lightning());
        if (profile.traits.contains(Trait.CHAOS_LENS) && recipe.formula().size() >= 4 && player.getRandom().nextFloat() < .25f)
            MagicStatuses.apply(player, MagicStatuses.Status.ARCANE_UNSTABLE, 100, 1);
        if (self && profile.traits.contains(Trait.SHARED_BLESSING)) share(player, recipe);
        if (recipe.shape().construction() || recipe.ultimate()) return;
        if (profile.traits.contains(Trait.RESONANCE) && RESONANCE.merge(player.getUUID(), 1, Integer::sum) % 5 == 0) {
            echo(player, recipe, self);
            return;
        }
        if (!profile.traits.contains(Trait.MIRROR_PRISM)) return;
        long now = player.level().getGameTime();
        if (now - ECHOES.getOrDefault(player.getUUID(), -1000L) < 100) return;
        ECHOES.put(player.getUUID(), now);
        echo(player, recipe, self);
    }

    private static void echo(ServerPlayer player, SpellDefinition recipe, boolean self) {
        SpellDefinition echo = weaken(recipe);
        SpellRuntime.delay(player, 25, () -> { if (player.isAlive()) SpellRuntime.cast(player, echo, self); });
    }

    public static int share(ServerPlayer player, SpellDefinition recipe) {
        if (recipe.selfEffects().isEmpty()) return 0;
        int shared = 0;
        for (LivingEntity ally : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3),
                e -> e != player && e.isAlive() && EffectGraph.ally(player, e))) {
            EffectGraph.entity(player, ally, recipe, recipe.selfEffects(), player.position(), true);
            shared++;
        }
        return shared;
    }

    private static SpellDefinition weaken(SpellDefinition recipe) {
        int[] values = MagicStats.defaults();
        for (MagicStats.Stat stat : List.of(MagicStats.Stat.DAMAGE, MagicStats.Stat.HEALING, MagicStats.Stat.SUMMONS, MagicStats.Stat.FORCE))
            values[stat.ordinal()] = 50;
        try { return MagicStats.scale(recipe, values); } catch (RuntimeException e) { return recipe; }
    }

    public static float heal(ServerPlayer caster, LivingEntity target, float amount) {
        Profile profile = profile(caster);
        if (profile.traits.contains(Trait.DESPERATE_MENDING) && caster.getHealth() < caster.getMaxHealth() * .4f) amount *= 2;
        if (target == caster) return profile.traits.contains(Trait.GLASS_HEART) ? amount * 1.5f : amount;
        if (!profile.traits.contains(Trait.BLOOD_FOCUS)) return amount;
        caster.hurt(caster.damageSources().magic(), amount * .3f);
        return amount * 1.6f;
    }

    public static void status(ServerPlayer caster, LivingEntity target, String status) {
        if (target == caster && status.equals("stone_shell") && has(caster, Trait.STONEHEART))
            caster.setAbsorptionAmount(Math.min(20, caster.getAbsorptionAmount() + 4));
    }

    public static boolean blocked(ServerPlayer caster, LivingEntity target, String type, double amount) {
        if (target != caster) return false;
        return switch (type) {
            case "add_charge" -> has(caster, Trait.SHOCK_WARD);
            case "modify_temperature" -> amount < 0 && has(caster, Trait.FROST_WARD);
            default -> false;
        };
    }

    private static void attributes(ServerPlayer player) {
        Map<Holder<Attribute>, Map<AttributeModifier.Operation, Double>> sums = new HashMap<>();
        for (GearItem gear : profile(player).items) for (GearItem.Attr attr : gear.attributes())
            sums.computeIfAbsent(attr.attribute(), key -> new EnumMap<>(AttributeModifier.Operation.class))
                    .merge(attr.operation(), attr.amount(), Double::sum);
        for (Holder<Attribute> holder : MANAGED) {
            AttributeInstance instance = player.getAttribute(holder);
            if (instance == null) continue;
            for (AttributeModifier.Operation operation : AttributeModifier.Operation.values()) {
                Identifier id = modifier(holder, operation);
                instance.removeModifier(id);
                double amount = sums.getOrDefault(holder, Map.of()).getOrDefault(operation, 0.0);
                if (amount != 0) instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
            }
        }
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    private static Identifier modifier(Holder<Attribute> holder, AttributeModifier.Operation operation) {
        Identifier key = BuiltInRegistries.ATTRIBUTE.getKey(holder.value());
        return Identifier.fromNamespaceAndPath(Magicka.ID,
                "gear/" + (key == null ? "unknown" : key.getPath()) + "/" + operation.name().toLowerCase(Locale.ROOT));
    }

    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 60 != 0) return;
        Adaptation adaptation = ADAPTED.get(player.getUUID());
        if (adaptation != null && adaptation.until <= player.level().getGameTime()) {
            ADAPTED.remove(player.getUUID());
            MagicStats.invalidate(player);
        }
        if (player.isAlive() && has(player, Trait.LIVING_REGEN))
            MagicStatuses.apply(player, MagicStatuses.Status.REGENERATING, 80, 1);
    }

    @SubscribeEvent public void hurt(LivingIncomingDamageEvent event) {
        event.setAmount(MagicPotions.ward(event.getEntity(), element(event.getSource()), event.getAmount()));
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE) && has(player, Trait.FIRE_FRAIL))
            event.setAmount(event.getAmount() * 1.5f);
        event.setAmount(adapt(player, element(event.getSource()), event.getAmount()));
    }

    public static float adapt(ServerPlayer player, Element element, float amount) {
        if (element == null || !has(player, Trait.ADAPTATION)) return amount;
        Element previous = adapted(player);
        ADAPTED.put(player.getUUID(), new Adaptation(element, player.level().getGameTime() + ADAPT_TICKS));
        if (previous != element) MagicStats.invalidate(player);
        return previous == element ? amount * .7f : amount;
    }

    static Element element(net.minecraft.world.damagesource.DamageSource source) {
        SpellDefinition spell = com.restor.magicka.enemy.MagicEnemies.hurting;
        if (spell != null && spell.formula().size() > 0) {
            Element lead = null;
            for (Element element : Element.values())
                if (lead == null || spell.formula().count(element) > spell.formula().count(lead)) lead = element;
            return lead;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) return Element.FIRE;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) return Element.COLD;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) return Element.LIGHTNING;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)) return Element.WATER;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.WITHER)) return Element.DEATH;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC) || source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)) return Element.ARCANE;
        return null;
    }

    @SubscribeEvent public void kill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity() == player || !has(player, Trait.QUICKENING)) return;
        if (QUICKENED.add(player.getUUID())) MagicStats.invalidate(player);
    }

    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) invalidate(p); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer p) invalidate(p); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        PROFILES.remove(event.getEntity().getUUID());
        ECHOES.remove(event.getEntity().getUUID());
        QUICKENED.remove(event.getEntity().getUUID());
        RESONANCE.remove(event.getEntity().getUUID());
        ADAPTED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public void equip(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && (!GearBonus.of(event.getFrom()).charms().isEmpty() || !GearBonus.of(event.getTo()).charms().isEmpty())) invalidate(player);
    }

    @SubscribeEvent public void anvil(AnvilUpdateEvent event) {
        ItemStack result = CharmItem.socket(event.getLeft(), event.getRight());
        if (result.isEmpty()) return;
        event.setOutput(result);
        event.setXpCost(3 + 2 * GearBonus.of(event.getLeft()).charms().size());
        event.setMaterialCost(1);
    }

    @SubscribeEvent public void grindstone(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem(), bottom = event.getBottomItem();
        if (!top.isEmpty() && !bottom.isEmpty()) return;
        ItemStack result = CharmItem.strip(top.isEmpty() ? bottom : top);
        if (result.isEmpty()) return;
        event.setOutput(result);
        event.setXp(0);
    }
    @SubscribeEvent public void clone(PlayerEvent.Clone event) {
        CompoundTag tag = event.getOriginal().getPersistentData().getCompoundOrEmpty(TAG);
        if (!tag.isEmpty()) event.getEntity().getPersistentData().put(TAG, tag.copy());
        PROFILES.remove(event.getEntity().getUUID());
        REVISIONS.merge(event.getEntity().getUUID(), 1, Integer::sum);
    }
    public MagicGear() {}
}
