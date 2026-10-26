package com.restor.magicka;

import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Vanilla stand visuals with server-owned HP, no equipment extraction, loot or disk persistence. */
public final class MagicSummon extends ArmorStand {
    public MagicSummon(ServerLevel level,Vec3 point,float health){
        super(level,point.x,point.y,point.z);addTag("magicka_object");setNoGravity(true);setHealth(Mth.clamp(health,1,20));
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(level.isClientSide || !isAlive() || amount<=0)return false;
        if(source.getEntity() instanceof ServerPlayer p && !EffectGraph.canHarm(p,this))return false;
        setHealth(Math.max(0,getHealth()-amount));level.broadcastEntityEvent(this,(byte)32);
        if(getHealth()<=0)discard();return true;
    }
    @Override public InteractionResult interactAt(Player player,Vec3 location,InteractionHand hand){return InteractionResult.FAIL;}
    @Override public boolean shouldBeSaved(){return false;}
}
