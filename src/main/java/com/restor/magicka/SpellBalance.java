package com.restor.magicka;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

public final class SpellBalance {
    private static final String TAG="magicka_balance";
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static long serial;
    private static Cast context;
    static final class State {
        long recovery,lastUpdate,lastMana,lastStrain,notice=-1000;
        double mana=BalanceConfig.MANA_MAX.get(),strain;
        final Map<String,Long> groups=new HashMap<>();
        final long[] elements=new long[Element.values().length];
        State(long now){lastUpdate=now;}
        void advance(long now,boolean channel){
            long elapsed=Math.max(0,now-lastUpdate);
            if(!channel || BalanceConfig.CHANNEL_REGEN.get())mana=Math.min(BalanceConfig.MANA_MAX.get(),mana+Math.min(elapsed,Math.max(0,now-lastMana-BalanceConfig.MANA_DELAY.get()))*BalanceConfig.MANA_REGEN.get()/20);
            strain=Math.max(0,strain-Math.min(elapsed,Math.max(0,now-lastStrain-BalanceConfig.STRAIN_DELAY.get()))*BalanceConfig.STRAIN_DECAY.get()/20);
            mana=net.minecraft.util.Mth.clamp(mana,0,BalanceConfig.MANA_MAX.get());strain=Math.min(strain,BalanceConfig.STRAIN_MAX.get());lastUpdate=now;
            groups.values().removeIf(t->t<=now);
        }
        CompoundTag save(){var n=new CompoundTag();n.putLong("recovery",recovery);n.putLong("updated",lastUpdate);n.putLong("mana_used",lastMana);n.putLong("strain_used",lastStrain);n.putDouble("mana",mana);n.putDouble("strain",strain);n.putLongArray("elements",elements);var g=new CompoundTag();groups.forEach(g::putLong);n.put("groups",g);return n;}
        static State load(CompoundTag n,long now){var s=new State(now);if(!n.contains("mana"))return s;s.recovery=n.getLong("recovery");s.lastUpdate=n.getLong("updated");s.lastMana=n.getLong("mana_used");s.lastStrain=n.getLong("strain_used");s.mana=n.getDouble("mana");s.strain=n.getDouble("strain");if(!Double.isFinite(s.mana))s.mana=0;if(!Double.isFinite(s.strain))s.strain=BalanceConfig.STRAIN_MAX.get();long[] a=n.getLongArray("elements");System.arraycopy(a,0,s.elements,0,Math.min(a.length,s.elements.length));var g=n.getCompound("groups");g.getAllKeys().stream().limit(256).forEach(k->s.groups.put(k,g.getLong(k)));return s;}
    }
    static final class Cast {
        final UUID owner;final BalanceRules.Plan plan;final long serial=++SpellBalance.serial;
        boolean finished,cancelled,started;
        Cast(UUID owner,BalanceRules.Plan plan){this.owner=owner;this.plan=plan;}
    }
    static long now(ServerPlayer p){return p.getLevel().getServer().overworld().getGameTime();}
    static boolean bypass(ServerPlayer p){return BalanceConfig.MODE.get()==BalanceConfig.Mode.OFF || BalanceConfig.CREATIVE.get()&&p.isCreative() || BalanceConfig.FAKE.get()&&p instanceof FakePlayer;}
    private static State state(ServerPlayer p){return STATES.computeIfAbsent(p.getUUID(),id->State.load(p.getPersistentData().getCompound(TAG),now(p)));}
    private static void save(ServerPlayer p,State s){p.getPersistentData().put(TAG,s.save());}
    static BalanceRules.Plan plan(ServerPlayer p,SpellDefinition recipe){return BalanceRules.plan(recipe,MagicGear.total(p)[MagicStats.Stat.CAST_SPEED.ordinal()]);}
    static int waitTicks(State s,BalanceRules.Plan plan,long now){if(BalanceConfig.COOLDOWN_SCALE.get()==0)return 0;long ready=BalanceConfig.recovery()?s.recovery:0;if(plan!=null&&BalanceConfig.groups())ready=Math.max(ready,s.groups.getOrDefault(plan.group(),0L));if(plan!=null&&BalanceConfig.elements())for(int i=0;i<s.elements.length;i++)if(plan.elements()[i]>0)ready=Math.max(ready,s.elements[i]);return (int)net.minecraft.util.Mth.clamp(ready-now,0,1000000);}
    static void reject(ServerPlayer p,String key,Object...args){State s=state(p);long now=now(p);if(now-s.notice>=10){s.notice=now;p.displayClientMessage(Component.translatable(key,args),true);}sync(p);}
    static Cast begin(ServerPlayer p,SpellDefinition recipe){
        if(bypass(p))return new Cast(p.getUUID(),null);
        var plan=plan(p,recipe);var s=state(p);long now=now(p);s.advance(now,false);
        int wait=waitTicks(s,plan,now);if(wait>0){reject(p,"balance.magicka.wait",String.format(Locale.ROOT,"%.1f",wait/20.0));return null;}
        if(BalanceConfig.mana() && s.mana+1e-7<plan.mana()){reject(p,"balance.magicka.no_mana");return null;}
        if(BalanceConfig.strain() && s.strain+plan.strain()>BalanceConfig.STRAIN_MAX.get()+1e-7){reject(p,"balance.magicka.too_much_strain");return null;}
        var active=BalanceConfig.LIMITS.get() && plan.activeLimit()>0?SpellRuntime.activeCasts(p,plan.activeGroup()):List.<Cast>of();
        if(active.size()>=plan.activeLimit() && plan.activeLimit()>0 && BalanceConfig.ZONE_POLICY.get()==BalanceConfig.ZonePolicy.REJECT){reject(p,"balance.magicka.active_limit");return null;}
        for(int i=0;i<=active.size()-plan.activeLimit() && i<active.size();i++)active.get(i).cancelled=true;
        var cast=new Cast(p.getUUID(),plan);consume(s,plan.mana(),plan.strain(),now);s.recovery=Math.max(s.recovery,now+plan.recovery());cooldowns(s,cast,now);save(p,s);return cast;
    }
    private static void consume(State s,double mana,double strain,long now){if(BalanceConfig.mana()&&mana>0){s.mana=Math.max(0,s.mana-mana);s.lastMana=now;}if(BalanceConfig.strain()&&strain>0){s.strain=Math.min(BalanceConfig.STRAIN_MAX.get(),s.strain+strain);s.lastStrain=now;}}
    private static void cooldowns(State s,Cast cast,long now){var plan=cast.plan;if(plan.cooldown()>0)s.groups.merge(plan.group(),now+plan.cooldown(),Math::max);if(BalanceConfig.elements())for(int i=0;i<s.elements.length;i++)s.elements[i]=Math.max(s.elements[i],now+plan.elements()[i]);}
    static void started(ServerPlayer p,Cast cast){if(cast==null||cast.started)return;cast.started=true;if(cast.plan!=null){var s=state(p);cooldowns(s,cast,now(p));save(p,s);}}
    static void reserve(ServerPlayer p,Cast cast){if(cast==null||cast.plan==null)return;var s=state(p);long now=now(p);s.recovery=Math.max(s.recovery,now+cast.plan.recovery());cooldowns(s,cast,now);save(p,s);}
    static boolean channel(ServerPlayer p,Cast cast){
        if(cast==null||cast.cancelled)return false;if(cast.plan==null||bypass(p))return true;
        var s=state(p);long now=now(p);s.advance(now,true);var plan=cast.plan;
        if(BalanceConfig.mana() && s.mana+1e-7<plan.manaRate()/20){reject(p,"balance.magicka.no_mana");return false;}
        if(BalanceConfig.strain() && s.strain+plan.strainRate()/20>BalanceConfig.STRAIN_MAX.get()+1e-7){reject(p,"balance.magicka.too_much_strain");return false;}
        consume(s,plan.manaRate()/20,plan.strainRate()/20,now);save(p,s);return true;
    }
    static void finish(ServerPlayer p,Cast cast){if(cast==null||cast.finished)return;cast.finished=true;if(cast.plan!=null){var s=state(p);long now=now(p);s.recovery=Math.max(s.recovery,now+cast.plan.recovery());cooldowns(s,cast,now);save(p,s);}sync(p);}
    static Cast context(){return context;}
    static void settingsChanged(ServerPlayer player){var s=state(player);s.advance(now(player),CastingServer.snapshot(player).channeling());save(player,s);sync(player);}
    static void run(Cast cast,Runnable action){Cast old=context;context=cast;try{action.run();}finally{context=old;}}
    static void sync(ServerPlayer p){
        if(p instanceof FakePlayer)return;
        if(bypass(p)||!BalanceConfig.SHOW.get()){MagicNetwork.sendToPlayer(p,MagicNetwork.Balance.EMPTY);return;}
        State s=state(p);long now=now(p);var current=CastingServer.casting(p);var f=Formula.unpack(CastingServer.snapshot(p).formula());var plan=current!=null?plan(p,current):f.empty()?null:plan(p,CastingServer.resolve(p,f));
        int wait=waitTicks(s,plan,now);
        MagicNetwork.sendToPlayer(p,new MagicNetwork.Balance(true,wait,BalanceConfig.mana()?(float)s.mana:-1,BalanceConfig.mana()?BalanceConfig.MANA_MAX.get().floatValue():0,BalanceConfig.strain()?(float)s.strain:-1,BalanceConfig.strain()?BalanceConfig.STRAIN_MAX.get().floatValue():0));
    }
    @SubscribeEvent public void tick(PlayerTickEvent e){if(e.phase==net.minecraftforge.event.TickEvent.Phase.END && e.player instanceof ServerPlayer p && !(p instanceof FakePlayer)){var s=state(p);s.advance(now(p),CastingServer.snapshot(p).channeling());if(p.tickCount%5==0)sync(p);if(p.tickCount%20==0)save(p,s);}}
    @SubscribeEvent public void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer old && e.getEntity() instanceof ServerPlayer p){var s=STATES.getOrDefault(old.getUUID(),State.load(old.getPersistentData().getCompound(TAG),now(p)));if(e.isWasDeath()&&BalanceConfig.DEATH_REFILL.get())s.mana=BalanceConfig.MANA_MAX.get();STATES.put(p.getUUID(),s);save(p,s);}}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){SettingsData.forget(e.getEntity().getUUID());if(e.getEntity() instanceof ServerPlayer p){var s=STATES.remove(p.getUUID());if(s!=null)save(p,s);}}
    @SubscribeEvent public void stop(ServerStoppedEvent e){STATES.clear();context=null;serial=0;SettingsData.reset();}
}
