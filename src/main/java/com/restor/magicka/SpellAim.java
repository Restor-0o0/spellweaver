package com.restor.magicka;

import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Trace as far as the loaded world allows, without a spell-distance cap or chunk generation. */
public final class SpellAim {
    private static boolean loaded(Level level,Vec3 from,Vec3 to){
        if(to.y<level.getMinY() || to.y>=level.getMaxY() || !level.getWorldBorder().isWithinBounds(BlockPos.containing(to)))return false;
        int ax=BlockPos.containing(from).getX()>>4,bx=BlockPos.containing(to).getX()>>4,az=BlockPos.containing(from).getZ()>>4,bz=BlockPos.containing(to).getZ()>>4;
        for(int x=Math.min(ax,bx);x<=Math.max(ax,bx);x++)for(int z=Math.min(az,bz);z<=Math.max(az,bz);z++)if(!level.hasChunk(x,z))return false;
        return true;
    }
    public static BlockHitResult hit(Player player){
        Vec3 from=player.getEyePosition(),look=player.getLookAngle(),step=look.scale(8),current=from;
        if(!Double.isFinite(look.x+look.y+look.z) || look.lengthSqr()<.1)return BlockHitResult.miss(from,Direction.UP,BlockPos.containing(from));
        while(true){
            Vec3 next=current.add(step);
            if(!loaded(player.level(),current,next))return BlockHitResult.miss(current,Direction.getApproximateNearest(look.x,look.y,look.z),BlockPos.containing(current));
            var hit=player.level().clip(new ClipContext(current,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if(hit.getType()!=HitResult.Type.MISS)return hit;current=next;
        }
    }
    public static LivingEntity entity(Player player,Vec3 end){
        Vec3 start=player.getEyePosition(),direction=end.subtract(start).normalize();double distance=start.distanceTo(end);
        for(double d=0;d<distance;d+=8){
            Vec3 from=start.add(direction.scale(d)),to=start.add(direction.scale(Math.min(distance,d+8)));LivingEntity closest=null;double best=Double.MAX_VALUE;
            for(var e:player.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(1),e->e!=player && e.isAlive() && !e.isSpectator())){
                var intercept=e.getBoundingBox().inflate(.25).clip(from,to);
                if(intercept.isPresent() && from.distanceToSqr(intercept.get())<best){closest=e;best=from.distanceToSqr(intercept.get());}
            }
            if(closest!=null)return closest;
        }
        return null;
    }
    private SpellAim(){}
}
