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
        Formula queued;
        int queueTicks, queueIndex;
        long lastQueue=-100, channelStart;
        boolean instant, channeling, secret;
        final StringBuilder typed = new StringBuilder();
        UUID construction;
        BlockPos root;
        boolean committed() { return remaining > 0; }
    }
    public static void input(ServerPlayer player, int action) {
        if (action < 0 || action > 13 || !player.isAlive() || player.isSpectator()) return;
        // Mode toggles stay live while polymorphed so the shape can never lock the controls out.
        if (action != 7 && action != 11 && action != 12 && PolymorphManager.polymorphed(player)) return;
        Session s = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        long now = player.serverLevel().getGameTime();
        if (s.inputTick != now) { s.inputTick = now; s.inputsThisTick = 0; }
        if (++s.inputsThisTick > 12) return;
        if (action == 12) { if (s.channeling && !s.recipe.shape().construction()) finishChannel(player, s); return; }
        if (s.committed()) return;
        if(s.queued!=null){s.queued=null;PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(0,0));}
        if (action == 7) s.enabled = !s.enabled;
        else if (action == 11) s.instant = !s.instant;
        else if (s.enabled) {
            if (action < 7 || action == 13) {
                int element = action == 13 ? Element.DEATH.ordinal() : action;
                if (s.formula.empty()) s.typed.setLength(0);
                int before = s.formula.size();
                s.formula.add(Element.values()[element]);
                if (s.formula.size() > before) s.typed.append(element + 1);
            }
            else if (action == 8) s.formula.clear();
            else if (!s.formula.empty()) {
                s.intent = Intent.values()[action - 9];
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
                if (s.secret && s.instant) {
                    SecretBuild.cast(player);
                    s.formula.clear(); s.recipe = null; s.total = 0;
                } else if (s.instant && s.intent == Intent.PRIMARY && s.recipe.channeled()) {
                    s.windup = 0; s.channeling = true; s.channelStart = now;
                    s.total = construction ? s.recipe.budget()*s.recipe.interval() : MagicConfig.CHANNEL_TICKS.get()*(1+s.recipe.formula().size());
                    SpellRuntime.cast(player, s.recipe, false);
                } else if (s.instant) {
                    SpellRuntime.cast(player, s.recipe, s.intent == Intent.SELF, true);
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
    public static final int QUEUE_TICKS_PER_ELEMENT = 4;
    static int pulseTicks(SpellDefinition recipe) { return recipe.shape() == SpellShape.CHAIN ? 10 : 5; }
    private static void finishChannel(ServerPlayer player, Session s) {
        MagicConstructionManager.stop(player, s.channelStart);
        s.channeling = false; s.remaining = 0; s.total = 0; s.formula.clear(); s.recipe = null;
        sync(player, s);
    }
    public static void quickFill(ServerPlayer player,int packed){
        if(!player.isAlive() || player.isSpectator() || !GrimoireState.valid(packed))return;
        Session s=SESSIONS.computeIfAbsent(player.getUUID(),id->new Session());long now=player.serverLevel().getGameTime();
        if(s.committed() || now-s.lastQueue<5)return;
        s.lastQueue=now;s.queued=Formula.unpack(packed);s.queueTicks=0;s.queueIndex=0;s.level=player.serverLevel();s.formula.clear();s.typed.setLength(0);s.enabled=true;
        sync(player,s);PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(packed,0));
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
                s.formula.add(s.queued.elements().get(s.queueIndex++));
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
        if (s.channeling) {
            if (s.remaining <= 0) { finishChannel(player, s); return; }
            if (!s.recipe.shape().construction() && s.elapsed % pulseTicks(s.recipe) == 0) SpellRuntime.pulse(player, s.recipe);
            sync(player, s); return;
        }
        if (s.elapsed < s.windup && s.elapsed % 3 == 0) SpellRuntime.particles(player,s.recipe,player.getEyePosition().add(player.getLookAngle().scale(.6)),3,.15);
        if (s.elapsed == s.windup) { if (s.secret) SecretBuild.cast(player); else SpellRuntime.cast(player,s.recipe,s.intent == Intent.SELF); }
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
    static void construction(ServerPlayer player, int attempts) { Session s=SESSIONS.get(player.getUUID()); if(s!=null)s.attempts=attempts; }
    private static void reset(ServerPlayer player) {
        Session old = SESSIONS.remove(player.getUUID());
        boolean instant = old != null && old.instant;
        if (instant) { Session fresh = new Session(); fresh.instant = true; SESSIONS.put(player.getUUID(), fresh); }
        PacketDistributor.sendToPlayer(player, new MagicNetwork.State(false, 0, 0, 0, 0, 0, instant, false));
        PacketDistributor.sendToPlayer(player,new MagicNetwork.QueueState(0,0));
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) reset(p); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { SESSIONS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer p) reset(p); }
    @SubscribeEvent public void stop(ServerStoppedEvent event) { SESSIONS.clear();WorldMagic.reset(); }
}
