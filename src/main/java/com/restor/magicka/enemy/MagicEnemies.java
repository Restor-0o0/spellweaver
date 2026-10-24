package com.restor.magicka.enemy;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.restor.magicka.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;
import java.util.function.Supplier;

public final class MagicEnemies {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Magicka.ID);
    public static final List<DeferredItem<Item>> EGGS = new ArrayList<>();
    private static final Map<DeferredHolder<EntityType<?>, ? extends EntityType<? extends SpecialEnemy>>, Supplier<AttributeSupplier.Builder>> ATTRIBUTES = new LinkedHashMap<>();

    public static final DeferredHolder<EntityType<?>, EntityType<EnemyBolt>> BOLT = TYPES.register("enemy_bolt", () -> EntityType.Builder.<EnemyBolt>of(EnemyBolt::new, MobCategory.MISC)
            .sized(.3125f, .3125f).clientTrackingRange(4).updateInterval(10).build(key("enemy_bolt")));
    public static final DeferredHolder<EntityType<?>, EntityType<CaveSkitter>> CAVE_SKITTER = mob("cave_skitter", CaveSkitter::new, .9f, .5f, 0x4A4038, 0x8E8270, CaveSkitter::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<ShieldBeetle>> SHIELD_BEETLE = mob("shield_beetle", ShieldBeetle::new, 1.3f, 1.1f, 0x4A5238, 0x8C6E44, ShieldBeetle::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<StoneCrawler>> STONE_CRAWLER = mob("stone_crawler", StoneCrawler::new, 1.4f, 1.5f, 0x7D7F83, 0x4F6A32, StoneCrawler::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<MushroomNurse>> MUSHROOM_NURSE = mob("mushroom_nurse", MushroomNurse::new, .8f, 1.7f, 0x8A3F2A, 0xE8DCC4, MushroomNurse::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<ElementBinder>> ELEMENT_BINDER = mob("element_binder", ElementBinder::new, .6f, 1.4f, 0x6E8B3D, 0x4E3A5E, ElementBinder::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<Burrower>> BURROWER = mob("burrower", Burrower::new, 1f, .6f, 0x3A3438, 0xD89A8E, Burrower::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<CaveWisp>> CAVE_WISP = mob("cave_wisp", CaveWisp::new, .9f, .6f, 0x9A8A70, 0x6E6250, CaveWisp::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<StoneEater>> STONE_EATER = mob("stone_eater", StoneEater::new, 1.2f, 1.2f, 0x6E6A64, 0x7E6A9A, StoneEater::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<FloodCaster>> FLOOD_CASTER = mob("flood_caster", FloodCaster::new, .9f, .9f, 0x5A6E3A, 0xC8C090, FloodCaster::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<Countermage>> COUNTERMAGE = mob("countermage", Countermage::new, .6f, 1.45f, 0x6A7A48, 0x9A3A2A, Countermage::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<SparkImp>> SPARK_IMP = mob("spark_imp", SparkImp::new, .7f, .8f, 0x4A4048, 0x5A4A50, SparkImp::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinWarrior>> GOBLIN_WARRIOR = mob("goblin_warrior", GoblinWarrior::new, .6f, 1.4f, 0x6E8B3D, 0x6B4A2E, GoblinWarrior::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinArcher>> GOBLIN_ARCHER = mob("goblin_archer", GoblinArcher::new, .6f, 1.4f, 0x6E8B3D, 0x4F6B3A, GoblinArcher::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinMage>> GOBLIN_MAGE = mob("goblin_mage", GoblinMage::new, .6f, 1.4f, 0x6E8B3D, 0x7A2E2A, GoblinMage::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinMage.Frost>> GOBLIN_FROST_MAGE = mob("goblin_frost_mage", GoblinMage.Frost::new, .6f, 1.4f, 0x6E8B3D, 0x9CC4D8, GoblinMage::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinMage.Storm>> GOBLIN_STORM_MAGE = mob("goblin_storm_mage", GoblinMage.Storm::new, .6f, 1.4f, 0x6E8B3D, 0x3A4868, GoblinMage::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinBomber>> GOBLIN_BOMBER = mob("goblin_bomber", GoblinBomber::new, .6f, 1.4f, 0x6E8B3D, 0x9A7A4A, GoblinBomber::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinShaman>> GOBLIN_SHAMAN = mob("goblin_shaman", GoblinShaman::new, .6f, 1.4f, 0x6E8B3D, 0xDDD3BC, GoblinShaman::attributes);
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinBomb>> GOBLIN_BOMB = TYPES.register("goblin_bomb", () -> EntityType.Builder.<GoblinBomb>of(GoblinBomb::new, MobCategory.MISC)
            .sized(.4f, .4f).clientTrackingRange(6).updateInterval(5).build(key("goblin_bomb")));

    private static <T extends SpecialEnemy> DeferredHolder<EntityType<?>, EntityType<T>> mob(String id, EntityType.EntityFactory<T> factory, float width, float height, int base, int spots, Supplier<AttributeSupplier.Builder> attributes) {
        DeferredHolder<EntityType<?>, EntityType<T>> holder = TYPES.register(id, () -> EntityType.Builder.of(factory, MobCategory.MONSTER).sized(width, height).clientTrackingRange(10).build(key(id)));
        EGGS.add(MagicItems.ITEMS.registerItem(id + "_spawn_egg", net.minecraft.world.item.SpawnEggItem::new, p -> p.spawnEgg(holder.get())));
        ATTRIBUTES.put(holder, attributes);
        return holder;
    }
    private static net.minecraft.resources.ResourceKey<EntityType<?>> key(String id) {
        return net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath(Magicka.ID, id));
    }
    public static void attributes(EntityAttributeCreationEvent event) {
        ATTRIBUTES.forEach((type, builder) -> event.put(type.get(), builder.get().build()));
    }
    public static void placements(RegisterSpawnPlacementsEvent event) {
        for (var type : ATTRIBUTES.keySet()) register(event, type.get());
    }
    private static <T extends Monster> void register(RegisterSpawnPlacementsEvent event, EntityType<T> type) {
        event.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random) -> (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION || pos.getY() < 48 && MagicConfig.SPECIAL_SPAWNS.get())
                        && Monster.checkMonsterSpawnRules(t, level, reason, pos, random), RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    public static boolean bySpell;
    public static SpellDefinition hurting;
    /** Every player-spell hit on a living target funnels through here so specialists can react to shape, element and angle. */
    public static void hurt(LivingEntity target, DamageSource source, float damage, ServerPlayer caster, SpellDefinition spell, boolean brittle) {
        if (spell != null) {
            if (target instanceof SpecialEnemy special) damage = special.spellDamage(caster, spell, damage, brittle);
            if (!(target instanceof ServerPlayer)) {
                float shield = 1;
                for (Countermage mage : target.level().getEntitiesOfClass(Countermage.class, target.getBoundingBox().inflate(Countermage.WATCH), LivingEntity::isAlive)) {
                    shield = Math.min(shield, mage.shield(target, spell));
                    if (mage == target || SpecialEnemy.ally(target)) mage.observe(spell);
                }
                damage *= shield;
            }
        }
        if (damage <= 0) return;
        bySpell = spell != null;
        hurting = spell;
        try { target.hurt(source, damage); } finally { bySpell = false; hurting = null; }
    }

    private record Flood(ResourceKey<Level> level, Vec3 center, double radius, long until) {}
    private static final List<Flood> FLOODS = new ArrayList<>();
    public static void flood(LivingEntity caster, Vec3 center, double radius, int ticks) {
        FLOODS.add(new Flood(caster.level().dimension(), center, radius, caster.level().getGameTime() + ticks));
        if (caster.level() instanceof ServerLevel level) level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, center.x, center.y + .2, center.z, 80, radius * .5, .2, radius * .5, .3);
    }
    public static boolean flooded(Level level, Vec3 point) {
        return FLOODS.stream().anyMatch(f -> f.level == level.dimension() && f.center.distanceToSqr(point) < f.radius * f.radius);
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || FLOODS.isEmpty()) return;
        long now = level.getGameTime();
        FLOODS.removeIf(f -> f.level == level.dimension() && f.until <= now);
        for (Flood f : FLOODS) {
            if (f.level != level.dimension()) continue;
            if (now % 4 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FALLING_WATER, f.center.x, f.center.y + .1, f.center.z, 10, f.radius * .5, .05, f.radius * .5, 0);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP, f.center.x, f.center.y + .1, f.center.z, 4, f.radius * .5, .05, f.radius * .5, 0);
            }
            if (now % 10 == 0) for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(f.center, f.center).inflate(f.radius, 1.5, f.radius), e -> e.position().distanceToSqr(f.center) < f.radius * f.radius))
                MagicStatuses.apply(e, MagicStatuses.Status.WET, 60, 1);
        }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { FLOODS.clear(); }

    private static boolean open(ServerLevel level, BlockPos pos) { return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); }
    private static Vec3 floor(ServerLevel level, Vec3 at, Vec3 fallback) {
        BlockPos pos = BlockPos.containing(at);
        for (int up = 0; up <= 2 && !open(level, pos); up++) pos = pos.above();
        if (!open(level, pos) || !open(level, pos.above())) return fallback;
        for (int down = 0; down < 6 && open(level, pos.below()); down++) pos = pos.below();
        return open(level, pos.below()) ? fallback : new Vec3(at.x, pos.getY(), at.z);
    }
    public static final Map<String, List<String>> ENCOUNTERS = new LinkedHashMap<>();
    static {
        ENCOUNTERS.put("a", List.of("cave_skitter*8", "mushroom_nurse"));
        ENCOUNTERS.put("b", List.of("shield_beetle*2", "element_binder", "fodder*4"));
        ENCOUNTERS.put("c", List.of("flood_caster", "spark_imp*3", "fodder*6"));
        ENCOUNTERS.put("d", List.of("stone_crawler*2", "burrower"));
        ENCOUNTERS.put("e", List.of("stone_eater*2", "shield_beetle", "fodder*3"));
        ENCOUNTERS.put("f", List.of("countermage", "element_binder", "mushroom_nurse", "shield_beetle*2", "fodder*4"));
        ENCOUNTERS.put("g", List.of("goblin_warrior*3", "goblin_archer*2", "goblin_mage", "countermage"));
        ENCOUNTERS.put("h", List.of("goblin_warrior*2", "goblin_bomber*2", "goblin_shaman", "goblin_frost_mage", "goblin_storm_mage", "element_binder"));
    }
    public static List<Mob> encounter(ServerLevel level, Vec3 center, String id) {
        List<Mob> spawned = new ArrayList<>();
        var fodder = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER);
        int index = 0;
        for (String entry : ENCOUNTERS.getOrDefault(id, List.of())) {
            String[] parts = entry.split("\\*");
            int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            for (int i = 0; i < count; i++, index++) {
                EntityType<?> type = parts[0].equals("fodder") ? fodder.get(i % fodder.size()) : TYPES.getEntries().stream().filter(h -> h.getId().getPath().equals(parts[0])).findFirst().orElseThrow().get();
                double a = index * 2.4, r = 2 + index * .35;
                Vec3 at = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                if (type.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob mob) {
                    Vec3 spot = floor(level, at, center);
                    mob.snapTo(spot.x, spot.y, spot.z, level.getRandom().nextFloat() * 360, 0);
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.COMMAND, null);
                    level.addFreshEntity(mob);
                    spawned.add(mob);
                }
            }
        }
        return spawned;
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("spellweaver").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("encounter").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> { ENCOUNTERS.keySet().forEach(b::suggest); return b.buildFuture(); })
                        .executes(c -> {
                            String id = StringArgumentType.getString(c, "id").toLowerCase(Locale.ROOT);
                            if (!ENCOUNTERS.containsKey(id)) { c.getSource().sendFailure(Component.translatable("command.magicka.encounter.unknown", id)); return 0; }
                            var source = c.getSource();
                            Vec3 look = source.getRotation() == null ? Vec3.ZERO : Vec3.directionFromRotation(0, source.getRotation().y);
                            int n = encounter(source.getLevel(), source.getPosition().add(look.scale(12)), id).size();
                            source.sendSuccess(() -> Component.translatable("command.magicka.encounter.spawned", id.toUpperCase(Locale.ROOT), n), true);
                            return n;
                        }))));
    }
}
