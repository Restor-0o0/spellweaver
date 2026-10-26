package com.restor.magicka;

import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.math.Vector3f;
import java.util.*;

/** Signature spells borrowed from pop culture; damage and support still come from each spell's effect graph. */
public final class LegendMagic {
    static final String STOPPED_TAG = "magicka:time_stop";
    private static final Set<UUID> STOPPED = new HashSet<>();
    static final Map<UUID,UUID> CHARMED = new HashMap<>();
    static final Map<UUID,UUID> CONFUSED = new HashMap<>();
    private static final BlockState[] FLOWERS = {
            Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(),
            Blocks.AZURE_BLUET.defaultBlockState(), Blocks.ALLIUM.defaultBlockState(), Blocks.LILY_OF_THE_VALLEY.defaultBlockState(), Blocks.PINK_TULIP.defaultBlockState(),
            Blocks.WHITE_TULIP.defaultBlockState(), Blocks.BLUE_ORCHID.defaultBlockState()};
    private static final BlockState[] GRASSES = {Blocks.GRASS.defaultBlockState(), Blocks.GRASS.defaultBlockState(), Blocks.FERN.defaultBlockState()};
    private static final BlockState[] SAPLINGS = {Blocks.OAK_SAPLING.defaultBlockState(), Blocks.BIRCH_SAPLING.defaultBlockState(), Blocks.SPRUCE_SAPLING.defaultBlockState(), Blocks.DARK_OAK_SAPLING.defaultBlockState()};

    static boolean handles(SpellDefinition s, boolean self) {
        return s.delivery().has("legend") && (!self || s.selfMode().equals("legend"));
    }
    static void cast(ServerPlayer p, SpellDefinition s, boolean self) {
        switch (SpellDefinition.string(s.delivery(),"legend","")) {
            case "flowers", "meadow", "grove" -> flowers(p,s,self);
            case "zoltraak" -> { if (self) defend(p,s); else zoltraak(p,s); }
            case "kamehameha" -> kamehameha(p,s,self);
            case "rasengan" -> rasengan(p,s);
            case "chidori" -> chidori(p,s);
            case "expelliarmus" -> expelliarmus(p,s);
            case "patronus" -> patronus(p,s,self);
            case "shout" -> shout(p,s,self);
            case "explosion" -> explosion(p,s,self);
            case "time_stop" -> timeStop(p,s);
            case "shall_not_pass" -> shallNotPass(p,s,self);
            case "defibrillator" -> LegendLife.defibrillator(p,s,self);
            case "life_drain" -> LegendLife.lifeDrain(p,s,self);
            case "purifying_flame" -> LegendLife.purifyingFlame(p,s,self);
            case "cryosleep" -> LegendLife.cryosleep(p,s,self);
            case "phoenix" -> LegendLife.phoenix(p,s,self);
            case "kaioken" -> LegendLife.kaioken(p,s);
            case "protego" -> LegendLife.protego(p,s,self);
            case "zombie_cure" -> LegendLife.zombieCure(p,s,self);
            case "wellspring" -> LegendLife.wellspring(p,s,self);
            case "amaterasu" -> LegendArts.amaterasu(p,s,self);
            case "mjolnir" -> LegendArts.mjolnir(p,s,self);
            case "accio" -> LegendArts.accio(p,s,self);
            case "infinity" -> LegendArts.infinity(p,s,self);
            case "aura_scale" -> LegendArts.auraScale(p,s);
            case "judradjim" -> LegendArts.judradjim(p,s,self);
            case "igni" -> LegendArts.igni(p,s,self);
            case "aard" -> LegendArts.aard(p,s,self);
            case "quen" -> LegendArts.quen(p,s);
            case "axii" -> LegendArts.axii(p,s);
            case "yrden" -> LegendArts.yrden(p,s,self);
            case "portal" -> LegendArts.portal(p,s,self);
            default -> throw new IllegalStateException("Unknown legend " + SpellDefinition.string(s.delivery(),"legend",""));
        }
    }

