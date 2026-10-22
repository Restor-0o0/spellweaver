package com.restor.magicka;

import net.minecraftforge.event.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class MagicGameTests {
    @GameTest(template = "empty")
    public static void formulas(GameTestHelper helper) {
        Formula f = new Formula();
        for (int i = 0; i < Formula.MAX; i++) helper.assertTrue(f.add(Element.EARTH), "Element accepted within capacity");
        helper.assertFalse(f.add(Element.FIRE), "Sixth element must be rejected");
        helper.assertTrue(f.budget() == 28 && f.count(Element.EARTH) == 5, "Earth x5 budget");
        // All 37,448 possible non-empty input sequences survive the network encoding.
        for (int length = 1; length <= Formula.MAX; length++) {
            int count = (int) Math.pow(8, length);
            for (int sequence = 0; sequence < count; sequence++) {
                Formula input = new Formula(); int value = sequence;
                for (int slot = 0; slot < length; slot++) { input.add(Element.values()[value % 8]); value /= 8; }
                helper.assertTrue(input.elements().equals(Formula.unpack(input.packed()).elements()), "Formula round trip");
                Formula reverse = new Formula(); var reversed=new java.util.ArrayList<>(input.elements());java.util.Collections.reverse(reversed);reversed.forEach(reverse::add);
                helper.assertTrue(input.key().equals(reverse.key()) && input.key().hashCode()==reverse.key().hashCode(), "Order independent keys");
                helper.assertTrue(RecipeRegistry.resolve(input.key())==RecipeRegistry.resolve(reverse.key()), "Order independent recipe resolution");
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void expiryAndPersistence(GameTestHelper helper) {
        BlockPos p = new BlockPos(1, 2, 1);
        helper.setBlock(p, MagicBlocks.ICE.get());
        helper.setBlock(p.east(), MagicBlocks.EARTH.get());
        MagicBlocks.MagicBlockEntity earth = (MagicBlocks.MagicBlockEntity) helper.getBlockEntity(p.east());
        earth.initialize(UUID.randomUUID(), UUID.randomUUID(), helper.absolutePos(p.east()), helper.getLevel().getGameTime() + 8, true);
        MagicBlocks.MagicBlockEntity data = (MagicBlocks.MagicBlockEntity) helper.getBlockEntity(p);
        UUID owner = UUID.randomUUID(), construction = UUID.randomUUID();
        long expire = helper.getLevel().getGameTime() + 8;
        data.initialize(owner, construction, helper.absolutePos(p), expire, false);
        var tag = data.saveWithFullMetadata();
        var restored = new MagicBlocks.MagicBlockEntity(helper.absolutePos(p), data.getBlockState());
        restored.load(tag);
        helper.assertTrue(restored.owner.equals(owner) && restored.construction.equals(construction)
                && restored.expires == expire && restored.root.equals(data.root), "Persistent ownership, root and lifetime");
        helper.runAfterDelay(12, () -> { helper.assertBlockPresent(Blocks.AIR, p); helper.assertBlockPresent(MagicBlocks.EARTH.get(), p.east()); helper.succeed(); });
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void rootCollapse(GameTestHelper helper) {
        BlockPos root = new BlockPos(1, 2, 1), child = root.east();
        helper.setBlock(root, MagicBlocks.EARTH.get()); helper.setBlock(child, MagicBlocks.EARTH.get());
        UUID id = UUID.randomUUID();
        ((MagicBlocks.MagicBlockEntity) helper.getBlockEntity(root)).initialize(id, id, helper.absolutePos(root), Long.MAX_VALUE, false);
        ((MagicBlocks.MagicBlockEntity) helper.getBlockEntity(child)).initialize(id, id, helper.absolutePos(root), Long.MAX_VALUE, false);
        helper.setBlock(root, Blocks.AIR);
        helper.runAfterDelay(15, () -> { helper.assertBlockPresent(Blocks.AIR, child); helper.succeed(); });
    }
    @GameTest(template = "empty")
    public static void canceledPlacementRestoresWorld(GameTestHelper helper) {
        BlockPos local = new BlockPos(1, 2, 1), pos = helper.absolutePos(local);
        helper.setBlock(local.below(), Blocks.STONE);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        Consumer<BlockEvent.EntityPlaceEvent> deny = event -> { if (event.getPos().equals(pos)) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(deny);
        try {
            helper.assertFalse(WorldPlacement.placeBlock(player, pos, MagicBlocks.EARTH.get().defaultBlockState(),
                    UUID.randomUUID(), pos, false, false, null), "Canceled event must reject placement");
            helper.assertBlockPresent(Blocks.AIR, local);
        } finally { MinecraftForge.EVENT_BUS.unregister(deny); }
        helper.assertTrue(WorldPlacement.placeBlock(player, pos, MagicBlocks.EARTH.get().defaultBlockState(),
                UUID.randomUUID(), pos, false, false, null), "Placement allowed after listener removed");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void collisionAndTerrainPreserved(GameTestHelper helper) {
        BlockPos local = new BlockPos(1, 2, 1), pos = helper.absolutePos(local);
        helper.setBlock(local.below(), Blocks.STONE);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, local);
        helper.assertFalse(WorldPlacement.placeBlock(player, pos, MagicBlocks.EARTH.get().defaultBlockState(), UUID.randomUUID(), pos, false, false, null), "No suffocation placement");
        pig.discard(); helper.setBlock(local, Blocks.CHEST);
        helper.assertFalse(WorldPlacement.placeBlock(player, pos, MagicBlocks.EARTH.get().defaultBlockState(), UUID.randomUUID(), pos, false, false, null), "Do not replace terrain or containers");
        helper.assertBlockPresent(Blocks.CHEST, local);
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void literalSelfAndElementReactions(GameTestHelper helper) {
        // A real ServerPlayer is needed: NeoForge FakePlayer rejects every damage source.
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SpellTester"));
        player.connection = FakePlayerFactory.getMinecraft(helper.getLevel()).connection;
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)));
        for (int i = 0; i < 61; i++) player.tick(); // Let vanilla spawn invulnerability expire.
        player.setHealth(20); player.invulnerableTime = 0; player.getAbilities().invulnerable = false;
        Formula water = new Formula(); water.add(Element.WATER);
        SpellRuntime.cast(player, RecipeRegistry.resolve(water.key()), true);
        helper.assertTrue(MagicStatuses.has(player,MagicStatuses.Status.WET), "Self water wets caster");
        Formula cold = new Formula(); cold.add(Element.COLD);
        SpellRuntime.cast(player, RecipeRegistry.resolve(cold.key()), true);
        helper.assertTrue(player.getTicksFrozen() > 0, "Wet + cold freezes caster");
        helper.assertTrue(MagicStatuses.has(player,MagicStatuses.Status.BRITTLE), "Frozen targets become brittle");
        player.invulnerableTime = 0;
        Formula earth = new Formula(); earth.add(Element.EARTH);
        float before = player.getHealth();
        SpellRuntime.cast(player, RecipeRegistry.resolve(earth.key()), true);
        helper.assertTrue(player.getHealth() == before && MagicStatuses.has(player,MagicStatuses.Status.STONE_SHELL), "Self earth gives stone shell and mass per new spec");
        var fire=new Formula();fire.add(Element.FIRE);
        SpellRuntime.cast(player,RecipeRegistry.resolve(fire.key()),true);
        helper.assertTrue(player.getHealth()<before,"Bad Self still harms caster");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void committedCastRejectsAllEdits(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CommitTester"));
        CastingServer.input(player, 7); CastingServer.input(player, 0); CastingServer.input(player, 10);
        var before = CastingServer.snapshot(player);
        helper.assertTrue(before.remaining() > 0 && before.intent() == 1, "Self cast starts");
        for (int action : new int[]{8, 4, 9, 11, 7, -1, 1000}) CastingServer.input(player, action);
        helper.assertTrue(before.equals(CastingServer.snapshot(player)), "Commit must reject edits, mode changes and other intents");
        var server = new CastingServer();
        for (int i = 0; i < before.total(); i++) server.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
        helper.assertTrue(CastingServer.snapshot(player).remaining() == 0 && CastingServer.snapshot(player).formula() == 0, "Committed cast completes and consumes formula");
        helper.succeed();
    }
}
