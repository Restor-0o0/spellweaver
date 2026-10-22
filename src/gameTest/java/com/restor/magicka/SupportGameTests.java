package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class SupportGameTests {
    @GameTest(template="empty")
    public static void supportMagicLightsAndHelps(GameTestHelper h){
        DeathGameTests.floor(h);var p=RecipeGameTests.caster(h);
        h.assertTrue(SpellTags.of(RecipeGameTests.spell("Cat's Eye")).contains("utility") && SpellTags.of(RecipeGameTests.spell("Glowstone")).contains("construction"),"Support spells are tagged");
        SpellRuntime.cast(p,RecipeGameTests.spell("Glowstone"),true);
        h.assertBlockPresent(MagicBlocks.GLOW.get(),new BlockPos(1,4,1));
        h.assertTrue(SupportMagic.light(p,h.absoluteVec(new Vec3(3.5,2.2,3.5)),1),"Wisp light is placed");
        h.assertBlockPresent(MagicBlocks.LIGHT.get(),new BlockPos(3,3,3));
        SpellRuntime.cast(p,RecipeGameTests.spell("Cat's Eye"),true);
        h.assertTrue(p.hasEffect(MobEffects.NIGHT_VISION),"Cat's Eye grants night vision");
        SpellRuntime.cast(p,RecipeGameTests.spell("Miner's Vigor"),true);
        h.assertTrue(p.getEffect(MobEffects.DIG_SPEED)!=null && p.getEffect(MobEffects.DIG_SPEED).getAmplifier()==1,"Miner's Vigor grants Haste II");
        h.setBlock(new BlockPos(4,2,0),Blocks.FIRE);
        h.assertTrue(SupportMagic.extinguish(p,h.absoluteVec(new Vec3(4.5,2.5,.5)),3)>0,"Douse puts out fire");
        h.assertBlockPresent(Blocks.AIR,new BlockPos(4,2,0));
        h.succeed();
    }
}
