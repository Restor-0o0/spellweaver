package com.restor.magicka;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public final class BalanceConfig {
    private static final ForgeConfigSpec.Builder B=new ForgeConfigSpec.Builder();
    public enum Mode  {
        DEFAULT, ELEMENTS, MANA, STRAIN, CUSTOM, OFF;
        public net.minecraft.network.chat.Component getTranslatedName(){return net.minecraft.network.chat.Component.translatable("balance.magicka.mode."+name().toLowerCase(java.util.Locale.ROOT));}
    }
    public enum ZonePolicy  {
        REPLACE_OLDEST, REJECT;
        public net.minecraft.network.chat.Component getTranslatedName(){return net.minecraft.network.chat.Component.translatable("balance.magicka.zones."+name().toLowerCase(java.util.Locale.ROOT));}
    }
    public static final ForgeConfigSpec.EnumValue<Mode> MODE=label("mode","DEFAULT uses recovery and automatic spell groups; ELEMENTS, MANA and STRAIN are alternatives; CUSTOM combines the switches below; OFF disables balance rules").defineEnum("mode",Mode.DEFAULT);
    public static final ForgeConfigSpec.BooleanValue CREATIVE=label("creativeBypass","Creative players bypass balance rules").define("creativeBypass",true);
    public static final ForgeConfigSpec.BooleanValue FAKE=label("fakePlayerBypass","Automation and fake players bypass balance rules").define("fakePlayerBypass",true);
    public static final ForgeConfigSpec.BooleanValue RECOVERY=label("customRecovery","Enable recovery after casts in CUSTOM mode").define("customRecovery",true);
    public static final ForgeConfigSpec.BooleanValue GROUPS=label("customGroupCooldowns","Enable automatic group cooldowns in CUSTOM mode").define("customGroupCooldowns",true);
    public static final ForgeConfigSpec.BooleanValue ELEMENTS=label("customElementCooldowns","Enable element cooldowns in CUSTOM mode").define("customElementCooldowns",false);
    public static final ForgeConfigSpec.BooleanValue MANA=label("customMana","Enable mana in CUSTOM mode").define("customMana",false);
    public static final ForgeConfigSpec.BooleanValue STRAIN=label("customStrain","Enable strain in CUSTOM mode").define("customStrain",false);
    public static final ForgeConfigSpec.BooleanValue LIMITS=label("activeLimits","Limit ongoing zone and summon casts in every enabled mode").define("activeLimits",true);
    public static final ForgeConfigSpec.BooleanValue SHOW=label("showStatus","Show recovery and resource status inside the cast HUD card").define("showStatus",true);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RECOVERY_TICKS=rows("recoveryTicks","Ticks after a cast or channel ends; 20 ticks = 1 second",List.of("basic=5","burst=14","control=12","healing=10","summon=16","zone=16","ultimate=24","mining=5","construction=4","utility=4","channel=10"));
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GROUP_TICKS=rows("groupCooldownTicks","Shared cooldown by automatic group; zero disables that group cooldown",List.of("basic=0","burst=20","control=40","healing=20","summon=100","zone=80","ultimate=600","mining=0","construction=0","utility=0","channel=0"));
    public static final ForgeConfigSpec.DoubleValue COOLDOWN_SCALE=label("cooldownMultiplier","Multiplier for all recovery, group and element cooldown durations").defineInRange("cooldownMultiplier",1.0,0.0,100.0);
    public static final ForgeConfigSpec.BooleanValue CAST_SPEED=label("castSpeedAffectsCooldowns","Cast speed also reduces cooldowns, subject to the minimum below").define("castSpeedAffectsCooldowns",false);
    public static final ForgeConfigSpec.IntValue MIN_RECOVERY=label("minimumRecoveryTicks","Minimum enabled recovery after multipliers; zero allows no recovery").defineInRange("minimumRecoveryTicks",2,0,1200);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ELEMENT_TICKS=rows("elementCooldownTicks","Per-element cooldown ticks; elemental mode uses the longest remaining involved cooldown",List.of("fire=12","water=10","cold=14","lightning=16","earth=16","life=20","arcane=14","death=20","steam=14","ice=16","shield=20","magma=18"));
    public static final ForgeConfigSpec.DoubleValue ELEMENT_REPEAT=label("elementRepeatMultiplier","Extra cooldown fraction for every repeated point of the same element").defineInRange("elementRepeatMultiplier",.25,0.0,10.0);
    public static final ForgeConfigSpec.BooleanValue PARENTS=label("derivedElementsShareCooldowns","Steam, ice, shield and magma also use the cooldowns of their base parents").define("derivedElementsShareCooldowns",true);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> OVERRIDES=rows("spellOverrides","id|recovery=5|cooldown=20|group=burst|mana=10|mana_per_second=4|strain=8|strain_per_second=5|active_group=zone|active_limit=2; only specified values are replaced",List.of());
    public static final ForgeConfigSpec.DoubleValue MANA_MAX=label("manaCapacity","Maximum mana").defineInRange("manaCapacity",100.0,1.0,1000000.0);
    public static final ForgeConfigSpec.DoubleValue MANA_REGEN=label("manaRegenPerSecond","Mana restored per second").defineInRange("manaRegenPerSecond",12.0,0.0,1000000.0);
    public static final ForgeConfigSpec.IntValue MANA_DELAY=label("manaRegenDelayTicks","Delay after casting or channeling before mana starts regenerating").defineInRange("manaRegenDelayTicks",20,0,72000);
    public static final ForgeConfigSpec.DoubleValue MANA_BASE=label("manaBaseCost","Base mana cost per cast").defineInRange("manaBaseCost",2.0,0.0,1000000.0);
    public static final ForgeConfigSpec.DoubleValue MANA_POINT=label("manaCostPerElement","Additional mana cost per formula point").defineInRange("manaCostPerElement",3.0,0.0,1000000.0);
    public static final ForgeConfigSpec.DoubleValue MANA_CHANNEL=label("manaChannelPerSecond","Additional mana cost per second of channeling").defineInRange("manaChannelPerSecond",8.0,0.0,1000000.0);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MANA_WEIGHTS=rows("manaGroupMultipliers","Mana cost multipliers by automatic group",List.of("ultimate=4","burst=1.5","summon=2","zone=1.5","mining=0.1","construction=0.3","utility=0.5"));
    public static final ForgeConfigSpec.BooleanValue CHANNEL_REGEN=label("regenerateWhileChanneling","Allow mana regeneration during channels after the initial delay").define("regenerateWhileChanneling",false);
    public static final ForgeConfigSpec.BooleanValue DEATH_REFILL=label("refillManaOnDeath","Refill mana on death; cooldowns remain intact").define("refillManaOnDeath",false);
    public static final ForgeConfigSpec.DoubleValue STRAIN_MAX=label("strainCapacity","Maximum strain; casts that would exceed it are refused").defineInRange("strainCapacity",100.0,1.0,1000000.0);
    public static final ForgeConfigSpec.DoubleValue STRAIN_DECAY=label("strainDecayPerSecond","Strain removed per second").defineInRange("strainDecayPerSecond",18.0,0.0,1000000.0);
    public static final ForgeConfigSpec.IntValue STRAIN_DELAY=label("strainDecayDelayTicks","Delay after the last cast or channel tick before strain decays").defineInRange("strainDecayDelayTicks",10,0,72000);
    public static final ForgeConfigSpec.DoubleValue STRAIN_BASE=label("strainBaseCost","Base strain added by a cast").defineInRange("strainBaseCost",4.0,0.0,1000000.0);
    public static final ForgeConfigSpec.DoubleValue STRAIN_POINT=label("strainPerElement","Additional strain per formula point").defineInRange("strainPerElement",3.0,0.0,1000000.0);
    public static final ForgeConfigSpec.DoubleValue STRAIN_CHANNEL=label("strainChannelPerSecond","Additional strain per second of channeling").defineInRange("strainChannelPerSecond",10.0,0.0,1000000.0);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> STRAIN_WEIGHTS=rows("strainGroupMultipliers","Strain cost multipliers by automatic group",List.of("ultimate=2","burst=1.5","summon=1.5","zone=1.5","mining=0.1","construction=0.3","utility=0.5"));
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ACTIVE_LIMITS=rows("activeCastLimits","Limits on concurrent ongoing casts; each cast may create several effect nodes; zero means unlimited",List.of("zone=2","ultimate=1","summon=3"));
    public static final ForgeConfigSpec.EnumValue<ZonePolicy> ZONE_POLICY=label("activeLimitPolicy","Replace the oldest ongoing cast or refuse a new cast at its active limit").defineEnum("activeLimitPolicy",ZonePolicy.REPLACE_OLDEST);
    public static final ForgeConfigSpec SPEC=B.build();
    private static ForgeConfigSpec.Builder label(String key,String hint){return B.translation("magicka.configuration.balance."+key).comment(hint);}
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> rows(String key,String hint,List<String> defaults){return label(key,hint).defineListAllowEmpty(List.of(key),()->defaults,value->value instanceof String);}
    public static boolean recovery(){return MODE.get()==Mode.DEFAULT || MODE.get()==Mode.CUSTOM && RECOVERY.get();}
    public static boolean groups(){return MODE.get()==Mode.DEFAULT || MODE.get()==Mode.CUSTOM && GROUPS.get();}
    public static boolean elements(){return MODE.get()==Mode.ELEMENTS || MODE.get()==Mode.CUSTOM && ELEMENTS.get();}
    public static boolean mana(){return MODE.get()==Mode.MANA || MODE.get()==Mode.CUSTOM && MANA.get();}
    public static boolean strain(){return MODE.get()==Mode.STRAIN || MODE.get()==Mode.CUSTOM && STRAIN.get();}
    private BalanceConfig(){}
}
