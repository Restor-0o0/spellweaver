package com.restor.magicka;

import net.minecraftforge.event.TickEvent;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class EpicGameTests {
    private static Zombie dummy(GameTestHelper h) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new Vec3(2.5, 2, 2.5));
        zombie.setNoAi(true);
        return zombie;
    }
    private static void type(net.minecraft.server.level.ServerPlayer p, SpellDefinition spell) {
        for (Element e : spell.spoken()) CastingServer.input(p, e == Element.DEATH ? 13 : e.ordinal());
    }

    @GameTest(template="empty")
    public static void epicUltimatesChannelOnlyWhileStandingStill(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h); var server = new CastingServer();
        for (String name : new String[]{"Thunderstorm", "Meteor Shower", "Hurricane", "Blizzard"})
            h.assertTrue(RecipeGameTests.spell(name).epic() && !RecipeGameTests.spell(name).incantation().isEmpty(), name + " is an epic ultimate");
        var storm = RecipeGameTests.spell("Thunderstorm");
        CastingServer.input(p, 7); CastingServer.input(p, 11); type(p, storm); CastingServer.input(p, 9);
        h.assertTrue(CastingServer.snapshot(p).remaining() > 0 && !CastingServer.snapshot(p).channeling(), "Instant mode does not skip the ultimate windup");
        for (int i = 0; i < 400 && !CastingServer.snapshot(p).channeling(); i++) server.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        h.assertTrue(CastingServer.snapshot(p).channeling() && storm.equals(CastingServer.casting(p)), "The storm channels after the windup");
        server.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        h.assertTrue(CastingServer.snapshot(p).channeling(), "Standing still keeps the channel");
        CastingServer.input(p, 12);
        server.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        h.assertTrue(CastingServer.snapshot(p).channeling(), "Releasing the button does not end an ultimate");
        p.setPos(p.position().add(1, 0, 0));
        server.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        h.assertFalse(CastingServer.snapshot(p).channeling(), "Stepping aside breaks the channel");
        h.assertTrue(CastingServer.snapshot(p).remaining() == 0 && CastingServer.casting(p) == null, "A broken channel ends the ultimate");
        CastingServer.input(p, 12);
        type(p, storm); CastingServer.input(p, 9);
        p.setPos(p.position().add(0, 0, 1));
        server.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        h.assertTrue(CastingServer.snapshot(p).remaining() == 0, "Moving during the windup cancels it too");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void stormLightningStrikesUnderTheCloud(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h); var zombie = dummy(h);
        EpicMagic.strike(p, RecipeGameTests.spell("Thunderstorm"), zombie.position(), zombie.getY() + 6, .1);
        h.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "A bolt hurts what stands under it");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void meteorExplodesOnImpact(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h); var zombie = dummy(h);
        EpicMagic.meteor(p, RecipeGameTests.spell("Meteor Shower"), zombie.position().add(-2, 4.5, 0), zombie.position());
        h.runAfterDelay(20, () -> {
            h.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "The meteor blast hurts its target");
            h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void epicUltimatesNeedOpenSky(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h); p.setXRot(60);
        h.assertFalse(EpicMagic.cramped(p), "Open sky lets the ultimate through");
        for (int x = -5; x < 10; x++) for (int z = -5; z < 10; z++) {
            h.getLevel().setBlockAndUpdate(h.absolutePos(new net.minecraft.core.BlockPos(x, 1, z)), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(h.absolutePos(new net.minecraft.core.BlockPos(x, 5, z)), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        h.assertTrue(EpicMagic.cramped(p), "A three-block cave is too cramped");
        type(p, RecipeGameTests.spell("Meteor Shower")); CastingServer.input(p, 9);
        h.assertTrue(CastingServer.snapshot(p).remaining() == 0 && CastingServer.casting(p) == null, "The ultimate does not start in a cave");
        h.succeed();
    }
}
