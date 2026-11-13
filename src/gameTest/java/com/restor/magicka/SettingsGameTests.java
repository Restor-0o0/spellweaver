package com.restor.magicka;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class SettingsGameTests {
    static FakePlayer operator(GameTestHelper h){return new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"SettingsTester")){@Override public boolean hasPermissions(int level){return level<=2;}};}
    @GameTest(template="empty",batch="settings_audit") public static void categoriesCoverAllSettings(GameTestHelper h){
        var values=ConfigCommand.values();Check.assertTrue(h,values.keySet().stream().allMatch(k->SettingsData.category(k)!=null),"Every setting is categorized");
        Check.assertTrue(h,values.keySet().stream().filter(k->k.startsWith("balance.")).count()==37,"All balance settings included");
        Check.assertTrue(h,SettingsData.category("balance.manaCapacity")==SettingsData.Category.MANA && SettingsData.category("balance.spellOverrides")==SettingsData.Category.LIMITS && SettingsData.category("mageLevels")==SettingsData.Category.PROGRESSION,"Categories follow meaning");h.succeed();
    }
    @GameTest(template="empty",batch="settings_audit") public static void validatesNumbersEnumsAndLists(GameTestHelper h){
        Check.assertTrue(h,SettingsData.parse("balance.mode","MANA")==BalanceConfig.Mode.MANA,"Enum recognized");
        Check.assertTrue(h,SettingsData.parse("balance.manaCapacity","-1")==null && SettingsData.parse("balance.manaCapacity","NaN")==null,"Invalid numbers refused");
        Check.assertTrue(h,SettingsData.valid("balance.groupCooldownTicks",List.of("burst=40","custom_group=0")),"Custom groups supported");
        Check.assertFalse(h,SettingsData.valid("balance.groupCooldownTicks",List.of("burst=-1")),"Negative cooldown rejected");
        Check.assertFalse(h,SettingsData.valid("balance.groupCooldownTicks",List.of("burst=NaN")),"Nonfinite cooldown rejected");
        Check.assertTrue(h,SettingsData.valid("balance.elementCooldownTicks",List.of("magma=30","fire=10")),"Element names recognized");
        Check.assertFalse(h,SettingsData.valid("balance.elementCooldownTicks",List.of("fier=30")),"Unknown element names rejected instead of ignored");
        Check.assertTrue(h,SettingsData.valid("balance.spellOverrides",List.of("magicka:firebomb|group=custom|cooldown=40|mana=10")),"Valid override supported");
        Check.assertFalse(h,SettingsData.valid("balance.spellOverrides",List.of("magicka:firebomb|typo=10")),"Unknown override fields rejected");
        Check.assertFalse(h,SettingsData.valid("balance.spellOverrides",List.of("magicka:firebomb|mana=1|mana=2")),"Duplicate fields rejected");h.succeed();
    }
    @GameTest(template="empty",batch="settings_audit") public static void commandExamplesRoundTrip(GameTestHelper h){
        ConfigCommand.values().forEach((key,value)->{String prefix="/spellweaver config "+key+" ",command=SettingsData.command(key,value.getDefault());Check.assertTrue(h,command.startsWith(prefix),"Command targets the exact key");Object parsed=SettingsData.parse(key,command.substring(prefix.length()));Check.assertTrue(h,Objects.equals(parsed,value.getDefault()),"Command retains the typed value for "+key);});
        var rows=List.of("magicka:firebomb|group=firebomb|cooldown=60|mana=20");String command=SettingsData.command("balance.spellOverrides",rows);Check.assertTrue(h,command.contains("cooldown=60")&&!command.contains("u003d"),"Readable list commands preserve equals and pipes");Check.assertTrue(h,SettingsData.parse("balance.spellOverrides",command.substring("/spellweaver config balance.spellOverrides ".length())).equals(rows),"List command parses without losing data");h.succeed();
    }
    @GameTest(template="empty",batch="settings_audit") public static void nonOperatorsCannotChangeSettings(GameTestHelper h){
        var p=RecipeGameTests.caster(h);var before=BalanceConfig.MODE.get();Check.assertFalse(h,SettingsData.authorized(p),"Fake survival player has no operator permission");
        SettingsData.apply(p,new MagicNetwork.SettingsUpdate(1,"{\"balance.mode\":\"OFF\"}"));Check.assertTrue(h,BalanceConfig.MODE.get()==before,"Unauthorized payload has no effect");h.succeed();
    }
    @GameTest(template="empty",batch="settings_audit") public static void invalidBatchIsAtomic(GameTestHelper h){
        var p=operator(h);var mode=BalanceConfig.MODE.get();double mana=BalanceConfig.MANA_MAX.get();
        SettingsData.apply(p,new MagicNetwork.SettingsUpdate(1,"{\"balance.mode\":\"MANA\",\"balance.manaCapacity\":-1}"));
        Check.assertTrue(h,BalanceConfig.MODE.get()==mode && BalanceConfig.MANA_MAX.get()==mana,"One invalid field refuses the whole batch");h.succeed();
    }
    @GameTest(template="empty",batch="settings_audit") public static void operatorBatchSavesAndRateLimits(GameTestHelper h){
        var p=operator(h);var mode=BalanceConfig.MODE.get();double mana=BalanceConfig.MANA_MAX.get();
        try{
            SettingsData.apply(p,new MagicNetwork.SettingsUpdate(1,"{\"balance.mode\":\"MANA\",\"balance.manaCapacity\":175}"));
            Check.assertTrue(h,BalanceConfig.MODE.get()==BalanceConfig.Mode.MANA && BalanceConfig.MANA_MAX.get()==175,"Operator changes saved together");
            SettingsData.apply(p,new MagicNetwork.SettingsUpdate(2,"{\"balance.manaCapacity\":200}"));Check.assertTrue(h,BalanceConfig.MANA_MAX.get()==175,"Repeated immediate writes refused");
        }finally{BalanceConfig.MODE.set(mode);BalanceConfig.MANA_MAX.set(mana);BalanceConfig.MODE.save();SettingsData.forget(p.getUUID());}h.succeed();
    }
    @GameTest(template="empty",batch="settings_audit") public static void consoleCommandsApplyAndRejectInvalidLists(GameTestHelper h){
        try(var f=new SettingsApplicationGameTests.Fixture(h)){
            var commands=h.getLevel().getServer().getCommands();var source=h.getLevel().getServer().createCommandSourceStack();
            commands.performPrefixedCommand(source,"spellweaver config balance.manaBaseCost 17");Check.assertTrue(h,BalanceConfig.MANA_BASE.get()==17,"Console command applies resource cost");
            var before=List.copyOf(BalanceConfig.ELEMENT_TICKS.get());
            commands.performPrefixedCommand(source,"spellweaver config balance.elementCooldownTicks [\"fier=30\"]");Check.assertTrue(h,BalanceConfig.ELEMENT_TICKS.get().equals(before),"Command rejects unknown elements without changing config");
            commands.performPrefixedCommand(source,"spellweaver config balance.manaBaseCost default");Check.assertTrue(h,BalanceConfig.MANA_BASE.get().equals(BalanceConfig.MANA_BASE.getDefault()),"Command reset applies default");
            var state=new SpellBalance.State(100);state.recovery=200;f.update(Map.of("balance.mode",BalanceConfig.Mode.DEFAULT,"balance.cooldownMultiplier",0.0));Check.assertTrue(h,SpellBalance.waitTicks(state,null,100)==0,"Idle HUD hides disabled cooldown timers");f.update(Map.of("balance.mode",BalanceConfig.Mode.MANA,"balance.cooldownMultiplier",1.0));Check.assertTrue(h,SpellBalance.waitTicks(state,null,100)==0,"Mana-only mode does not show old recovery in idle HUD");
        }h.succeed();
    }
}
