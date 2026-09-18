package com.restor.magicka;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Personal 5x5x5 stencil, 125 cells packed into two longs and kept in player data. */
public final class ShapePattern {
    public static final int SIZE = 5, CELLS = SIZE*SIZE*SIZE;
    private static final String TAG = "magicka_shape";
    private static final long HIGH_MASK = (1L << (CELLS-64)) - 1;
    public static int index(int x,int y,int z){return y*SIZE*SIZE+z*SIZE+x;}
    public static boolean inside(int x,int y,int z){return x>=0 && x<SIZE && y>=0 && y<SIZE && z>=0 && z<SIZE;}
    public static long[] defaults(){long[] bits=new long[2];for(int x=0;x<SIZE;x++)for(int z=0;z<SIZE;z++)set(bits,x,0,z,true);return bits;}
    public static long[] sanitize(long low,long high){return new long[]{low,high&HIGH_MASK};}
    public static long[] of(Player player){
        if(!(player instanceof ServerPlayer server))return MagicNetwork.clientPattern.clone();
        long[] stored=server.getPersistentData().getLongArray(TAG);
        return stored.length==2?sanitize(stored[0],stored[1]):defaults();
    }
    public static void store(ServerPlayer player,long low,long high){
        player.getPersistentData().putLongArray(TAG,sanitize(low,high));sync(player);
    }
    public static void sync(ServerPlayer player){long[] bits=of(player);PacketDistributor.sendToPlayer(player,new MagicNetwork.ShapeGrid(bits[0],bits[1]));}
    public static boolean filled(long[] bits,int x,int y,int z){
        if(!inside(x,y,z))return false;
        int i=index(x,y,z);
        return (((i<64?bits[0]:bits[1])>>>(i&63))&1L)!=0;
    }
    public static void set(long[] bits,int x,int y,int z,boolean value){
        if(!inside(x,y,z))return;
        int i=index(x,y,z);long mask=1L<<(i&63);
        if(value)bits[i<64?0:1]|=mask;else bits[i<64?0:1]&=~mask;
    }
    public static int count(long[] bits){return Long.bitCount(bits[0])+Long.bitCount(bits[1]&HIGH_MASK);}
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event){if(event.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public void clone(PlayerEvent.Clone event){
        long[] stored=event.getOriginal().getPersistentData().getLongArray(TAG);
        if(stored.length==2)event.getEntity().getPersistentData().putLongArray(TAG,stored.clone());
    }
    public ShapePattern(){}
}
