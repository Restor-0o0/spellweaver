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
    public enum Intent { PRIMARY, SELF }
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
        boolean instant, channeling, secret;
        final StringBuilder typed = new StringBuilder();
        UUID construction;
        BlockPos root;
        net.minecraft.world.phys.Vec3 anchor;
        EpicMagic.Channel epic;
        boolean committed() { return remaining > 0; }
    }
    public static void input(ServerPlayer player, int action) {
        if (action < 0 || action > 14 || !player.isAlive() || player.isSpectator()) return;
        // Mode toggles stay live while polymorphed so the shape can never lock the controls out.
        if (action != 7 && action != 11 && action != 12 && PolymorphManager.polymorphed(player)) return;
        if (action == 14) { MagicGear.open(player); return; }
        Session s = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        long now = player.serverLevel().getGameTime();
        if (s.inputTick != now) { s.inputTick = now; s.inputsThisTick = 0; }
        if (++s.inputsThisTick > 12) return;
        if (action == 12) { if (s.channeling && s.epic == null && !s.recipe.shape().construction()) finishChannel(player, s); return; }
        if (s.committed()) return;
        if(s.queued!=null){s.queued=null;PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(0,0));}
        if (action == 7) s.enabled = !s.enabled;
        else if (action == 11) s.instant = !s.instant;
        else if (s.enabled) {
            if (action < 7 || action == 13) {
                int element = action == 13 ? Element.DEATH.ordinal() : action;
                if (ElementLocks.locked(player, Element.values()[element])) { ElementLocks.reject(player, Element.values()[element]); sync(player, s); return; }
                if (s.formula.empty()) s.typed.setLength(0);
                int before = s.formula.size();
                s.formula.add(Element.values()[element]);
                if (s.formula.size() > before) {
                    s.typed.append(element + 1);
                    if (MagicGear.has(player, MagicGear.Trait.SECOND_TONGUE) && ++s.tongue % 4 == 0) {
                        int echoed = s.formula.size();
                        s.formula.add(Element.values()[element]);
                        if (s.formula.size() > echoed) s.typed.append(element + 1);
                    }
                }
            }
            else if (action == 8) s.formula.clear();
            else if (!s.formula.empty()) {
                s.intent = Intent.values()[action - 9];
                s.last = Formula.unpack(s.formula.packed()); s.lastCast = now;
                s.level = player.serverLevel();
                s.elapsed = 0; s.attempts = 0; s.placed = 0;
                s.root = null; s.construction = UUID.randomUUID();
                s.recipe = MagicStats.resolve(player, s.formula.key());
                if (!s.recipe.incantation().equals(s.typed.toString()) && !s.recipe.incantation().isEmpty()) s.recipe = fizzle(player, s);
                s.windup = Math.max(1,(int)Math.round(s.recipe.castTicks()*MagicConfig.CAST_SCALE.get()));
                boolean construction = s.intent == Intent.PRIMARY && s.recipe.shape().construction() || s.intent == Intent.SELF && s.recipe.selfMode().equals("construct");
                s.total = s.windup + (construction ? s.recipe.budget()*s.recipe.interval() : 0);
                s.secret = s.intent == Intent.PRIMARY && s.typed.length() == s.formula.size() && SecretBuild.matches(s.typed.toString());
                if (s.secret) s.total = s.windup;
                boolean epic = !s.secret && s.intent == Intent.PRIMARY && s.recipe.epic();
                s.anchor = epic ? player.position() : null;
                if (s.secret && s.instant) {
                    SecretBuild.cast(player);
                    s.formula.clear(); s.recipe = null; s.total = 0;
                } else if (epic && EpicMagic.cramped(player)) {
                    deny(player);
                    s.formula.clear(); s.recipe = null; s.total = 0; s.anchor = null;
                } else if (epic) {
                    s.total = s.windup;
                } else if (s.instant && s.intent == Intent.PRIMARY && s.recipe.channeled()) {
                    s.windup = 0; s.channeling = true; s.channelStart = now;
                    s.total = construction ? s.recipe.budget()*s.recipe.interval() : MagicConfig.CHANNEL_TICKS.get()*(1+s.recipe.formula().size());
                    SpellRuntime.cast(player, s.recipe, false);
                    MagicGear.cast(player, s.recipe, false);
                } else if (s.instant) {
                    SpellRuntime.cast(player, s.recipe, s.intent == Intent.SELF, true);
                    MagicGear.cast(player, s.recipe, s.intent == Intent.SELF);
                    s.formula.clear(); s.recipe = null; s.total = 0;
                }
                s.remaining = s.total;
            }
        }
        sync(player, s);
    }
    private static SpellDefinition fizzle(ServerPlayer player, Session s) {
        Formula lesser = new Formula();
        var elements = s.formula.elements();
        for (int i = 0; i < elements.size() - 1; i++) lesser.add(elements.get(i));
        SpellDefinition weaker = MagicStats.resolve(player, lesser.key());
        player.serverLevel().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.PLAYERS,.8f,.7f);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hud.magicka.incantation_broken",weaker.displayName()),true);
        return weaker;
    }
    /** Presets only store which elements a spell needs, so the incantation order comes from the recipe. */
    public static Formula spoken(Formula preset){
        var recipe=RecipeRegistry.resolve(preset.key());
        return recipe.incantation().isEmpty()?preset:recipe.spokenFormula();
    }
    public static final int QUEUE_TICKS_PER_ELEMENT = 4;
    static int pulseTicks(SpellDefinition recipe) { return recipe.shape() == SpellShape.CHAIN ? 10 : 5; }
    private static void finishChannel(ServerPlayer player, Session s) {
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
        player.serverLevel().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.PLAYERS,1f,.5f);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hud.magicka.channel_broken"),true);
        finishChannel(player, s);
    }
    private static void deny(ServerPlayer player) {
        player.serverLevel().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.PLAYERS,1f,.5f);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hud.magicka.epic_cramped"),true);
    }
    private static void startEpic(ServerPlayer player, Session s) {
        if (EpicMagic.cramped(player)) { deny(player); s.anchor = null; s.remaining = s.total = 0; s.formula.clear(); s.recipe = null; return; }
        s.channeling = true; s.channelStart = player.serverLevel().getGameTime(); s.elapsed = 0;
        s.total = s.remaining = Math.clamp((int)s.recipe.number("duration_ticks",240),20,1200);
        s.epic = EpicMagic.start(player, s.recipe);
        MagicGear.cast(player, s.recipe, false);
    }    public static void quickFill(ServerPlayer player,int packed){
        if(!player.isAlive() || player.isSpectator() || !GrimoireState.valid(packed))return;
        Session s=SESSIONS.computeIfAbsent(player.getUUID(),id->new Session());long now=player.serverLevel().getGameTime();
        if(s.committed() || now-s.lastQueue<5)return;
        s.lastQueue=now;s.queued=spoken(Formula.unpack(packed));s.queueTicks=0;s.queueIndex=0;s.level=player.serverLevel();s.formula.clear();s.typed.setLength(0);s.enabled=true;
        sync(player,s);PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(s.queued.packed(),0));
    }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        if(s.queued!=null){
            if(!player.isAlive() || player.isSpectator() || player.serverLevel()!=s.level){reset(player);return;}
            s.queueTicks++;
            int target=s.queueTicks/QUEUE_TICKS_PER_ELEMENT;
            while(s.queueIndex<target && s.queueIndex<s.queued.size()){
                Element element=s.queued.elements().get(s.queueIndex++);
                if(ElementLocks.locked(player,element)){ElementLocks.reject(player,element);s.queued=null;s.formula.clear();s.typed.setLength(0);sync(player,s);PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(0,0));return;}
                s.formula.add(element);s.typed.append(element.ordinal()+1);
                player.serverLevel().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.PLAYERS,.35f,.85f+s.queueIndex*.15f);
            }
            int queued=s.queued.packed();if(s.queueIndex>=s.queued.size())s.queued=null;
            sync(player,s);PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(s.queued==null?0:queued,s.queueTicks));return;
        }
        if (!s.committed()) {
            if(s.enabled && !s.formula.empty() && player.tickCount%12==0) SpellRuntime.particles(player,RecipeRegistry.resolve(s.formula.key()),player.getEyePosition().add(player.getLookAngle().scale(.6)),2,.1);
            return;
        }
        // Death/disconnect/dimension transfer end the session; controls cannot cancel a live cast.
        if (!player.isAlive() || player.isSpectator() || player.serverLevel() != s.level) {
            reset(player); return;
        }
        s.elapsed++; s.remaining--;
        if (s.anchor != null && moved(player, s)) { breakEpic(player, s); return; }
        if (s.channeling) {
            if (s.remaining <= 0) { finishChannel(player, s); return; }
            if (s.epic == null && !s.recipe.shape().construction() && s.elapsed % pulseTicks(s.recipe) == 0) SpellRuntime.pulse(player, s.recipe);
            sync(player, s); return;
        }
        if (s.elapsed < s.windup && s.elapsed % 3 == 0) SpellRuntime.particles(player,s.recipe,player.getEyePosition().add(player.getLookAngle().scale(.6)),3,.15);
        if (s.elapsed == s.windup && s.anchor != null) { startEpic(player, s); sync(player, s); return; }
        if (s.elapsed == s.windup) { if (s.secret) SecretBuild.cast(player); else { SpellRuntime.cast(player,s.recipe,s.intent == Intent.SELF); MagicGear.cast(player,s.recipe,s.intent == Intent.SELF); } }
        if (s.remaining == 0) { s.formula.clear(); s.recipe = null; }
        sync(player, s);
    }
    static BlockHitResult aim(ServerPlayer player) {
        return SpellAim.hit(player);
    }
    static boolean hit(BlockHitResult hit) { return hit.getType() == HitResult.Type.BLOCK; }
    private static void sync(ServerPlayer player, Session s) {
        PacketDistributor.sendToPlayer(player, snapshot(player, s));
    }
    static MagicNetwork.State snapshot(ServerPlayer player) {
        return snapshot(player, SESSIONS.getOrDefault(player.getUUID(), new Session()));
    }
    private static MagicNetwork.State snapshot(ServerPlayer player, Session s) {
        int budget = s.formula.construction() ? Math.max(0, (s.recipe != null ? s.recipe : MagicStats.resolve(player, s.formula.key())).budget() - s.attempts) : 0;
        return new MagicNetwork.State(s.enabled, s.formula.packed(), s.remaining, s.total, s.intent.ordinal(), budget, s.instant, s.channeling);
    }
    /** The formula an observer can read: what is being prepared now, else the last cast for a few seconds. */
    public static Formula prepared(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return new Formula();
        if (!s.formula.empty()) return Formula.unpack(s.formula.packed());
        return s.last != null && player.serverLevel().getGameTime() - s.lastCast < 100 ? Formula.unpack(s.last.packed()) : new Formula();
    }
    static void dissolveLocked(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.committed() || (ElementLocks.mask(s.formula) & ElementLocks.active(player)) == 0 && s.queued == null) return;
        s.formula.clear(); s.typed.setLength(0);
        if (s.queued != null) { s.queued = null; PacketDistributor.sendToPlayer(player, new MagicNetwork.QueueState(0, 0)); }
        sync(player, s);
    }
    static SpellDefinition casting(ServerPlayer player) { Session s=SESSIONS.get(player.getUUID()); return s==null?null:s.recipe; }
    static void construction(ServerPlayer player, int attempts) { Session s=SESSIONS.get(player.getUUID()); if(s!=null)s.attempts=attempts; }
    private static void reset(ServerPlayer player) {
        Session old = SESSIONS.remove(player.getUUID());
        if (old != null && old.epic != null) old.epic.live = false;
        ElementLocks.clear(player);
        boolean instant = old != null && old.instant;
        if (instant) { Session fresh = new Session(); fresh.instant = true; SESSIONS.put(player.getUUID(), fresh); }
        PacketDistributor.sendToPlayer(player, new MagicNetwork.State(false, 0, 0, 0, 0, 0, instant, false));
        PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(0,0));
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) reset(p); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { SESSIONS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer p) reset(p); }
    @SubscribeEvent public void stop(ServerStoppedEvent event) { SESSIONS.clear();WorldMagic.reset();ElementLocks.reset(); }
}
