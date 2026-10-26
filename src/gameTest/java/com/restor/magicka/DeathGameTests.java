package com.restor.magicka;

import net.minecraft.world.entity.EntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
public final class DeathGameTests {
    static void floor(GameTestHelper h){for(int x=0;x<5;x++)for(int z=0;z<5;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);}
    static java.util.List<Mob> minions(GameTestHelper h){return h.getLevel().getEntitiesOfClass(Mob.class,new AABB(h.absolutePos(BlockPos.ZERO)).inflate(12),m->m.entityTags().contains(UndeadMinions.TAG));}
    static java.util.List<Mob> minions(GameTestHelper h,net.minecraft.server.level.ServerPlayer owner){return minions(h).stream().filter(m->UndeadMinions.ownedBy(m,owner)).toList();}
    @MagicTest
    public static void deathInputAndLegacyPresets(GameTestHelper h){
        var p=RecipeGameTests.caster(h);
        CastingServer.input(p,7);CastingServer.input(p,13);CastingServer.input(p,13);
        var formula=Formula.unpack(CastingServer.snapshot(p).formula());
        h.assertTrue(formula.count(Element.DEATH)==2,"Death key adds the eighth element");
        h.assertTrue(RecipeRegistry.resolve(formula.key()).name().equals("Grave Call"),"Death x2 resolves to Grave Call");
        int legacy=1|(7<<3);
        h.assertTrue(Formula.valid(legacy) && Formula.unpack(legacy).count(Element.ARCANE)==1 && Formula.unpack(legacy).count(Element.FIRE)==1,"Old three-bit presets still load");
        var state=new GrimoireState();state.assign(0,legacy);
        h.assertTrue(state.slot(0)==Formula.unpack(legacy).packed(),"Old presets are converted");
        h.assertTrue(SpellTags.of(RecipeGameTests.spell("Boulder")).contains("destruction") && SpellTags.of(RecipeGameTests.spell("Tomb Collapse")).contains("destruction"),"Terrain breaking spells are tagged");
        h.assertTrue(SpellTags.of(RecipeGameTests.spell("Legion of the Dead")).contains("summon") && !SpellTags.of(RecipeGameTests.spell("Ember")).contains("destruction"),"Summon and destruction tags are precise");
        h.succeed();
    }
    @MagicTest(timeoutTicks=200)
    public static void minionsServeTheirMaster(GameTestHelper h){
        floor(h);var p=RecipeGameTests.caster(h);
        SpellRuntime.cast(p,RecipeGameTests.spell("Grave Call"),true);
        var raised=minions(h,p);
        h.assertTrue(raised.size()==2 && UndeadMinions.count(p)==2,"Grave Call raises two servants, got "+raised.size());
        var servant=raised.getFirst();servant.setTarget(p);
        h.assertTrue(servant.getTarget()==null,"A servant never targets its master");
        h.assertTrue(EffectGraph.canHarm(p,servant),"Master spells hurt own servants too");
        var enemy=h.spawnWithNoFreeWill(EntityType.SKELETON,new BlockPos(3,2,3));
        h.assertTrue(raised.stream().allMatch(m->m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.SKELETON_SKULL)),"Servants wear skulls against sunlight");
        h.succeedWhen(()->h.assertTrue(raised.stream().anyMatch(m->m.getTarget()==enemy) || enemy.getHealth()<enemy.getMaxHealth(),"Servants attack nearby monsters"));
    }
    @MagicTest(timeoutTicks=80)
    public static void servantsGatherAndCrumble(GameTestHelper h){
        floor(h);var p=RecipeGameTests.caster(h);
        UndeadMinions.summon(p,p.position(),"minecraft:zombie",3,200);
        var raised=minions(h,p);
        h.assertTrue(raised.size()>=2,"Servants rise around their master, got "+raised.size());
        h.assertTrue(raised.stream().allMatch(m->m.position().subtract(p.position()).horizontalDistance()<6),"Servants rise at the master, not at the aim point");
        h.assertTrue(raised.stream().map(Mob::blockPosition).distinct().count()==raised.size(),"Every servant gets its own spot");
        var stray=raised.getFirst();
        stray.teleportTo(p.getX()+200,p.getY(),p.getZ());
        h.runAfterDelay(12,()->{
            h.assertTrue(!stray.isAlive()||stray.isRemoved(),"A servant left far behind crumbles instead of teleporting");
            h.assertTrue(stray.distanceToSqr(p)>100*100,"The stray never follows its master back");
            h.succeed();
        });
    }
    @MagicTest(timeoutTicks=80)
    public static void servantsCauseFriendlyFire(GameTestHelper h){
        floor(h);var p=RecipeGameTests.caster(h);
        SpellRuntime.cast(p,RecipeGameTests.spell("Creeper Graves"),true);
        var creepers=minions(h).stream().filter(m->m instanceof net.minecraft.world.entity.monster.Creeper).toList();
        h.assertTrue(creepers.size()==3,"Creeper Graves raises three creepers, got "+creepers.size());
        h.assertTrue(RecipeGameTests.spell("Legion of the Dead").effects().stream().noneMatch(n->n.toString().contains("wither")),"No wither skeletons in the legion");
        var creeper=creepers.getFirst();var other=creepers.get(1);
        other.hurt(h.getLevel().damageSources().explosion(creeper,creeper),6);
        h.assertTrue(other.getHealth()<other.getMaxHealth(),"Servant blasts hurt other servants");
        other.setTarget(p);
        h.assertTrue(other.getTarget()==null,"Servants still never target their master");
        other.setTarget(creepers.get(2));
        h.assertTrue(other.getTarget()==null,"Servants do not pick fights with each other");
        other.setLastHurtByMob(creeper);other.setTarget(creeper);
        h.assertTrue(other.getTarget()==creeper,"A hurt servant strikes back");
        h.succeed();
    }
    @MagicTest(timeoutTicks=80)
    public static void raiseDeadAndWithering(GameTestHelper h){
        floor(h);var p=RecipeGameTests.caster(h);
        var fallen=h.spawnWithNoFreeWill(EntityType.SKELETON,new BlockPos(2,2,3));var corpse=fallen.position();
        fallen.hurt(p.damageSources().magic(),1000);
        var pig=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(0,2,3));
        MagicStatuses.apply(pig,MagicStatuses.Status.WITHERING,100,2);
        h.runAfterDelay(25,()->{
            h.assertTrue(RevivalManager.raise(p,corpse,3,4,200)==1,"The fallen skeleton rises");
            h.assertTrue(minions(h).stream().anyMatch(m->m.getType()==EntityType.SKELETON),"Raised as its own kind");
            h.assertTrue(pig.getHealth()<pig.getMaxHealth(),"Withering hurts over time");
            p.setHealth(4);var victim=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new BlockPos(1,2,3));
            EffectGraph.entity(p,victim,RecipeGameTests.spell("Soul Siphon"),RecipeGameTests.spell("Soul Siphon").effects(),p.position(),false);
            h.assertTrue(p.getHealth()>4 && victim.getHealth()<victim.getMaxHealth(),"Soul Siphon drains life");
            h.succeed();
        });
    }
}
