package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CastingServer {
    public enum Intent { PRIMARY, SELF, AREA, WEAPON }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    static final class Session {
        boolean enabled;
        Formula formula = new Formula();
        Intent intent = Intent.PRIMARY;
        int remaining, total, elapsed, attempts, placed;
        long inputTick = -1;
        int inputsThisTick;
        ServerLevel level;
        SpellDefinition recipe;
        int windup;
        Formula queued, last;
        long lastCast=-1000;
        int queueTicks, queueIndex, tongue;
        long lastQueue=-100, channelStart;
        boolean channeling, secret;
        Boolean instantOverride;
        boolean instant() { return instantOverride != null ? instantOverride : MagicConfig.INSTANT_CAST.get(); }
        final StringBuilder typed = new StringBuilder();
        UUID construction;
        BlockPos root;
        net.minecraft.world.phys.Vec3 anchor;
        EpicMagic.Channel epic;
        SpellBalance.Cast balance;
        boolean committed() { return remaining > 0; }
    }
    public static void input(ServerPlayer player, int action) {
        if (action < 0 || action > 16 || !player.isAlive() || player.isSpectator()) return;
        // Mode toggles stay live while polymorphed so the shape can never lock the controls out.
        if (action != 7 && action != 11 && action != 12 && PolymorphManager.polymorphed(player)) return;
        if (action == 14) { MagicGear.open(player); return; }
        Session s = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        long now = player.level().getGameTime();
        if (s.inputTick != now) { s.inputTick = now; s.inputsThisTick = 0; }
        if (++s.inputsThisTick > 12) return;
        if (action == 12) { if (s.channeling && s.epic == null && !s.recipe.shape().construction()) finishChannel(player, s); return; }
        if (s.committed()) return;
        if(s.queued!=null){s.queued=null;PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(0,0));}
        if (action == 7) s.enabled = !s.enabled;
        else if (action == 11) s.instantOverride = !s.instant();
        else if (s.enabled) {
            if (action < 7 || action == 13) {
                int element = action == 13 ? Element.DEATH.ordinal() : action;
                if (ElementLocks.locked(player, Element.values()[element])) { ElementLocks.reject(player, Element.values()[element]); sync(player, s); return; }
                if (s.formula.empty()) s.typed.setLength(0);
                var reaction = Reactions.apply(s.formula, s.typed, Element.values()[element], MagicConfig.CANCEL_OPPOSITES.get());
                if (reaction.kind() != Reactions.Kind.NONE) { react(player, reaction); sync(player, s); return; }
                int cap = MageLevel.maxElements(player);
                if (s.formula.size() >= cap && cap < Formula.MAX) { MageLevel.fizzle(player); sync(player, s); return; }
                int before = s.formula.size();
                s.formula.add(Element.values()[element]);
                if (s.formula.size() > before) {
                    s.typed.append(Element.values()[element].code());
                    if (MagicGear.has(player, MagicGear.Trait.SECOND_TONGUE) && ++s.tongue % 4 == 0 && s.formula.size() < cap) {
                        int echoed = s.formula.size();
                        s.formula.add(Element.values()[element]);
                        if (s.formula.size() > echoed) s.typed.append(Element.values()[element].code());
                    }
                }
            }
            else if (action == 8) s.formula.clear();
            else if (!s.formula.empty()) {
                if(s.formula.size()>MageLevel.maxElements(player)){MageLevel.reject(player,s.formula.size());sync(player,s);return;}
                s.intent = action == 15 ? Intent.AREA : action == 16 ? Intent.WEAPON : Intent.values()[action - 9];
                s.last = Formula.unpack(s.formula.packed()); s.lastCast = now;
                s.level = player.level();
                s.elapsed = 0; s.attempts = 0; s.placed = 0;
                s.root = null; s.construction = UUID.randomUUID();
                s.recipe = resolve(player,s.formula);
                s.windup = Math.max(1,(int)Math.round(s.recipe.castTicks()*MagicConfig.CAST_SCALE.get()));
                boolean construction = s.intent == Intent.PRIMARY && s.recipe.shape().construction() || s.intent == Intent.SELF && s.recipe.selfMode().equals("construct");
                s.total = s.windup + (construction ? s.recipe.budget()*s.recipe.interval() : 0);
                s.secret = s.intent == Intent.PRIMARY && s.typed.length() == s.formula.size() && SecretSpell.INSTANCE.matches(s.typed.toString());
                if (s.secret) s.total = s.windup;
                boolean epic = !s.secret && s.intent == Intent.PRIMARY && s.recipe.epic();
                s.anchor = epic ? player.position() : null;
                if(epic && EpicMagic.cramped(player)){deny(player);s.recipe=null;s.total=0;s.anchor=null;sync(player,s);return;}
                s.balance=SpellBalance.begin(player,s.recipe);
                if(s.balance==null){s.recipe=null;s.total=0;s.anchor=null;sync(player,s);return;}
                if (s.secret && s.instant()) {
                    balanced(player,s,()->SecretSpell.INSTANCE.cast(player));
                    SpellBalance.finish(player,s.balance);s.balance=null;
                    s.formula.clear(); s.recipe = null; s.total = 0;
                } else if (epic) {
                    s.total = s.windup;
                } else if (s.instant() && s.intent == Intent.PRIMARY && s.recipe.channeled()) {
                    s.windup = 0; s.channeling = true; s.channelStart = now;
                    s.total = construction ? s.recipe.budget()*s.recipe.interval() : MagicConfig.CHANNEL_TICKS.get()*(1+s.recipe.formula().size());
                    try {
                        balanced(player,s,()->SpellRuntime.cast(player, s.recipe, false));
                        MageLevel.cast(player, s.recipe, false);
                    } catch (RuntimeException error) {
                        SpellBalance.finish(player,s.balance);clearCast(s);
                        sync(player, s);
                        throw error;
                    }
                } else if (s.instant()) {
                    try {
                        balanced(player,s,()->release(player, s.recipe, s.intent, true));
                    } catch (RuntimeException error) {
                        sync(player, s);
                        throw error;
                    } finally {
                        SpellBalance.finish(player,s.balance);
                        clearCast(s);
                    }
                }
                s.remaining = s.total;
            }
        }
        sync(player, s);
    }
    /** Presets only store which elements a spell needs, so the typing order comes from the recipe. */
    public static Formula spoken(Formula preset){
        return RecipeRegistry.resolve(preset.key()).spokenFormula();
    }
    public static final int QUEUE_TICKS_PER_ELEMENT = 4;
    static int pulseTicks(SpellDefinition recipe) { return recipe.shape() == SpellShape.CHAIN ? 10 : 5; }
    private static void finishChannel(ServerPlayer player, Session s) {
        SpellBalance.finish(player,s.balance);s.balance=null;
        MagicConstructionManager.stop(player, s.channelStart);
        if (s.epic != null) s.epic.live = false;
        s.epic = null; s.anchor = null;
        s.channeling = false; s.remaining = 0; s.total = 0; s.formula.clear(); s.recipe = null;
        sync(player, s);
    }
    static final double EPIC_DRIFT = .45;
    private static boolean moved(ServerPlayer player, Session s) {
        double dx = player.getX()-s.anchor.x, dz = player.getZ()-s.anchor.z;
        return dx*dx + dz*dz > EPIC_DRIFT*EPIC_DRIFT;
    }
    private static void breakEpic(ServerPlayer player, Session s) {
        player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.PLAYERS,1f,.5f);
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("hud.magicka.channel_broken"));
        finishChannel(player, s);
    }
    private static void deny(ServerPlayer player) {
        player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.PLAYERS,1f,.5f);
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("hud.magicka.epic_cramped"));
    }
    private static void startEpic(ServerPlayer player, Session s) {
        if (EpicMagic.cramped(player)) { deny(player); SpellBalance.finish(player,s.balance);clearCast(s);return; }
        s.channeling = true; s.channelStart = player.level().getGameTime(); s.elapsed = 0;
        boolean persistentTitan = "titan".equals(SpellDefinition.string(s.recipe.delivery(), "epic", ""));
        s.total = s.remaining = persistentTitan ? 1 : Math.clamp((int)s.recipe.number("duration_ticks",240),20,1200);
        balanced(player,s,()->s.epic = EpicMagic.start(player, s.recipe));
        MageLevel.cast(player, s.recipe, false);
    }    public static void quickFill(ServerPlayer player,int packed){
        if(!player.isAlive() || player.isSpectator() || !GrimoireState.valid(packed))return;
        Session s=SESSIONS.computeIfAbsent(player.getUUID(),id->new Session());long now=player.level().getGameTime();
        if(s.committed() || now-s.lastQueue<5)return;
        s.lastQueue=now;s.queued=spoken(Formula.unpack(packed));s.queueTicks=0;s.queueIndex=0;s.level=player.level();s.formula.clear();s.typed.setLength(0);s.enabled=true;
        sync(player,s);PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(s.queued.packed(),0));
    }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        if(s.queued!=null){
            if(!player.isAlive() || player.isSpectator() || player.level()!=s.level){reset(player);return;}
            s.queueTicks++;
            int target=s.queueTicks/QUEUE_TICKS_PER_ELEMENT;
            while(s.queueIndex<target && s.queueIndex<s.queued.size()){
                Element element=s.queued.elements().get(s.queueIndex++);
                int cap=MageLevel.maxElements(player);
                boolean tooLong=s.formula.size()>=cap && cap<Formula.MAX;
                if(tooLong)MageLevel.reject(player,s.queued.size());
                if(tooLong || ElementLocks.locked(player,element)){if(!tooLong)ElementLocks.reject(player,element);s.queued=null;s.formula.clear();s.typed.setLength(0);sync(player,s);PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(0,0));return;}
                s.formula.add(element);s.typed.append(element.code());
                player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.PLAYERS,.35f,.85f+s.queueIndex*.15f);
            }
            int queued=s.queued.packed();if(s.queueIndex>=s.queued.size())s.queued=null;
            sync(player,s);PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(s.queued==null?0:queued,s.queueTicks));return;
        }
        if (!s.committed()) {
            if(s.enabled && !s.formula.empty() && player.tickCount%12==0) SpellRuntime.particles(player,RecipeRegistry.resolve(s.formula.key()),player.getEyePosition().add(player.getLookAngle().scale(.6)),2,.1);
            return;
        }
        // Death/disconnect/dimension transfer end the session; controls cannot cancel a live cast.
        if (!player.isAlive() || player.isSpectator() || player.level() != s.level) {
            reset(player); return;
        }
        SpellBalance.reserve(player,s.balance);
        boolean persistentTitan = s.epic != null && s.recipe != null && "titan".equals(SpellDefinition.string(s.recipe.delivery(), "epic", ""));
        s.elapsed++; if (!persistentTitan) s.remaining--;
        if (s.anchor != null && moved(player, s)) { breakEpic(player, s); return; }
        if (persistentTitan && !s.epic.live) { finishChannel(player, s); return; }
        if (s.channeling) {
            if (!persistentTitan && s.remaining <= 0) { finishChannel(player, s); return; }
            if(!SpellBalance.channel(player,s.balance)){finishChannel(player,s);return;}
            if (s.epic == null && !s.recipe.shape().construction() && s.elapsed % (SpellRuntime.mining(s.recipe) ? 6 : 2) == 0) SpellVfx.hand(player, s.recipe.formula(), SpellRuntime.mining(s.recipe) ? 6 : 8);
            if (s.epic == null && !s.recipe.shape().construction() && s.elapsed % pulseTicks(s.recipe) == 0) SpellBalance.run(s.balance,()->SpellRuntime.pulse(player, s.recipe));
            sync(player, s); return;
        }
        if (s.elapsed < s.windup && s.elapsed % (SpellRuntime.mining(s.recipe) ? 8 : 3) == 0) SpellRuntime.particles(player,s.recipe,player.getEyePosition().add(player.getLookAngle().scale(.6)),SpellRuntime.mining(s.recipe) ? 1 : 3,SpellRuntime.mining(s.recipe) ? .045 : .15);
        if (s.elapsed == s.windup && s.anchor != null) { startEpic(player, s); sync(player, s); return; }
        if (s.elapsed == s.windup) {
            try {
                balanced(player,s,()->{if (s.secret) SecretSpell.INSTANCE.cast(player);else release(player, s.recipe, s.intent, false);});
            } catch (RuntimeException error) {
                SpellBalance.finish(player,s.balance);
                clearCast(s);
                sync(player, s);
                throw error;
            }
        }
        if (s.remaining == 0) { SpellBalance.finish(player,s.balance);s.balance=null;s.formula.clear(); s.recipe = null; }
        sync(player, s);
    }
    static BlockHitResult aim(ServerPlayer player) {
        return SpellAim.hit(player);
    }
    static boolean hit(BlockHitResult hit) { return hit.getType() == HitResult.Type.BLOCK; }
    private static void sync(ServerPlayer player, Session s) {
        PacketDistributor.sendToPlayer(player, snapshot(player, s));
        SpellBalance.sync(player);
    }
    static MagicNetwork.State snapshot(ServerPlayer player) {
        return snapshot(player, SESSIONS.getOrDefault(player.getUUID(), new Session()));
    }
    private static MagicNetwork.State snapshot(ServerPlayer player, Session s) {
        int budget = s.formula.construction() ? Math.max(0, (s.recipe != null ? s.recipe : MagicStats.resolve(player, s.formula.key())).budget() - s.attempts) : 0;
        return new MagicNetwork.State(s.enabled, s.formula.packed(), s.remaining, s.total, s.intent.ordinal(), budget, s.instant(), s.channeling);
    }
    /** The formula an observer can read: what is being prepared now, else the last cast for a few seconds. */
    public static Formula prepared(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return new Formula();
        if (!s.formula.empty()) return Formula.unpack(s.formula.packed());
        return s.last != null && player.level().getGameTime() - s.lastCast < 100 ? Formula.unpack(s.last.packed()) : new Formula();
    }
    static void interrupt(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s != null && s.recipe != null) finishChannel(player, s);
        SpellRuntime.interrupt(player);
    }
    static void dissolveLocked(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.committed() || (ElementLocks.mask(s.formula) & ElementLocks.active(player)) == 0 && s.queued == null) return;
        s.formula.clear(); s.typed.setLength(0);
        if (s.queued != null) { s.queued = null; PacketDistributor.sendToPlayer(player, new MagicNetwork.QueueState(0, 0)); }
        sync(player, s);
    }
    static void clearFormula(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s != null && s.remaining == 0 && !s.formula.empty()) { s.formula.clear(); sync(player, s); }
    }
    private static void clearCast(Session s) {
        s.balance=null;
        s.remaining = 0; s.total = 0; s.elapsed = 0; s.channeling = false;
        s.epic = null; s.anchor = null; s.formula.clear(); s.recipe = null;
    }
    static SpellDefinition casting(ServerPlayer player) { Session s=SESSIONS.get(player.getUUID()); return s==null?null:s.recipe; }
    static void settingsChanged(ServerPlayer player,boolean instant){Session s=SESSIONS.get(player.getUUID());if(s!=null){if(instant)s.instantOverride=null;sync(player,s);}}
    static SpellDefinition resolve(ServerPlayer player,Formula formula){var named=RecipeRegistry.resolve(formula.key());return !named.ordered()||named.spoken().equals(formula.elements())?MagicStats.resolve(player,formula.key()):MagicStats.apply(player,RecipeRegistry.fusion(formula.key()));}
    static void construction(ServerPlayer player, int attempts) { Session s=SESSIONS.get(player.getUUID()); if(s!=null)s.attempts=attempts; }
    private static void reset(ServerPlayer player) {
        Session old = SESSIONS.remove(player.getUUID());
        if(old!=null)SpellBalance.finish(player,old.balance);
        if (old != null && old.epic != null) old.epic.live = false;
        ElementLocks.clear(player);
        Boolean override = old == null ? null : old.instantOverride;
        if (override != null) { Session fresh = new Session(); fresh.instantOverride = override; SESSIONS.put(player.getUUID(), fresh); }
        boolean instant = override != null ? override : MagicConfig.INSTANT_CAST.get();
        PacketDistributor.sendToPlayer(player, new MagicNetwork.State(false, 0, 0, 0, 0, 0, instant, false));
        PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(0,0));
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) reset(p); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {var s=SESSIONS.remove(event.getEntity().getUUID());if(s!=null && event.getEntity() instanceof ServerPlayer p)SpellBalance.finish(p,s.balance);}
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer p) reset(p); }
    @SubscribeEvent public void stop(ServerStoppedEvent event) { SESSIONS.clear();WorldMagic.reset();ElementLocks.reset(); }
    private static void release(ServerPlayer player, SpellDefinition recipe, Intent intent, boolean instant) {
        switch (intent) {
            case AREA -> CastModes.area(player, recipe);
            case WEAPON -> CastModes.imbue(player, recipe);
            default -> {
                if (intent == Intent.SELF) CastModes.selfWard(player, recipe);
                SpellRuntime.cast(player, recipe, intent == Intent.SELF, instant);
            }
        }
        MageLevel.cast(player, recipe, intent == Intent.SELF);
    }
    private static void balanced(ServerPlayer player,Session s,Runnable action){SpellBalance.started(player,s.balance);SpellBalance.run(s.balance,action);}
    private static void react(ServerPlayer player, Reactions.Result reaction) {
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        var hand = player.getEyePosition().add(player.getLookAngle().scale(.6)).add(0, -.3, 0);
        switch (reaction.kind()) {
            case CANCEL -> {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, hand.x, hand.y, hand.z, 8, .12, .12, .12, .02);
                level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.PLAYERS, .5f, 1.6f);
            }
            case FUSE -> {
                boolean steam = reaction.became() == Element.STEAM;
                level.sendParticles(steam ? net.minecraft.core.particles.ParticleTypes.CLOUD : net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, hand.x, hand.y, hand.z, 10, .1, .1, .1, .03);
                level.playSound(null, player.blockPosition(), steam ? net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH : net.minecraft.sounds.SoundEvents.GLASS_PLACE, net.minecraft.sounds.SoundSource.PLAYERS, .5f, steam ? 1.4f : 1.8f);
            }
            default -> {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.DRIPPING_WATER, hand.x, hand.y, hand.z, 6, .1, .1, .1, 0);
                level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, net.minecraft.sounds.SoundSource.PLAYERS, .4f, 1.6f);
            }
        }
    }
}
