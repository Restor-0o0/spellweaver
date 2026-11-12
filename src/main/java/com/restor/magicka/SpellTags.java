package com.restor.magicka;
import java.util.*;
public final class SpellTags {
    public static Set<String> of(SpellDefinition spell){Set<String> tags=new LinkedHashSet<>();String source=spell.tags().toUpperCase(Locale.ROOT);
        if(source.contains("DMG")||source.contains("DOT")||has(spell,"deal_damage"))tags.add("damage");if(source.contains("HEAL")||has(spell,"heal")||has(spell,"revive"))tags.add("heal");
        if(source.contains("CC")||source.contains("CTRL")||source.contains("DEBUFF")||has(spell,"modify_temperature")||has(spell,"add_charge")||spell.effects().stream().anyMatch(n->SpellDefinition.string(n,"status","").matches("rooted|stunned|chilled|frozen|obscured")))tags.add("control");
        if(spell.shape().construction()||has(spell,"create_construction")||has(spell,"place_glowstone")||spell.delivery().has("ice_surface")||spell.selfMode().equals("construct"))tags.add("construction");if(source.contains("UTIL")||(has(spell,"apply_effect")&&!tags.contains("damage")&&!source.contains("DEBUFF"))||has(spell,"place_light")||has(spell,"light_path")||has(spell,"reveal")||has(spell,"extinguish"))tags.add("utility");
        if(source.contains("BREAK")||has(spell,"destroy_blocks")||has(spell,"heave_blocks")||has(spell,"brittle_terrain"))tags.add("destruction");if(spell.shape()==SpellShape.SUMMON_OBJECT||spell.selfMode().equals("summon")||has(spell,"summon_undead")||has(spell,"raise_dead"))tags.add("summon");
        if(has(spell,"mine_blocks"))tags.add("mining");
        if(source.contains("DEF")||spell.selfEffects().stream().anyMatch(n->SpellDefinition.string(n,"status","").equals("stone_shell")))tags.add("defense");
        if(spell.shape()==SpellShape.DASH||source.contains("MOVE")||source.contains("TRAV")||spell.selfMode().equals("dash"))tags.add("movement");if(spell.shape()==SpellShape.TARGETED_ZONE||spell.shape()==SpellShape.MINE||has(spell,"spawn_zone")||spell.selfMode().equals("zone"))tags.add("zone");if(spell.shape()==SpellShape.PROJECTILE||spell.shape()==SpellShape.BURST_PROJECTILE)tags.add("projectile");if(tags.isEmpty())tags.add("utility");return Collections.unmodifiableSet(tags);}
    private static boolean has(SpellDefinition s,String type){return s.effects().stream().anyMatch(n->SpellDefinition.string(n,"type","").equals(type));}
    private SpellTags(){}
}
