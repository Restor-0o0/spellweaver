package com.restor.magicka;

import com.restor.magicka.enemy.MagicEnemies;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Area and weapon casts of any formula; Shield raises a dome in area casts and a ward on self casts. */
public final class CastModes {
    public static final int IMBUE_TICKS = 600, WARD_TICKS = 400;
    public static final float WARD_PER_ELEMENT = .5f, WARD_CAP = .9f, BARE_WARD = .3f;
    public record Imbue(SpellDefinition spell, int hits, long until) {}
    public record Ward(SpellFormula formula, long until) {}
    public static final class Dome {
        public final Vec3 center; public final double radius; public final int ticks;
        boolean live = true;
        Dome(Vec3 center, double radius, int ticks) { this.center = center; this.radius = radius; this.ticks = ticks; }
    }
    private static final Map<UUID, Imbue> IMBUES = new HashMap<>();
    private static final Map<UUID, Ward> WARDS = new HashMap<>();
    private static final Map<UUID, Dome> DOMES = new HashMap<>();

    static SpellDefinition elemental(ServerPlayer p, SpellFormula f) { return MagicStats.apply(p, RecipeRegistry.fusion(f)); }
    public static double novaRadius(SpellFormula f) { return 2 + .6 * f.size(); }
    public static double domeRadius(SpellFormula f) { return 3.5 + .25 * f.size(); }
    public static int domeTicks(SpellFormula f) { return Math.min(260, 120 + 30 * f.shield() + 15 * (f.size() - f.shield())); }
    static SpellFormula unshielded(SpellFormula f) { while (f.shield() > 0) f = f.without(Element.SHIELD); return f; }

    public static void area(ServerPlayer p, SpellDefinition recipe) {
        SpellFormula f = recipe.formula();
        p.swing(InteractionHand.MAIN_HAND, true);
        if (f.shield() > 0) { dome(p, f); return; }
        SpellDefinition s = elemental(p, f);
        Conduction.backfire(p, s, false);
        Vec3 at = p.position();
        double r = novaRadius(f);
        for (LivingEntity e : SpellRuntime.near(p, at.add(0, .9, 0), r)) {
            if (e == p || EffectGraph.ally(p, e) && !(e instanceof Player)) continue;
            EffectGraph.entity(p, e, s, s.effects(), at, false);
            Vec3 push = e.position().subtract(at).multiply(1, 0, 1);
            if (push.lengthSqr() > 1e-4 && EffectGraph.canHarm(p, e)) { push = push.normalize().scale(.6); e.push(push.x, .25, push.z); e.hurtMarked = true; }
        }
        ring(p.serverLevel(), f, at.add(0, .3, 0), r, 40);
        ring(p.serverLevel(), f, at.add(0, .3, 0), r * .55, 20);
        SpellVfx.cue(p, s, SpellVfx.IMPACT, at, at, r, 12);
        SpellVfx.sound(p, s, at, true);
    }