    private static void flowers(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.getLevel();
        Vec3 center = self ? p.position() : EpicMagic.focus(p);
        int radius = (int)Math.round(Mth.clamp(s.number("radius",12),2,16));
        BlockPos base = new BlockPos(center);
        List<BlockPos> ground = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx*dx+dz*dz > radius*radius) continue;
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos at = base.offset(dx,dy,dz);
                BlockState above = level.getBlockState(at.above());
                if (level.getBlockState(at).is(BlockTags.DIRT) && (above.isAir() || above.is(Blocks.GRASS))) { ground.add(at); break; }
            }
        }
        ground.sort(Comparator.comparingDouble(b -> b.distSqr(base)));
        RandomSource random = level.random;
        String kind = SpellDefinition.string(s.delivery(),"legend","flowers");
        List<BlockPos> trees = new ArrayList<>();
        int maxTrees = (int)Mth.clamp(s.number("trees",6),0,16);
        sound(level,center,SoundEvents.AMETHYST_BLOCK_CHIME,1.2f,1.4f);
        SpellRuntime.repeat(p,Math.max(24,(int)s.number("duration_ticks",200)),age -> {
            if (age < 20) {
                for (int i = ground.size()*age/20; i < ground.size()*(age+1)/20; i++) bloom(p,ground.get(i),random,kind,trees,maxTrees);
                if (age % 4 == 0) sound(level,center,SoundEvents.AMETHYST_BLOCK_CHIME,.8f,1.2f+age*.04f);
            }
            if (age % 3 == 0) EpicMagic.fx(level,kind.equals("flowers") ? ParticleTypes.FALLING_SPORE_BLOSSOM : ParticleTypes.SPORE_BLOSSOM_AIR,center.x,center.y+2.5,center.z,10,radius*.6,1,radius*.6,0);
            if (age % 20 == 0) for (LivingEntity e : withCaster(p,near(p,center,radius),center,radius))
                if (EffectGraph.ally(p,e)) { EffectGraph.entity(p,e,s,s.effects(),center,false); EpicMagic.fx(level,ParticleTypes.HEART,e.getX(),e.getY()+e.getBbHeight()+.3,e.getZ(),2,.3,.2,.3,0); }
        });
    }
    private static net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?,?> biomeTree(ServerLevel level, BlockPos at, RandomSource random) {
        List<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?,?>> found = new ArrayList<>();
        for (var step : level.getBiome(at).value().getGenerationSettings().features()) for (var placed : step) {
            var feature = placed.value().feature().value();
            if (placed.value().getFeatures().anyMatch(f -> f.feature() instanceof net.minecraft.world.level.levelgen.feature.TreeFeature)) found.add(feature);
        }
        return found.isEmpty() ? null : found.get(random.nextInt(found.size()));
    }
    private static void bloom(ServerPlayer p, BlockPos soil, RandomSource random, String kind, List<BlockPos> trees, int maxTrees) {
        ServerLevel level = p.getLevel();
        boolean grove = kind.equals("grove");
        if (random.nextFloat() > (grove ? .3f : .85f)) return;
        if (grove && (trees.size() >= maxTrees || trees.stream().anyMatch(t -> t.distSqr(soil) <= 16))) return;
        if (level.getBlockState(soil).is(Blocks.DIRT)) WorldMagic.replace(p,soil,Blocks.GRASS_BLOCK.defaultBlockState());
        BlockPos at = soil.above();
        if (grove) {
            BlockState sapling = SAPLINGS[random.nextInt(SAPLINGS.length)];
            if (sapling.canSurvive(level,at) && WorldMagic.replace(p,at,sapling) && level.getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.SaplingBlock tree) {
                tree.advanceTree(level,at,level.getBlockState(at),random);
                var wild = biomeTree(level,at,random);
                if (wild != null) {
                    level.setBlock(at,Blocks.AIR.defaultBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    if (!wild.place(level,level.getChunkSource().getGenerator(),random,at) && level.getBlockState(at).isAir()) level.setBlockAndUpdate(at,sapling);
                }
                for (int i = 0; i < 6 && level.getBlockState(at).getBlock() == tree; i++) tree.advanceTree(level,at,level.getBlockState(at),random);
                trees.add(soil);
                sound(level,Vec3.atCenterOf(at),SoundEvents.AZALEA_LEAVES_PLACE,1f,.8f);
                EpicMagic.fx(level,ParticleTypes.HAPPY_VILLAGER,at.getX()+.5,at.getY()+2,at.getZ()+.5,20,1.2,1.5,1.2,0);
            }
            return;
        }
        BlockState[] pool = kind.equals("meadow") ? GRASSES : FLOWERS;
        BlockState plant = pool[random.nextInt(pool.length)];
        if (plant.canSurvive(level,at) && WorldMagic.replace(p,at,plant))
            EpicMagic.fx(level,ParticleTypes.HAPPY_VILLAGER,soil.getX()+.5,soil.getY()+1.3,soil.getZ()+.5,3,.25,.2,.25,0);
    }

    private static final SpellFormula WARD_SIGIL = new SpellFormula(0,0,0,0,0,0,1);
    private static Vec3 rayOffset(int ray, int rays) {
        double a = ray*Math.PI*2/rays+.4;
        return new Vec3(1.1,Math.sin(a)*1.1,Math.cos(a)*1.1);
    }
    private static Vec3 rayOrigin(ServerPlayer p, int ray, int rays) {
        Vec3 look = p.getLookAngle(), side = side(look), up = side.cross(look).normalize(), o = rayOffset(ray,rays);
        return p.getEyePosition().add(look.scale(o.x)).add(up.scale(o.y)).add(side.scale(o.z));
    }
    private static void zoltraak(ServerPlayer p, SpellDefinition s) {
        ServerLevel level = p.getLevel();
        int rays = 5, charge = 6; double range = 64;
        announce(p,24,"magicka.legend.zoltraak");
        for (int i = 0; i < rays; i++) SpellVfx.attached(p,s.formula(),rayOffset(i,rays),new Vec3(1,0,0),.45,charge+rays*3+4);
        sound(level,p.getEyePosition(),SoundEvents.BEACON_POWER_SELECT,.8f,1.8f);
        SpellRuntime.repeat(p,charge+rays*3,age -> {
            if (age < charge || (age-charge) % 3 != 0) return;
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), from = rayOrigin(p,(age-charge)/3,rays);
            LivingEntity marked = SpellRuntime.target(p);
            Vec3 aim = marked != null && foe(p,marked) ? marked.getBoundingBox().getCenter() : SpellAim.hit(p).getLocation();
            if (aim.distanceToSqr(eye) > range*range || aim.distanceToSqr(eye) < 1) aim = eye.add(look.scale(range));
            Vec3 dir = aim.subtract(from).normalize(), to = reach(p,from,dir,range);
            line(level,from,to,.5,dust(0xF4EEFF,.8f));
            SpellVfx.cue(p,s,SpellVfx.BEAM,from,to,.08,6);
            for (LivingEntity e : segment(p,from,to,.35)) if (foe(p,e)) { e.invulnerableTime = 0; EffectGraph.entity(p,e,s,s.effects(),from,false); }
            sound(level,from,SoundEvents.AMETHYST_BLOCK_CHIME,.9f,1.9f);
            sound(level,from,SoundEvents.ILLUSIONER_CAST_SPELL,.6f,1.6f);
        });
    }
    private static final Map<UUID,Long> WARDS = new HashMap<>();
    private static void defend(ServerPlayer p, SpellDefinition s) {
        ServerLevel level = p.getLevel();
        int duration = Mth.clamp((int)s.number("ward_ticks",100),20,400);
        WARDS.put(p.getUUID(),level.getGameTime()+duration);
        sound(level,p.getEyePosition(),SoundEvents.BEACON_ACTIVATE,1f,1.8f);
        SpellRuntime.repeat(p,duration,age -> {
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), center = eye.add(look.scale(1.4));
            if (age % 2 == 0) SpellVfx.attached(p,WARD_SIGIL,new Vec3(1.4,0,0),new Vec3(1,0,0),1.4,Math.min(6,duration-age));
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class,new AABB(center,center).inflate(2.5),e -> e.isAlive() && e.getOwner() != p)) {
                Vec3 rel = shot.position().subtract(eye);
                if (rel.dot(look) < .3 || rel.lengthSqr() > 12) continue;
                EpicMagic.fx(level,ParticleTypes.END_ROD,shot.getX(),shot.getY(),shot.getZ(),8,.1,.1,.1,.08);
                sound(level,shot.position(),SoundEvents.AMETHYST_BLOCK_HIT,1f,1.4f);
                shot.discard();
            }
        });
    }
    public static void ward(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Long until = WARDS.get(p.getUUID());
        if (until == null || p.getLevel().getGameTime() > until || event.getSource().getEntity() == p) return;
        Vec3 from = event.getSource().getSourcePosition();
        if (from == null || from.subtract(p.getEyePosition()).normalize().dot(p.getLookAngle()) < .35) return;
        event.setCanceled(true);
        Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(1.4));
        EpicMagic.fx(p.getLevel(),ParticleTypes.END_ROD,at.x,at.y,at.z,10,.4,.4,.4,.06);
        sound(p.getLevel(),at,SoundEvents.AMETHYST_BLOCK_HIT,1.2f,1.2f);
    }

    private static void kamehameha(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.getLevel();
        int charge = 30, fire = 24;
        announce(p,32,"magicka.legend.kamehameha_charge");
        SpellRuntime.repeat(p,charge+fire,age -> {
            Vec3 dir = self ? new Vec3(0,-1,0) : p.getLookAngle();
            Vec3 hand = self ? p.position().add(0,.1,0) : p.getEyePosition().add(dir.scale(.9)).add(0,-.3,0);
            if (self) p.fallDistance = 0;
            if (age < charge) {
                RandomSource r = level.random;
                for (int i = 0; i < 4; i++) {
                    Vec3 off = new Vec3(r.nextGaussian(),r.nextGaussian(),r.nextGaussian()).normalize().scale(1.6);
                    EpicMagic.fx(level,ParticleTypes.ELECTRIC_SPARK,hand.x+off.x,hand.y+off.y,hand.z+off.z,0,-off.x,-off.y,-off.z,.12);
                }
                EpicMagic.fx(level,dust(0x7FE8FF,.4f+age*.05f),hand.x,hand.y,hand.z,2,.08,.08,.08,0);
                if (age % 4 == 0) sound(level,hand,SoundEvents.AMETHYST_BLOCK_CHIME,1f,.5f+age*.05f);
                return;
            }
            int t = age-charge;
            if (t == 0) {
                announce(p,32,"magicka.legend.kamehameha");
                sound(level,hand,SoundEvents.WARDEN_SONIC_BOOM,2f,1.3f);
                sound(level,hand,SoundEvents.BEACON_ACTIVATE,1.5f,1.6f);
                if (self) { p.setDeltaMovement(0,1.7,0); p.hurtMarked = true; MagicStatuses.apply(p,MagicStatuses.Status.SLOW_FALLING,100,1); }
            }
            Vec3 to = reach(p,hand,dir,self ? 14 : 64);
            line(level,hand,to,.6,dust(0x6FD8FF,2.4f));
            line(level,hand,to,1.2,ParticleTypes.END_ROD);
            EpicMagic.fx(level,ParticleTypes.EXPLOSION,to.x,to.y,to.z,1,.3,.3,.3,0);
            if (t % 2 == 0) SpellVfx.cue(p,s,SpellVfx.BEAM,hand,to,.6,3);
            if (t % 4 == 0) for (LivingEntity e : segment(p,hand,to,1.3)) if (foe(p,e)) {
                EffectGraph.entity(p,e,s,s.effects(),hand,false);
                e.push(dir.x*.6,.15,dir.z*.6); e.hurtMarked = true;
            }
            if (t % 6 == 0) sound(level,hand,SoundEvents.FIRECHARGE_USE,1f,.6f);
        });
    }

    private static void rasengan(ServerPlayer p, SpellDefinition s) {
        ServerLevel level = p.getLevel();
        announce(p,24,"magicka.legend.rasengan");
        sound(level,p.position(),SoundEvents.ELYTRA_FLYING,.5f,2f);
        SpellRuntime.sustain(p,age -> {
            Vec3 look = p.getLookAngle();
            Vec3 hand = p.getEyePosition().add(look.scale(.9)).add(0,-.35,0);
            p.fallDistance = 0;
            if (age < 10 && age % 2 == 0) { p.setDeltaMovement(look.x*1.1,Math.max(p.getDeltaMovement().y,look.y*.6+.05),look.z*1.1); p.hurtMarked = true; }
            for (int i = 0; i < 6; i++) {
                double a = age*.9+i*Math.PI/3;
                EpicMagic.fx(level,dust(0x9AD4FF,.8f),hand.x+Math.cos(a)*.35,hand.y+Math.sin(a*1.3)*.2,hand.z+Math.sin(a)*.35,1,0,0,0,0);
            }
            EpicMagic.fx(level,ParticleTypes.END_ROD,hand.x,hand.y,hand.z,1,.05,.05,.05,0);
            LivingEntity target = near(p,hand,1.6).stream().filter(e -> foe(p,e)).min(Comparator.comparingDouble(e -> e.distanceToSqr(hand))).orElse(null);
            if (target != null) {
                EffectGraph.entity(p,target,s,s.effects(),hand,false);
                Vec3 push = flat(look);
                target.push(push.x*2.2,.35,push.z*2.2); target.hurtMarked = true;
                for (int i = 0; i < 36; i++) {
                    double a = i*Math.PI*2/12;
                    EpicMagic.fx(level,ParticleTypes.CLOUD,hand.x,hand.y,hand.z,0,Math.cos(a),(i/12-1)*.3,Math.sin(a),.35+i/12*.1);
                }
                sound(level,hand,SoundEvents.GENERIC_EXPLODE,1.2f,1.5f);
                return true;
            }
            if (age >= 14) { EpicMagic.fx(level,ParticleTypes.POOF,hand.x,hand.y,hand.z,8,.2,.2,.2,.02); return true; }
            return false;
        });
    }

    private static void chidori(ServerPlayer p, SpellDefinition s) {
        ServerLevel level = p.getLevel();
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x,Mth.clamp(look.y,-.3,.3),look.z).normalize();
        Set<UUID> struck = new HashSet<>();
        int windup = 6;
        announce(p,24,"magicka.legend.chidori");
        SpellRuntime.sustain(p,age -> {
            Vec3 hand = p.position().add(0,.9,0).add(dir.scale(.6));
            EpicMagic.fx(level,ParticleTypes.ELECTRIC_SPARK,hand.x,hand.y,hand.z,8,.25,.25,.25,.15);
            if (age % 2 == 0) sound(level,hand,SoundEvents.PARROT_AMBIENT,1f,1.6f+level.random.nextFloat()*.4f);
            if (age < windup) return false;
            p.fallDistance = 0;
            p.setDeltaMovement(dir.scale(1.5)); p.hurtMarked = true;
            EpicMagic.fx(level,dust(0xCFE8FF,1.2f),p.getX(),p.getY()+1,p.getZ(),4,.3,.4,.3,0);
            for (LivingEntity e : near(p,p.position().add(0,1,0),1.8)) if (foe(p,e) && struck.add(e.getUUID())) {
                EffectGraph.entity(p,e,s,s.effects(),p.position(),false);
                SpellVfx.cue(p,s,SpellVfx.ARC,hand,e.getBoundingBox().getCenter(),.15,10);
                sound(level,e.position(),SoundEvents.LIGHTNING_BOLT_IMPACT,1f,1.4f);
            }
            if (age >= windup+8 || age > windup && p.horizontalCollision) { p.setDeltaMovement(p.getDeltaMovement().scale(.2)); p.hurtMarked = true; return true; }
            return false;
        });
    }

    private static void expelliarmus(ServerPlayer p, SpellDefinition s) {
        ServerLevel level = p.getLevel();
        Vec3[] bolt = {p.getEyePosition().add(p.getLookAngle().scale(.8)), p.getLookAngle().scale(2.2)};
        announce(p,24,"magicka.legend.expelliarmus");
        sound(level,bolt[0],SoundEvents.ILLUSIONER_CAST_SPELL,1f,1.4f);
        SpellRuntime.sustain(p,age -> {
            Vec3 from = bolt[0], to = from.add(bolt[1]);
            var block = level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
            Vec3 end = to;
            line(level,from,end,.3,dust(0xFF2A2A,1.3f));
            LivingEntity victim = segment(p,from,end,.4).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
            if (victim != null) { disarm(p,s,victim,from); return true; }
            if (block.getType() != HitResult.Type.MISS || age >= 20) { EpicMagic.fx(level,ParticleTypes.CRIT,end.x,end.y,end.z,10,.2,.2,.2,.2); return true; }
            bolt[0] = end;
            return false;
        });
    }
    private static void disarm(ServerPlayer p, SpellDefinition s, LivingEntity victim, Vec3 from) {
        ServerLevel level = p.getLevel();
        EffectGraph.entity(p,victim,s,s.effects(),from,false);
        Vec3 at = victim.getBoundingBox().getCenter();
        EpicMagic.fx(level,dust(0xFF2A2A,1.5f),at.x,at.y,at.z,20,.4,.5,.4,0);
        sound(level,at,SoundEvents.ANVIL_LAND,.5f,2f);
        ItemStack held = victim.getMainHandItem();
        if (!foe(p,victim) || held.isEmpty()) return;
        victim.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        ItemEntity item = new ItemEntity(level,victim.getX(),victim.getEyeY()-.3,victim.getZ(),held.copy());
        Vec3 d = p.position().subtract(item.position());
        item.setDeltaMovement(d.x*.1,d.y*.1+Math.sqrt(Math.sqrt(d.lengthSqr()))*.08,d.z*.1);
        item.setPickUpDelay(10);
        level.addFreshEntity(item);
    }

    private static void patronus(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.getLevel();
        Vec3[] pos = {p.position().add(flat(p.getLookAngle()).scale(1.5))}, dir = {flat(p.getLookAngle())};
        Map<UUID,Integer> cooldown = new HashMap<>();
        double hunt = Mth.clamp(s.number("radius",20),6,48), turn = .2, speed = .65;
        announce(p,32,"magicka.legend.patronus");
        sound(level,pos[0],SoundEvents.BEACON_ACTIVATE,1.2f,1.5f);
        SpellRuntime.repeat(p,Math.max(20,(int)s.number("duration_ticks",160)),age -> {
            Vec3 here = pos[0];
            LivingEntity prey = near(p,here,hunt).stream()
                    .filter(e -> foe(p,e) && cooldown.getOrDefault(e.getUUID(),-99) <= age && (!self || e.distanceToSqr(p) < 144))
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(here))).orElse(null);
            Vec3 goal = prey != null ? prey.position() : self ? p.position().add(Math.cos(age*.12)*4,0,Math.sin(age*.12)*4) : null;
            Vec3 want = goal != null ? flat(goal.subtract(here)) : flat(dir[0].add(new Vec3(-dir[0].z,0,dir[0].x).scale(Math.sin(age*.15)*.15)));
            dir[0] = flat(dir[0].scale(1-turn).add(want.scale(turn)));
            Vec3 step = here.add(dir[0].scale(speed));
            Vec3 ground = EpicMagic.drop(level,new Vec3(step.x,here.y+2.5,step.z),6,p);
            if (ground == null || ground.y > here.y+1.6) { dir[0] = new Vec3(-dir[0].z,0,dir[0].x).scale(ground == null ? -1 : 1); return; }
            pos[0] = new Vec3(step.x,ground.y,step.z);
            Vec3 at = pos[0];
            if (age % 2 == 0) stag(level,at,dir[0],age);
            if (age % 6 == 0) sound(level,at,SoundEvents.HORSE_GALLOP,.8f,1.4f);
            if (age % 10 == 0) sound(level,at,SoundEvents.AMETHYST_BLOCK_CHIME,1f,1.6f);
            for (LivingEntity e : near(p,at.add(0,1,0),2.2)) {
                if (EffectGraph.ally(p,e)) { MagicStatuses.remove(e,MagicStatuses.Status.WITHERING); MagicStatuses.remove(e,MagicStatuses.Status.OBSCURED); continue; }
                if (!foe(p,e) || cooldown.getOrDefault(e.getUUID(),-99) > age) continue;
                cooldown.put(e.getUUID(),age+20);
                EffectGraph.entity(p,e,s,s.effects(),at,false);
                if (e.getMobType() == net.minecraft.world.entity.MobType.UNDEAD) EffectGraph.entity(p,e,s,s.effects(),at,false);
                Vec3 away = flat(e.position().subtract(at).add(dir[0].scale(1.5)));
                e.push(away.x*1.4,.45,away.z*1.4); e.hurtMarked = true;
                sound(level,at,SoundEvents.PLAYER_ATTACK_KNOCKBACK,1f,.9f);
                EpicMagic.fx(level,ParticleTypes.END_ROD,e.getX(),e.getY()+e.getBbHeight()*.6,e.getZ(),8,.3,.3,.3,.12);
            }
        });
    }
    private static void stag(ServerLevel level, Vec3 pos, Vec3 forward, int age) {
        Vec3 side = new Vec3(-forward.z,0,forward.x);
        ParticleOptions glow = dust(0xCFE6FF,.6f);
        java.util.function.BiConsumer<double[],double[]> bone = (a,b) -> line(level,local(pos,forward,side,a),local(pos,forward,side,b),.14,glow);
        for (double s : new double[]{-.18,0,.18}) bone.accept(new double[]{-.6,1.05,s},new double[]{.6,1.15,s});
        bone.accept(new double[]{-.5,1.3,0},new double[]{.5,1.35,0});
        bone.accept(new double[]{.6,1.2,0},new double[]{.9,1.7,0});
        bone.accept(new double[]{.9,1.7,0},new double[]{1.25,1.6,0});
        for (double s : new double[]{-1,1}) {
            bone.accept(new double[]{.9,1.85,.08*s},new double[]{.75,2.45,.35*s});
            bone.accept(new double[]{.8,2.15,.2*s},new double[]{1.05,2.35,.3*s});
            bone.accept(new double[]{.75,2.45,.35*s},new double[]{.6,2.6,.5*s});
            bone.accept(new double[]{.75,2.45,.35*s},new double[]{.95,2.7,.4*s});
        }
        bone.accept(new double[]{-.6,1.2,0},new double[]{-.78,1.0,0});
        for (int leg = 0; leg < 4; leg++) {
            double f = leg < 2 ? .45 : -.45, s = leg % 2 == 0 ? .16 : -.16, phase = age*.8+(leg == 0 || leg == 3 ? 0 : Math.PI);
            bone.accept(new double[]{f,1.0,s},new double[]{f+Math.sin(phase)*.3,Math.max(0,Math.cos(phase))*.2,s});
        }
        Vec3 heart = local(pos,forward,side,new double[]{.2,1.2,0});
        EpicMagic.fx(level,ParticleTypes.END_ROD,heart.x,heart.y,heart.z,2,.5,.3,.5,.01);
    }
    private static Vec3 local(Vec3 pos, Vec3 forward, Vec3 side, double[] v) {
        return pos.add(forward.scale(v[0])).add(0,v[1],0).add(side.scale(v[2]));
    }

    private static void shout(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.getLevel();
        Vec3 origin = p.getEyePosition().add(0,-.3,0), dir = p.getLookAngle();
        double range = Mth.clamp(s.number("radius",10),3,24), cone = Math.cos(Math.toRadians(35));
        Set<UUID> hit = new HashSet<>();
        announce(p,32,"magicka.legend.shout");
        sound(level,origin,SoundEvents.RAVAGER_ROAR,2f,.7f);
        sound(level,origin,SoundEvents.ENDER_DRAGON_GROWL,1f,1.6f);
        SpellRuntime.repeat(p,8,age -> {
            double r = range*(age+1)/8;
            RandomSource random = level.random;
            for (int i = 0; i < 28; i++) {
                Vec3 v = new Vec3(random.nextGaussian(),random.nextGaussian()*(self ? .3 : 1),random.nextGaussian()).normalize();
                if (!self) { v = dir.add(v.scale(.55)).normalize(); if (v.dot(dir) < cone) continue; }
                Vec3 at = origin.add(v.scale(r));
                EpicMagic.fx(level,ParticleTypes.CLOUD,at.x,at.y,at.z,0,v.x,v.y,v.z,.35);
                if (i % 4 == 0) EpicMagic.fx(level,ParticleTypes.SWEEP_ATTACK,at.x,at.y,at.z,1,0,0,0,0);
            }
            for (LivingEntity e : near(p,origin,r)) {
                Vec3 to = e.getBoundingBox().getCenter().subtract(origin);
                if (e == p || hit.contains(e.getUUID()) || !self && to.normalize().dot(dir) < cone && to.lengthSqr() > 1) continue;
                hit.add(e.getUUID());
                EffectGraph.entity(p,e,s,s.effects(),origin,false);
                if (!EffectGraph.canHarm(p,e)) continue;
                Vec3 v = to.lengthSqr() < .01 ? dir : to.normalize();
                double power = 2.4*(1-to.length()/range*.5);
                e.push(v.x*power,.45+Math.max(0,v.y)*power*.5,v.z*power); e.hurtMarked = true;
            }
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class,new AABB(origin,origin).inflate(r))) {
                Vec3 to = shot.position().subtract(origin);
                if (shot.getOwner() == p || !self && to.normalize().dot(dir) < cone) continue;
                shot.setDeltaMovement(to.normalize().scale(1.5)); shot.hurtMarked = true;
            }
        });
    }

    private static void explosion(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.getLevel();
        Vec3 target = self ? p.position() : EpicMagic.focus(p);
        double radius = Mth.clamp(s.number("radius",8),3,16);
        int charge = 40;
        announce(p,32,"magicka.legend.explosion_chant");
        SpellVfx.cue(p,s,SpellVfx.TELEGRAPH,target,target,radius,charge+10);
        sound(level,target,SoundEvents.BEACON_POWER_SELECT,2f,.6f);
        SpellRuntime.repeat(p,charge+30,age -> {
            if (age < charge) {
                if (age % 2 == 0) for (int ring = 0; ring < 3; ring++)
                    circle(level,target.add(0,2+ring*3.5,0),radius*(1-ring*.25),age*.06*(ring % 2 == 0 ? 1 : -1),ring == 1 ? 0xFFA229 : 0xFF3A1F);
                if (age % 5 == 0) sound(level,target,SoundEvents.AMETHYST_BLOCK_CHIME,2f,.5f+age*.03f);
                return;
            }
            int t = age-charge;
            if (t == 0) detonate(p,s,target,radius,self);
            double stem = Math.min(t*.6,12);
            EpicMagic.fx(level,ParticleTypes.CAMPFIRE_COSY_SMOKE,target.x,target.y+stem*.5,target.z,6,.6,stem*.25,.6,.01);
            if (t > 6) {
                double cap = 1.5+t*.12;
                EpicMagic.fx(level,ParticleTypes.LARGE_SMOKE,target.x,target.y+stem+1,target.z,14,cap,.6,cap,.02);
                EpicMagic.fx(level,ParticleTypes.FLAME,target.x,target.y+stem,target.z,4,cap*.5,.4,cap*.5,.02);
            }
        });
    }
    private static void detonate(ServerPlayer p, SpellDefinition s, Vec3 target, double radius, boolean self) {
        ServerLevel level = p.getLevel();
        for (int i = 0; i < 48; i++) EpicMagic.fx(level,dust(0xFFF2D0,3f),target.x,target.y+i*.5,target.z,3,.4,.2,.4,0);
        EpicMagic.fx(level,ParticleTypes.FLASH,target.x,target.y+1,target.z,3,.5,.5,.5,0);
        EpicMagic.fx(level,ParticleTypes.EXPLOSION_EMITTER,target.x,target.y+1,target.z,4,radius/3,1,radius/3,0);
        EpicMagic.fx(level,ParticleTypes.LAVA,target.x,target.y+1,target.z,40,radius/2,1,radius/2,0);
        sound(level,target,SoundEvents.GENERIC_EXPLODE,6f,.5f);
        sound(level,target,SoundEvents.LIGHTNING_BOLT_THUNDER,4f,.6f);
        if (self) { p.setDeltaMovement(0,2.2,0); p.hurtMarked = true; MagicStatuses.apply(p,MagicStatuses.Status.SLOW_FALLING,160,1); }
        SpellRuntime.impact(p,s,target,radius,false,true);
        announce(p,64,"magicka.legend.explosion");
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,160,3));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,160,1));
        ElementLocks.lock(p,0xFF,160);
        p.displayClientMessage(Component.translatable("magicka.legend.explosion_spent"),true);
    }
    private static void circle(ServerLevel level, Vec3 center, double radius, double spin, int color) {
        ParticleOptions glow = dust(color,1.4f);
        int points = (int)(radius*10);
        for (int i = 0; i < points; i++) {
            double a = spin+i*Math.PI*2/points;
            EpicMagic.fx(level,glow,center.x+Math.cos(a)*radius,center.y,center.z+Math.sin(a)*radius,1,0,0,0,0);
        }
        for (int i = 0; i < 5; i++) {
            double a = spin+i*Math.PI*2/5, b = spin+(i+2)*Math.PI*2/5;
            line(level,center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),center.add(Math.cos(b)*radius,0,Math.sin(b)*radius),.3,glow);
        }
    }

    private record Paused(Vec3 motion, boolean noGravity) {}
    private static void timeStop(ServerPlayer p, SpellDefinition s) {
        ServerLevel level = p.getLevel();
        Vec3 center = p.position();
        double radius = Mth.clamp(s.number("radius",16),4,32);
        List<UUID> held = new ArrayList<>();
        Map<Projectile,Paused> shots = new IdentityHashMap<>();
        announce(p,48,"magicka.legend.time_stop");
        sound(level,center,SoundEvents.BELL_BLOCK,3f,.5f);
        sound(level,center,SoundEvents.BEACON_DEACTIVATE,2f,.5f);
        SpellRuntime.timed(p,Mth.clamp((int)s.number("duration_ticks",100),20,400),age -> {
            if (age < 10) {
                double r = radius*(age+1)/10;
                RandomSource random = level.random;
                for (int i = 0; i < 60; i++) {
                    Vec3 v = new Vec3(random.nextGaussian(),random.nextGaussian(),random.nextGaussian()).normalize().scale(r);
                    EpicMagic.fx(level,dust(0x8C8FA8,1.6f),center.x+v.x,center.y+1+v.y,center.z+v.z,1,0,0,0,0);
                }
            }
            if (age % 20 == 10) sound(level,center,SoundEvents.NOTE_BLOCK_HAT,1.5f,.5f);
            AABB box = new AABB(center,center).inflate(radius);
            for (Mob mob : level.getEntitiesOfClass(Mob.class,box,m -> m.isAlive() && !m.isNoAi() && m.distanceToSqr(center) <= radius*radius && !EffectGraph.ally(p,m))) {
                mob.setNoAi(true); mob.getPersistentData().putBoolean(STOPPED_TAG,true); mob.setDeltaMovement(Vec3.ZERO); mob.hurtMarked = true;
                STOPPED.add(mob.getUUID()); held.add(mob.getUUID());
                EpicMagic.fx(level,ParticleTypes.REVERSE_PORTAL,mob.getX(),mob.getY()+mob.getBbHeight()/2,mob.getZ(),12,.3,.4,.3,.02);
            }
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class,box,e -> e.isAlive() && e.distanceToSqr(center) <= radius*radius))
                if (!shots.containsKey(shot)) shots.put(shot,new Paused(shot.getDeltaMovement(),shot.isNoGravity()));
            for (Projectile shot : shots.keySet()) { shot.setDeltaMovement(Vec3.ZERO); shot.setNoGravity(true); shot.hurtMarked = true; }
        },() -> {
            boolean running = p.getServer().isRunning();
            for (UUID id : held) {
                STOPPED.remove(id);
                if (level.getEntity(id) instanceof Mob mob) {
                    release(mob);
                    if (running && mob.isAlive()) EffectGraph.entity(p,mob,s,s.effects(),center,false);
                }
            }
            shots.forEach((shot,paused) -> { if (shot.isAlive()) { shot.setNoGravity(paused.noGravity()); shot.setDeltaMovement(paused.motion()); shot.hurtMarked = true; } });
            if (running) { announce(p,48,"magicka.legend.time_resume"); sound(level,center,SoundEvents.BELL_BLOCK,2f,1f); }
        });
    }
    private static void release(Mob mob) {
        if (!mob.getPersistentData().getBoolean(STOPPED_TAG)) return;
        mob.getPersistentData().remove(STOPPED_TAG);
        mob.setNoAi(false);
    }
    static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && !STOPPED.contains(mob.getUUID())) release(mob);
    }

    private static void shallNotPass(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.getLevel();
        Vec3 forward = flat(p.getLookAngle()), side = new Vec3(-forward.z,0,forward.x);
        Vec3 center = self ? p.position() : p.position().add(forward.scale(3));
        Vec3 ground = EpicMagic.drop(level,center.add(0,1.5,0),5,p);
        if (ground != null) center = ground;
        Vec3 c = center;
        double half = 4.5, ring = 4;
        Map<UUID,Integer> cooldown = new HashMap<>();
        for (ServerPlayer viewer : level.players()) if (viewer.distanceToSqr(p) < 32*32) viewer.displayClientMessage(Component.translatable("magicka.legend.shall_not_pass",p.getDisplayName()),true);
        sound(level,c,SoundEvents.ANVIL_LAND,1.5f,.5f);
        sound(level,c,SoundEvents.BEACON_ACTIVATE,1.5f,1.2f);
        EpicMagic.fx(level,ParticleTypes.FLASH,c.x,c.y+1,c.z,1,0,0,0,0);
        SpellRuntime.repeat(p,Math.max(20,(int)s.number("duration_ticks",160)),age -> {
            RandomSource random = level.random;
            if (age % 2 == 0) for (int i = 0; i < 26; i++) {
                double h = random.nextDouble()*2.8;
                Vec3 at = self ? c.add(Math.cos(i*Math.PI*2/26)*ring,h,Math.sin(i*Math.PI*2/26)*ring) : c.add(side.scale(-half+half*2*i/25)).add(0,h,0);
                EpicMagic.fx(level,dust(i % 3 == 0 ? 0xFFD76A : 0xFFFBEF,1f),at.x,at.y,at.z,1,0,0,0,0);
                if (i % 6 == 0) EpicMagic.fx(level,ParticleTypes.END_ROD,at.x,at.y,at.z,0,0,.04,0,1);
            }
            for (LivingEntity e : near(p,c,self ? ring+1.5 : half+2)) {
                if (!foe(p,e)) continue;
                Vec3 rel = e.position().subtract(c), push;
                if (self) {
                    double d = Math.sqrt(rel.x*rel.x+rel.z*rel.z);
                    if (d > ring+1.2) continue;
                    push = flat(rel);
                } else {
                    if (Math.abs(rel.dot(side)) > half+.5 || Math.abs(rel.dot(forward)) > 1.2) continue;
                    push = forward;
                }
                e.setDeltaMovement(push.x*.9,.3,push.z*.9); e.hurtMarked = true;
                if (cooldown.getOrDefault(e.getUUID(),-99) <= age) { cooldown.put(e.getUUID(),age+20); EffectGraph.entity(p,e,s,s.effects(),c,false); }
            }
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class,new AABB(c,c).inflate(self ? ring+1 : half+1,3,self ? ring+1 : half+1))) {
                if (shot.getOwner() == p || shot.getOwner() instanceof LivingEntity owner && EffectGraph.ally(p,owner)) continue;
                Vec3 rel = shot.position().subtract(c);
                boolean inside = self ? Math.abs(Math.sqrt(rel.x*rel.x+rel.z*rel.z)-ring) < 1 : Math.abs(rel.dot(side)) <= half && Math.abs(rel.dot(forward)) < 1;
                if (!inside) continue;
                EpicMagic.fx(level,ParticleTypes.FLASH,shot.getX(),shot.getY(),shot.getZ(),1,0,0,0,0);
                shot.discard();
            }
        });
    }

    static boolean charmedBy(LivingEntity e, ServerPlayer p) {
        return p.getUUID().equals(CHARMED.get(e.getUUID()));
    }
    static void target(LivingChangeTargetEvent event) {
        UUID id = event.getEntity().getUUID(), owner = CHARMED.get(id);
        LivingEntity next = event.getNewTarget();
        if (next == null) return;
        if (owner != null && (next instanceof Player || owner.equals(CHARMED.get(next.getUUID())))) event.setCanceled(true);
        else if (CONFUSED.containsKey(id) && next instanceof Player) event.setCanceled(true);
    }
    static List<JsonObject> graph(SpellDefinition s, String key) {
        return s.delivery().has(key) ? java.util.stream.StreamSupport.stream(s.delivery().getAsJsonArray(key).spliterator(),false).map(JsonElement::getAsJsonObject).toList() : List.of();
    }
    static LivingEntity aimed(ServerPlayer p, double range) {
        LivingEntity e = SpellRuntime.target(p);
        return e != null && e.distanceToSqr(p) <= range*range ? e : null;
    }

    static boolean foe(ServerPlayer p, LivingEntity e) {
        return e != p && e.isAlive() && !e.isSpectator() && EffectGraph.canHarm(p,e) && !EffectGraph.ally(p,e);
    }
    static List<LivingEntity> near(ServerPlayer p, Vec3 center, double radius) {
        return p.getLevel().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && SpellRuntime.boxDistanceSqr(e.getBoundingBox(),center) <= radius*radius);
    }
    static List<LivingEntity> withCaster(ServerPlayer p, List<LivingEntity> near, Vec3 center, double radius) {
        if (near.contains(p) || SpellRuntime.boxDistanceSqr(p.getBoundingBox(),center) > radius*radius) return near;
        var all = new ArrayList<>(near); all.add(p); return all;
    }
    static List<LivingEntity> segment(ServerPlayer p, Vec3 from, Vec3 to, double width) {
        return p.getLevel().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(width),e -> {
            if (e == p || !e.isAlive() || e.isSpectator()) return false;
            AABB box = e.getBoundingBox().inflate(width);
            return box.contains(from) || box.clip(from,to).isPresent();
        });
    }
    static Vec3 reach(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
        var hit = p.getLevel().clip(new ClipContext(from,from.add(dir.scale(range)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        return hit.getLocation();
    }
    static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x,0,v.z);
        return f.lengthSqr() < 1e-4 ? new Vec3(1,0,0) : f.normalize();
    }
    static Vec3 side(Vec3 look) {
        Vec3 s = look.cross(new Vec3(0,1,0));
        return s.lengthSqr() < 1e-4 ? new Vec3(1,0,0) : s.normalize();
    }
    static void line(ServerLevel level, Vec3 from, Vec3 to, double step, ParticleOptions type) {
        int count = (int)Math.min(200,from.distanceTo(to)/step);
        for (int i = 0; i <= count; i++) {
            Vec3 at = count == 0 ? from : from.lerp(to,(double)i/count);
            EpicMagic.fx(level,type,at.x,at.y,at.z,1,0,0,0,0);
        }
    }
    static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f((rgb >> 16 & 255)/255f,(rgb >> 8 & 255)/255f,(rgb & 255)/255f),Mth.clamp(size,.01f,4f));
    }
    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null,at.x,at.y,at.z,sound,SoundSource.PLAYERS,volume,pitch);
    }
    static void announce(ServerPlayer p, double radius, String key) {
        for (ServerPlayer viewer : p.getLevel().players()) if (viewer.distanceToSqr(p) < radius*radius) viewer.displayClientMessage(Component.translatable(key),true);
    }
    private LegendMagic() {}
}
