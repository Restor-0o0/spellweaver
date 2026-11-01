package com.restor.magicka;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class Conduction {
    private static final double PUDDLE_RADIUS = 24;
    private static final int BACKFIRE_COOLDOWN = 10;
    private static final Map<UUID, Long> LAST_BACKFIRE = new HashMap<>();
    private static boolean spreading;

    static float lightning(List<JsonObject> graph) {
        float total = 0;
        for (JsonObject n : graph) {
            if (EffectGraph.text(n,"type","").equals("deal_damage") && EffectGraph.text(n,"element","arcane").equals("lightning")) total += (float)EffectGraph.number(n,"amount",1);
            total += lightning(EffectGraph.children(n));
        }
        return total;
    }
    private static List<JsonObject> current(List<JsonObject> graph) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonObject n : graph) {
            String type = EffectGraph.text(n,"type","");
            if (type.equals("add_charge") || type.equals("deal_damage") && EffectGraph.text(n,"element","arcane").equals("lightning")) out.add(n);
            out.addAll(current(EffectGraph.children(n)));
        }
        return out;
    }

    public static void backfire(ServerPlayer p, SpellDefinition s, boolean self) {
        if (self || !MagicConfig.selfHarm() || s.formula().lightning()<=0 || !MagicStatuses.has(p,MagicStatuses.Status.WET)) return;
        float amount = lightning(s.effects());
        if (amount <= 0) return;
        ServerLevel level = p.getLevel();
        long now = level.getGameTime();
        if (LAST_BACKFIRE.getOrDefault(p.getUUID(),-99L)+BACKFIRE_COOLDOWN > now) return;
        LAST_BACKFIRE.put(p.getUUID(),now);
        float damage = amount*MagicConfig.PLAYER_DAMAGE.get().floatValue()*MagicConfig.selfHarmScale();
        p.invulnerableTime = 0;
        com.restor.magicka.enemy.MagicEnemies.hurt(p,net.minecraft.world.damagesource.DamageSource.MAGIC,damage,p,s,false);
        MagicStatuses.apply(p,MagicStatuses.Status.SHOCKED,40,1);
        MagicStatuses.charge(p,30);
        sparks(level,p.getBoundingBox().getCenter(),30);
        level.playSound(null,p.blockPosition(),SoundEvents.LIGHTNING_BOLT_IMPACT,SoundSource.PLAYERS,.8f,1.6f);
    }

    static void puddle(ServerPlayer caster, LivingEntity target, SpellDefinition spell, List<JsonObject> graph, Vec3 origin) {
        if (spreading || !standsInWater(target)) return;
        List<JsonObject> shock = current(graph);
        if (lightning(shock) <= 0) return;
        ServerLevel level = caster.getLevel();
        Vec3 at = target.position();
        List<LivingEntity> victims = SpellRuntime.near(caster,at,PUDDLE_RADIUS).stream()
                .filter(e -> e != target && standsInWater(e) && (e != caster || MagicConfig.selfHarm())).toList();
        if (victims.isEmpty()) return;
        spreading = true;
        try {
            for (LivingEntity e : victims) {
                e.invulnerableTime = 0;
                EffectGraph.entity(caster,e,spell,shock,origin,false);
                Vec3 c = e.getBoundingBox().getCenter();
                for (int i = 0; i <= 8; i++) {
                    Vec3 step = at.add(0,.1,0).lerp(c,i/8.0);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,step.x,step.y,step.z,2,.15,.05,.15,.05);
                }
            }
        } finally { spreading = false; }
        level.playSound(null,at.x,at.y,at.z,SoundEvents.LIGHTNING_BOLT_IMPACT,SoundSource.PLAYERS,.6f,1.8f);
    }

    public static boolean standsInWater(LivingEntity e) {
        return e.isInWater() || com.restor.magicka.enemy.MagicEnemies.flooded(e.level,e.position()) || SpellRuntime.inWetZone(e);
    }

    public static boolean wetCaster(ServerPlayer p) { return MagicConfig.selfHarm() && MagicStatuses.has(p,MagicStatuses.Status.WET); }

    private static void sparks(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,at.x,at.y,at.z,count,.4,.6,.4,.2);
    }

    private Conduction() {}
}