    static void dome(ServerPlayer p, SpellFormula f) {
        Dome old = DOMES.remove(p.getUUID());
        if (old != null) old.live = false;
        ServerLevel level = p.serverLevel();
        Dome dome = new Dome(p.position(), domeRadius(f), domeTicks(f));
        DOMES.put(p.getUUID(), dome);
        SpellFormula sting = unshielded(f);
        while (sting.life() > 0) sting = sting.without(Element.LIFE);
        SpellDefinition bite = sting.size() > 0 ? elemental(p, sting) : null;
        int life = f.life();
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.6f);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, .8f);
        SpellRuntime.sustain(p, age -> {
            if (!dome.live || age >= dome.ticks || !p.isAlive() || p.level() != level) { DOMES.remove(p.getUUID(), dome); return true; }
            if (age % 3 == 0) shell(level, f, dome);
            AABB box = new AABB(dome.center, dome.center).inflate(dome.radius + 2);
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class, box)) deflect(p, level, dome, shot);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && !EffectGraph.ally(p, m) && EffectGraph.canHarm(p, m))) {
                Vec3 off = mob.position().subtract(dome.center).multiply(1, 0, 1);
                double dist = off.length();
                if (dist > dome.radius + .6) continue;
                Vec3 out = dist < 1e-3 ? p.getLookAngle().multiply(1, 0, 1).normalize() : off.scale(1 / dist);
                mob.setDeltaMovement(out.x * .55, Math.max(mob.getDeltaMovement().y, .12), out.z * .55);
                mob.hurtMarked = true;
                if (bite != null && age % 10 == 0) EffectGraph.entity(p, mob, bite, bite.effects(), dome.center, false);
            }
            if (life > 0 && age % 20 == 0)
                for (Player ally : level.getEntitiesOfClass(Player.class, box, e -> e.isAlive() && e.position().distanceTo(dome.center) < dome.radius)) {
                    ally.heal(life);
                    EpicMagic.fx(level, ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY() + 1, ally.getZ(), 4, .4, .5, .4, 0);
                }
            return false;
        });
    }
    private static void deflect(ServerPlayer p, ServerLevel level, Dome dome, Projectile shot) {
        if (shot.getOwner() == p || shot.getOwner() instanceof Player) return;
        Vec3 off = shot.position().subtract(dome.center);
        double dist = off.length();
        if (dist > dome.radius + 1 || dist < dome.radius - 1.6 || dist < 1e-3) return;
        Vec3 normal = off.scale(1 / dist), v = shot.getDeltaMovement();
        double inward = v.dot(normal);
        if (inward >= 0) return;
        shot.setDeltaMovement(v.subtract(normal.scale(2 * inward)).scale(.7));
        shot.setOwner(p);
        shot.hurtMarked = true;
        EpicMagic.fx(level, ParticleTypes.ENCHANTED_HIT, shot.getX(), shot.getY(), shot.getZ(), 8, .1, .1, .1, .2);
        level.playSound(null, shot.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, .8f, 1.4f);
    }
    public static Dome dome(Player p) { return DOMES.get(p.getUUID()); }

    public static void imbue(ServerPlayer p, SpellDefinition recipe) {
        SpellDefinition s = elemental(p, recipe.formula());
        int hits = 3 + recipe.formula().size();
        IMBUES.put(p.getUUID(), new Imbue(s, hits, p.serverLevel().getGameTime() + IMBUE_TICKS));
        p.swing(InteractionHand.MAIN_HAND, true);
        SpellVfx.hand(p, recipe.formula(), 20);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.3f);
        p.displayClientMessage(Component.translatable("hud.magicka.imbued", hits), true);
    }
    public static Imbue imbued(Player p) { return IMBUES.get(p.getUUID()); }
    static void strike(ServerPlayer p, LivingEntity target) {
        Imbue imbue = IMBUES.get(p.getUUID());
        if (imbue == null || target == p || !target.isAlive()) return;
        if (p.serverLevel().getGameTime() > imbue.until) { IMBUES.remove(p.getUUID()); return; }
        target.invulnerableTime = 0;
        EffectGraph.entity(p, target, imbue.spell, imbue.spell.effects(), p.position(), false);
        Vec3 at = target.getBoundingBox().getCenter();
        SpellVfx.cue(p, imbue.spell, SpellVfx.IMPACT, at, at, 1.2, 8);
        if (imbue.hits > 1) IMBUES.put(p.getUUID(), new Imbue(imbue.spell, imbue.hits - 1, imbue.until));
        else { IMBUES.remove(p.getUUID()); p.displayClientMessage(Component.translatable("hud.magicka.imbue_spent"), true); }
    }

    public static void selfWard(ServerPlayer p, SpellDefinition recipe) {
        if (recipe.formula().shield() > 0) ward(p, recipe.formula(), WARD_TICKS);
    }
    public static void ward(ServerPlayer p, SpellFormula f, int ticks) {
        WARDS.put(p.getUUID(), new Ward(f, p.serverLevel().getGameTime() + ticks));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_DIAMOND.value(), SoundSource.PLAYERS, 1f, 1.5f);
        Component list = Component.empty();
        boolean first = true;
        for (Element e : Element.values()) if (e != Element.SHIELD && f.count(e) > 0) { if (!first) list.getSiblings().add(Component.literal(", ")); list.getSiblings().add(Component.translatable(e.key())); first = false; }
        p.displayClientMessage(first ? Component.translatable("hud.magicka.ward_bare") : Component.translatable("hud.magicka.ward", list), true);
    }
    public static float resist(LivingEntity e, Element element) {
        Ward w = WARDS.get(e.getUUID());
        if (w == null || e.level().getGameTime() > w.until) return 0;
        SpellFormula rest = unshielded(w.formula);
        if (rest.size() == 0) return element == Element.EARTH ? Math.min(.6f, BARE_WARD * w.formula.shield()) : 0;
        return element == Element.SHIELD ? 0 : Math.min(WARD_CAP, WARD_PER_ELEMENT * rest.total(element));
    }
    public static float resist(LivingEntity e, DamageSource source) {
        SpellDefinition spell = MagicEnemies.hurting;
        if (spell != null) {
            float sum = 0; int parts = 0;
            for (Element x : Element.BASE) { sum += spell.formula().total(x) * resist(e, x); parts += spell.formula().total(x); }
            return parts == 0 ? 0 : sum / parts;
        }
        Element element = element(source);
        return element == null ? 0 : resist(e, element);
    }
    static Element element(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) return Element.FIRE;
        if (source.is(DamageTypeTags.IS_FREEZING)) return Element.COLD;
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return Element.LIGHTNING;
        if (source.is(DamageTypeTags.IS_DROWNING)) return Element.WATER;
        if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL)) return Element.DEATH;
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) return Element.ARCANE;
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) && !source.is(DamageTypeTags.IS_EXPLOSION)) return null;
        return Element.EARTH;
    }

    static void ring(ServerLevel level, SpellFormula f, Vec3 at, double r, int points) {
        var elements = f.elements();
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            int color = elements.get(i % elements.size()).color;
            EpicMagic.fx(level, dust(color, 1.4f), at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r, 1, .05, .05, .05, 0);
        }
    }
    private static void shell(ServerLevel level, SpellFormula f, Dome dome) {
        var elements = f.elements();
        for (int i = 0; i < 26; i++) {
            double yaw = level.random.nextDouble() * Math.PI * 2, pitch = Math.acos(level.random.nextDouble());
            Vec3 at = dome.center.add(Math.cos(yaw) * Math.sin(pitch) * dome.radius, Math.cos(pitch) * dome.radius, Math.sin(yaw) * Math.sin(pitch) * dome.radius);
            int color = i % 2 == 0 ? Element.SHIELD.color : elements.get(i % elements.size()).color;
            EpicMagic.fx(level, dust(color, 1.2f), at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }
    private static DustParticleOptions dust(int color, float size) {
        return new DustParticleOptions(new org.joml.Vector3f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f), size);
    }

    @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
        float r = resist(event.getEntity(), event.getSource());
        if (r <= 0) return;
        event.setAmount(event.getAmount() * (1 - r));
        LivingEntity e = event.getEntity();
        if (e.level() instanceof ServerLevel level) EpicMagic.fx(level, ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + 1, e.getZ(), 6, .3, .4, .3, .1);
    }
    @SubscribeEvent public void struck(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (source.getDirectEntity() instanceof ServerPlayer p && source.getEntity() == p && source.is(DamageTypes.PLAYER_ATTACK)) strike(p, event.getEntity());
    }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        Imbue imbue = IMBUES.get(p.getUUID());
        if (imbue != null) {
            if (p.serverLevel().getGameTime() > imbue.until) { IMBUES.remove(p.getUUID()); p.displayClientMessage(Component.translatable("hud.magicka.imbue_spent"), true); }
            else SpellVfx.hand(p, imbue.spell.formula(), 4);
        }
        Ward ward = WARDS.get(p.getUUID());
        if (ward != null && p.serverLevel().getGameTime() > ward.until) WARDS.remove(p.getUUID());
        else if (ward != null && p.tickCount % 20 == 0) ring(p.serverLevel(), ward.formula, p.position().add(0, 1, 0), .9, 12);
    }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { forget(event.getEntity().getUUID()); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { forget(event.getEntity().getUUID()); }
    @SubscribeEvent public void stop(ServerStoppedEvent event) { IMBUES.clear(); WARDS.clear(); DOMES.values().forEach(d -> d.live = false); DOMES.clear(); }
    private static void forget(UUID id) {
        IMBUES.remove(id); WARDS.remove(id);
        Dome dome = DOMES.remove(id);
        if (dome != null) dome.live = false;
    }
}
