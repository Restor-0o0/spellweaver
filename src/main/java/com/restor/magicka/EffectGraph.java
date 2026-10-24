package com.restor.magicka;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class EffectGraph {
    private static final Set<String> TYPES = Set.of("deal_damage","heal","apply_status","remove_status","apply_force","set_wet","modify_temperature","add_charge",
            "spawn_projectile","spawn_zone","create_construction","destroy_blocks","heave_blocks","chain_targets","delay","conditional","revive","grow_plants","brittle_terrain","drain_life","summon_undead","raise_dead",
            "apply_effect","polymorph","place_light","light_path","light_ring","place_glowstone","reveal","extinguish");
    public static void validate(JsonObject node, int depth) {
        String type = text(node,"type","");
        if (!TYPES.contains(type) || depth > 6) throw new IllegalArgumentException("Invalid effect graph node " + type);
        if (node.has("status")) MagicStatuses.Status.valueOf(text(node,"status","").toUpperCase(Locale.ROOT));
        if (type.equals("apply_effect") && net.minecraft.resources.Identifier.tryParse(text(node,"effect","")) == null) throw new IllegalArgumentException("Invalid mob effect " + text(node,"effect",""));
        for (var field : node.entrySet()) if (field.getValue().isJsonPrimitive() && field.getValue().getAsJsonPrimitive().isNumber()) {
            double value = field.getValue().getAsDouble();
            if (!Double.isFinite(value) || Math.abs(value) > 72000) throw new IllegalArgumentException("Invalid effect number " + field.getKey());
        }
        if (node.has("effects")) {
            if (node.getAsJsonArray("effects").size() > 64) throw new IllegalArgumentException("Effect graph too large");
            for (JsonElement child : node.getAsJsonArray("effects")) validate(child.getAsJsonObject(),depth+1);
        }
    }
    public static String text(JsonObject n, String key, String fallback) { return n.has(key) ? n.get(key).getAsString() : fallback; }
    public static double number(JsonObject n, String key, double fallback) { return n.has(key) ? n.get(key).getAsDouble() : fallback; }
    public static List<JsonObject> children(JsonObject node) { return node.has("effects") ? node.getAsJsonArray("effects").asList().stream().map(JsonElement::getAsJsonObject).toList() : List.of(); }
    public static boolean canHarm(ServerPlayer caster, LivingEntity target) {
        if (caster == target) return true;
        if (target instanceof Player other && !caster.canHarmPlayer(other)) return false;
        return MagicConfig.FRIENDLY_FIRE.get() || (!(target instanceof Player) && !caster.isAlliedTo(target) && !target.isAlliedTo(caster));
    }
    public static boolean ally(ServerPlayer caster, LivingEntity target) {
        return target == caster || UndeadMinions.ownedBy(target, caster) || LegendMagic.charmedBy(target, caster) || target.isAlliedTo(caster) || caster.isAlliedTo(target) || target instanceof Player;
    }
    public static void entity(ServerPlayer caster, LivingEntity target, SpellDefinition spell, List<JsonObject> graph, Vec3 origin, boolean self) {
        if (!target.isAlive() || target.isSpectator()) return;
        boolean frozen = MagicStatuses.has(target,MagicStatuses.Status.FROZEN) || MagicStatuses.has(target,MagicStatuses.Status.BRITTLE);
        float damage = executeEntity(caster,target,spell,graph,origin,self,frozen);
        if (damage > 0 && canHarm(caster,target)) {
            if (target instanceof Player && target != caster) damage *= MagicConfig.PLAYER_DAMAGE.get().floatValue();
            com.restor.magicka.enemy.MagicEnemies.hurt(target,target == caster ? caster.damageSources().magic() : caster.damageSources().indirectMagic(caster,caster),damage,caster,spell,frozen);
        }
    }
    private static float executeEntity(ServerPlayer caster, LivingEntity target, SpellDefinition spell, List<JsonObject> graph, Vec3 origin, boolean self, boolean frozen) {
        float damage = 0;
        for (JsonObject node : graph) {
            String type = text(node,"type",""); double amount = number(node,"amount",1);
            int ticks = (int)number(node,"duration_ticks",100), strength = (int)number(node,"strength",1);
            switch(type) {
                case "deal_damage" -> {
                    if (!canHarm(caster,target)) break;
                    String element = text(node,"element","arcane");
                    double multiplier = element.equals("lightning") && MagicStatuses.has(target,MagicStatuses.Status.WET) ? 2 : element.equals("earth") && frozen ? 2 : element.equals("fire") && MagicStatuses.has(target,MagicStatuses.Status.WET) ? .5 : 1;
                    damage += (float)(amount*multiplier);
                }
                case "heal" -> target.heal(MagicGear.heal(caster,target,(float)amount));
                case "drain_life" -> {
                    if (!canHarm(caster,target) || target == caster) break;
                    damage += (float)amount;
                    caster.heal((float)(amount*number(node,"fraction",.5)));
                }
                case "set_wet" -> MagicStatuses.apply(target,MagicStatuses.Status.WET,ticks,1);
                case "modify_temperature" -> { if (canHarm(caster,target) && !MagicGear.blocked(caster,target,type,amount)) MagicStatuses.temperature(target,amount); }
                case "add_charge" -> { if (canHarm(caster,target) && !MagicGear.blocked(caster,target,type,amount)) MagicStatuses.charge(target,amount); }
                case "apply_status" -> {
                    String status = text(node,"status","");
                    if (Set.of("regenerating","stone_shell","slow_falling","haste").contains(status) || canHarm(caster,target)) { MagicStatuses.apply(target,status,ticks,strength); MagicGear.status(caster,target,status); }
                }
                case "apply_effect" -> SupportMagic.effect(caster,target,text(node,"effect",""),ticks,strength);
                case "polymorph" -> PolymorphManager.apply(caster,target,text(node,"entity","minecraft:sheep"),ticks);
                case "remove_status" -> MagicStatuses.remove(target,MagicStatuses.Status.valueOf(text(node,"status","").toUpperCase(Locale.ROOT)));
                case "apply_force" -> {
                    if (!canHarm(caster,target)) break;
                    Vec3 direction = target.position().subtract(origin).normalize();
                    if (direction.lengthSqr() < .001) direction = caster.getLookAngle();
                    target.push(direction.x*amount,number(node,"lift",0),direction.z*amount); target.syncVelocity = true;
                }
                case "conditional" -> {
                    boolean passes = switch(text(node,"condition","")) { case "wet" -> MagicStatuses.has(target,MagicStatuses.Status.WET); case "was_frozen","brittle" -> frozen; case "self" -> self; default -> false; };
                    if (passes) damage += executeEntity(caster,target,spell,children(node),origin,self,frozen);
                }
                case "delay" -> SpellRuntime.delay(caster,Math.clamp((int)number(node,"ticks",20),1,1200), () -> {
                    if (target.isAlive() && target.level() == caster.level()) entity(caster,target,spell,children(node),origin,self);
                });
                default -> {} // World-only effects are dispatched once per impact, not once per victim.
            }
        }
        return damage;
    }
    public static void world(ServerPlayer caster, SpellDefinition spell, List<JsonObject> graph, Vec3 point, boolean self, boolean allowSpawn) {
        for (JsonObject node : graph) switch(text(node,"type","")) {
            case "modify_temperature" -> WorldMagic.temperature(caster,point,number(node,"amount",0),spell);
            case "set_wet" -> {
                SupportMagic.extinguish(caster,point,Math.clamp(spell.number("radius",2),2,4));
                if (allowSpawn) SpellRuntime.wetZone(caster,point,Math.min(2,spell.number("radius",1)),(int)number(node,"duration_ticks",160));
            }
            case "destroy_blocks" -> WorldMagic.destroy(caster,point,(int)number(node,"budget",1),number(node,"radius",1),spell.ultimate());
            case "heave_blocks" -> WorldMagic.heave(caster,point,(int)number(node,"budget",8),number(node,"radius",3),number(node,"lift",.6));
            case "brittle_terrain" -> WorldMagic.brittle(caster,point,number(node,"radius",2));
            case "grow_plants" -> WorldMagic.grow(caster,point,(int)number(node,"budget",1));
            case "summon_undead" -> { if (allowSpawn) UndeadMinions.summon(caster,caster.position(),text(node,"entity","minecraft:zombie"),(int)number(node,"count",1)+(int)spell.number("summon_extra",0),(int)number(node,"duration_ticks",600),spell.number("summon_health",1)); }
            case "raise_dead" -> { if (allowSpawn) RevivalManager.raise(caster,caster.position(),number(node,"radius",spell.number("radius",6)),(int)number(node,"count",3)+(int)spell.number("summon_extra",0),(int)number(node,"duration_ticks",900),spell.number("summon_health",1)); }
            case "place_light" -> SupportMagic.light(caster,point,number(node,"offset_y",0));
            case "light_path" -> SupportMagic.path(caster,point,number(node,"spacing",6),(int)number(node,"count",8));
            case "light_ring" -> SupportMagic.ring(caster,point,number(node,"radius",5),(int)number(node,"count",6));
            case "place_glowstone" -> SupportMagic.glowstone(caster,point,number(node,"offset_y",0));
            case "reveal" -> SupportMagic.reveal(caster,point,number(node,"radius",24),(int)number(node,"duration_ticks",600));
            case "extinguish" -> SupportMagic.extinguish(caster,point,number(node,"radius",5));
            case "revive" -> RevivalManager.revive(caster,point,spell.number("radius",3),(float)number(node,"health_fraction",.3));
            case "create_construction" -> { if (allowSpawn) MagicConstructionManager.start(caster,spell,self,point); }
            case "spawn_zone" -> { if (allowSpawn) SpellRuntime.zone(caster,spell,point,(int)number(node,"duration_ticks",100),number(node,"radius",2),false,false); }
            case "spawn_projectile" -> { if (allowSpawn) SpellRuntime.projectile(caster,spell,point,caster.getLookAngle(),false); }
            case "chain_targets" -> SpellRuntime.chain(caster,spell,point,(int)number(node,"count",4),number(node,"range",4),(float)number(node,"amount",2));
            case "delay" -> { if (allowSpawn) SpellRuntime.delay(caster,(int)number(node,"ticks",20),() -> world(caster,spell,children(node),point,self,false)); }
            default -> {}
        }
    }
    private EffectGraph() {}
}
