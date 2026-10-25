package com.restor.magicka;

import net.minecraft.resources.Identifier;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

public final class MagicConstructionManager extends SavedData {
    public static final class Construction {
        public final UUID id, owner;
        public final String type;
        public final BlockPos root;
        public final Set<BlockPos> blocks = new HashSet<>();
        public long expires;
        public float integrity;
        Construction(UUID id, UUID owner, String type, BlockPos root, long expires, float integrity) {
            this.id=id;this.owner=owner;this.type=type;this.root=root;this.expires=expires;this.integrity=integrity;
        }
    }
    private final Map<UUID,Construction> structures = new HashMap<>();
    private final List<Build> builds = new ArrayList<>();
    private record Build(UUID id, UUID owner, SpellDefinition spell, Plan plan, long started, boolean self, boolean instant, int[] attempts, Set<UUID> impaled) {}
    static boolean instantBuilds;
    public MagicConstructionManager() {}
    private static final net.minecraft.world.level.saveddata.SavedDataType<MagicConstructionManager> TYPE = new net.minecraft.world.level.saveddata.SavedDataType<>(
            Identifier.fromNamespaceAndPath(Magicka.ID, "constructions"), MagicConstructionManager::new, CompoundTag.CODEC.xmap(MagicConstructionManager::load, MagicConstructionManager::save));
    public static MagicConstructionManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
    Construction structure(UUID id) { return structures.get(id); }
    public Collection<Construction> structures() { return Collections.unmodifiableCollection(structures.values()); }
    public static void start(ServerPlayer player, SpellDefinition spell, boolean self, Vec3 point) {
        if (!MagicConfig.CONSTRUCTION.get()) return;
        var manager = get(player.level());
        if (manager.builds.stream().filter(b -> b.owner.equals(player.getUUID())).count() >= 2) return;
        manager.builds.add(new Build(UUID.randomUUID(),player.getUUID(),spell,new Plan(player,spell,self,point),player.level().getGameTime(),self,instantBuilds,new int[]{0},new HashSet<>()));
    }
    static void stop(ServerPlayer player, long since) {
        get(player.level()).builds.removeIf(b -> b.owner.equals(player.getUUID()) && !b.instant && b.started >= since);
    }
    /** Immutable recipe determines shape; only bounded yaw/pitch steering changes the path. */
    public static final class Plan {
        public final BlockPos start;
        public final SpellShape shape;
        private final SpellDefinition spell;
        private BlockPos cursor;
        private Direction direction;
        private float yaw;
        private Vec3 heading;
        public final List<BlockPos> template;
        public int mainLength;
        public Plan(Player player, SpellDefinition spell, boolean self, Vec3 suppliedPoint) {
            this.spell=spell; this.shape=spell.shape()==SpellShape.SUMMON_OBJECT ? SpellShape.PILLAR : spell.shape();
            yaw=player.getYRot(); direction=Direction.fromYRot(yaw);
            var hit=SpellAim.hit(player);
            Vec3 origin=suppliedPoint==null ? SpellRuntime.origin(player,spell.origin()) : suppliedPoint;
            if (spell.construction().has("under_caster")) {
                int width=SpellDefinition.integer(spell.construction(),"width",1,1,5);
                start=player.blockPosition().below().relative(direction.getOpposite(),width/2).relative(direction.getCounterClockWise(),width/2);
            }
            else if (shape==SpellShape.SPIKE && spell.origin()==OriginMode.CASTER_FEET) start=spikeStart(player,direction);
            else if (spell.origin()==OriginMode.CASTER_FEET) start=player.blockPosition().relative(direction); // Reachable first step, one block up from the floor.
            else if (shape==SpellShape.GROWTH) start=floor(player.level(),BlockPos.containing(self ? player.position() : origin));
            else if (self || spell.origin()==OriginMode.UNDER_TARGET) start=BlockPos.containing(self ? player.position() : origin);
            else if (hit.getType()==HitResult.Type.BLOCK && spell.origin()!=OriginMode.AIM_POINT) start=hit.getBlockPos().relative(hit.getDirection());
            else start=BlockPos.containing(origin);
            cursor=start;
            var look=player.getLookAngle();
            var flat=new Vec3(look.x,0,look.z);
            heading=look.y>=0 ? look : flat.lengthSqr()>1e-4 ? flat.normalize() : Vec3.atLowerCornerOf(direction.getUnitVec3i());
            template=shape==SpellShape.SPIKE ? spike(player,self,heading) : spell.construction().has("template") ? structure(player,SpellDefinition.string(spell.construction(),"template","wall"),self) : null;
        }
        private static BlockPos floor(net.minecraft.world.level.Level level, BlockPos pos) {
            if (!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()) pos=pos.above();
            for (int i=0; i<4 && level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty(); i++) pos=pos.below();
            return pos;
        }
        public int limit(){return template==null ? spell.budget() : Math.min(spell.budget(),template.size());}
        public Vec3 heading(){return heading;}
        private static BlockPos spikeStart(Player player, Direction direction) {
            BlockPos front=player.blockPosition().relative(direction);
            for(int up=0;up<3;up++) if(WorldPlacement.replaceable(player.level().getBlockState(front.above(up)),true)) return front.above(up);
            return player.blockPosition().above(2);
        }
        private static BlockPos ground(net.minecraft.world.level.Level level,BlockPos pos){
            for(int i=0;i<3 && !WorldPlacement.replaceable(level.getBlockState(pos),true);i++)pos=pos.above();
            for(int i=0;i<3 && WorldPlacement.replaceable(level.getBlockState(pos),true) && WorldPlacement.replaceable(level.getBlockState(pos.below()),true);i++)pos=pos.below();
            return pos;
        }
        private static List<BlockPos> structure(Player player,String kind,boolean self){
            if(kind.equals("custom"))return sculpture(player,self);
            Vec3 forward=Vec3.directionFromRotation(0,player.getYRot()),feet=Vec3.atCenterOf(player.blockPosition());
            int reach=switch(kind){case "wall" -> 2;case "slope", "redoubt" -> 3;default -> 0;};
            if(reach==0 || kind.equals("wall") && player.getXRot()<-45)return template(player,kind,feet,forward);
            var hit=SpellAim.hit(player);
            BlockPos anchor=!self && hit.getType()==HitResult.Type.BLOCK ? hit.getBlockPos().relative(hit.getDirection()) : BlockPos.containing(feet.add(forward.scale(reach)));
            return template(player,kind,Vec3.atCenterOf(ground(player.level(),anchor)).subtract(forward.scale(reach)),forward);
        }
        /** The caster's own stencil: local x runs to their right, z away from them, y up from the anchor. */
        private static List<BlockPos> sculpture(Player player,boolean self){
            long[] bits=ShapePattern.of(player);
            Direction facing=Direction.fromYRot(player.getYRot());
            Vec3 forward=Vec3.directionFromRotation(0,player.getYRot()),feet=Vec3.atCenterOf(player.blockPosition());
            var hit=SpellAim.hit(player);
            boolean aimed=!self && hit.getType()==HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(player.getEyePosition())<=144;
            BlockPos anchor=aimed ? hit.getBlockPos().relative(hit.getDirection()) : ground(player.level(),BlockPos.containing(feet.add(forward.scale(4))));
            int half=ShapePattern.SIZE/2;
            var out=new ArrayList<BlockPos>();
            for(int y=0;y<ShapePattern.SIZE;y++)for(int z=0;z<ShapePattern.SIZE;z++)for(int x=0;x<ShapePattern.SIZE;x++)
                if(ShapePattern.filled(bits,x,y,z))out.add(anchor.above(y).relative(facing,z-half).relative(facing.getClockWise(),x-half));
            out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(cell->cell.distManhattan(anchor)));
            return List.copyOf(out);
        }
        /** Cells whose centres fall in a digital plane as thick as |nx|+|ny|+|nz|, so slanted surfaces stay face-connected. */
        public static List<BlockPos> template(Player player,String kind,Vec3 origin,Vec3 forward){
            var level=player.level();var out=new ArrayList<BlockPos>();
            if(kind.equals("wall") && player.getXRot()<-45)kind="tent";
            Vec3 right=new Vec3(-forward.z,0,forward.x);
            double across=Math.abs(forward.x)+Math.abs(forward.z),pitch=player.getXRot();
            boolean columns=kind.equals("wall") || kind.equals("redoubt");
            BlockPos center=BlockPos.containing(origin);
            for(int dy=columns ? 0 : -1;dy<=(columns ? 0 : 5);dy++)for(int dx=-7;dx<=7;dx++)for(int dz=-7;dz<=7;dz++){
                BlockPos cell=center.offset(dx,dy,dz);Vec3 v=Vec3.atCenterOf(cell).subtract(origin);
                double f=v.x*forward.x+v.z*forward.z,r=v.x*right.x+v.z*right.z,u=v.y;
                boolean inside=switch(kind){
                    case "wall" -> f-2>=-across/2 && f-2<across/2 && Math.abs(r)<=2.5;
                    case "redoubt" -> {double ax=Math.abs(v.x),az=Math.abs(v.z);yield f>=-.5 && Math.hypot(Math.max(ax-.5,0),Math.max(az-.5,0))<3 && Math.hypot(ax+.5,az+.5)>=3;}
                    case "slope" -> {double t=f+u-3;yield t>=-.5 && t<across+.5 && Math.abs(r)<=2.5 && u>-.5 && u<4.5;}
                    case "tent" -> {double t=u+Math.abs(r)-4;yield t>=-.5-across && t<.5 && Math.abs(f)<=2.5 && Math.abs(r)<=4.5 && u>-.5;}
                    case "slab" -> {
                        boolean vertical=pitch<-30 || pitch>50;
                        yield Math.abs(u-(pitch<-30 ? 3 : -1))<.5 && Math.abs(f-(vertical ? 0 : 3))<=2.5 && Math.abs(r)<=2.5;
                    }
                    default -> false;
                };
                if(!inside)continue;
                if(columns){BlockPos floor=ground(level,cell);for(int up=0;up<(kind.equals("wall") ? 3 : 2);up++)out.add(floor.above(up));}
                else out.add(cell);
            }
            out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(p->Vec3.atCenterOf(p).subtract(origin).horizontalDistanceSqr()));
            return List.copyOf(new LinkedHashSet<>(out));
        }
        private static Vec3 tilt(Vec3 axis,net.minecraft.util.RandomSource random,double min,double max){
            Vec3 side=Vec3.ZERO;
            while(side.lengthSqr()<1e-3)side=axis.cross(new Vec3(random.nextGaussian(),random.nextGaussian(),random.nextGaussian()));
            double angle=min+random.nextDouble()*(max-min);
            return axis.normalize().scale(Math.cos(angle)).add(side.normalize().scale(Math.sin(angle))).normalize();
        }
        private static void walk(net.minecraft.world.level.Level level,BlockPos from,Vec3 direction,int steps,List<BlockPos> out){
            // Face-connected voxel walk, so diagonal spikes become climbable stairs instead of edge-touching blocks.
            int[] crossed=new int[3];BlockPos cursor=from;
            for(int i=0;i<steps;i++){
                Direction.Axis next=null;
                for(var axis:Direction.Axis.values()){
                    double d=Math.abs(direction.get(axis));
                    if(d<1e-4)continue;
                    if(next==null || (.5+crossed[axis.ordinal()])/d<(.5+crossed[next.ordinal()])/Math.abs(direction.get(next)))next=axis;
                }
                if(next==null)next=Direction.Axis.Y;
                crossed[next.ordinal()]++;
                cursor=cursor.relative(Direction.fromAxisAndDirection(next,direction.get(next)>=0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE));
                if(out.contains(cursor) || !WorldPlacement.replaceable(level.getBlockState(cursor),true))return;
                out.add(cursor);
            }
        }
        private List<BlockPos> spike(Player player,boolean self,Vec3 look){
            var random=player.getRandom();var level=player.level();var hit=SpellAim.hit(player);
            BlockPos from;
            if(!self && hit.getType()==HitResult.Type.BLOCK && WorldPlacement.replaceable(level.getBlockState(hit.getBlockPos().relative(hit.getDirection())),true)){
                from=hit.getBlockPos().relative(hit.getDirection());heading=tilt(Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()),random,0,.4);
            }
            else{from=spikeStart(player,direction);heading=tilt(look,random,0,.12);}
            int budget=spell.budget();
            var cells=new ArrayList<BlockPos>();cells.add(from);
            walk(level,from,heading,budget*2/3-1+random.nextInt(budget/3+1),cells);
            mainLength=cells.size();
            for(int branch=random.nextInt(3);branch>0 && mainLength>3 && cells.size()<budget;branch--)
                walk(level,cells.get(2+random.nextInt(mainLength-2)),tilt(heading,random,.6,1.1),Math.min(budget-cells.size(),2+random.nextInt(3)),cells);
            return List.copyOf(cells);
        }
        public BlockPos next(Player player, int index) {
            // Vanilla rotation packets are authoritative input; turns are rate-limited per segment.
            float difference=net.minecraft.util.Mth.wrapDegrees(player.getYRot()-yaw);
            yaw+=Math.clamp(difference,-35,35); direction=Direction.fromYRot(yaw);
            if (template!=null) return index<template.size() ? template.get(index) : null;
            if (index==0 && shape!=SpellShape.GROWTH) return start;
            int width=SpellDefinition.integer(spell.construction(),"width",1,1,5);
            return switch(shape) {
                case PILLAR -> start.above(index);
                case PLATFORM -> {
                    if (spell.origin()==OriginMode.UNDER_TARGET) yield start.above(index);
                    int row=index/width, column=index%width;
                    // Snake rows keep each new block adjacent to the existing platform.
                    int side=row%2==0 ? column : width-1-column;
                    yield start.relative(direction,row).relative(direction.getClockWise(),side);
                }
                case WALL -> {
                    int height=SpellDefinition.integer(spell.construction(),"height",3,1,5);
                    int column=index/height, vertical=column%2==0 ? index%height : height-1-index%height;
                    yield start.relative(direction.getClockWise(),column).above(vertical);
                }
                case GROWTH -> {
                    if (index==16) yield start.above(2);
                    int cell=index>16 ? index-1 : index, ring=cell%8, layer=cell/8;
                    if (layer>2) yield null;
                    int[] dx={1,1,0,-1,-1,-1,0,1}, dz={0,1,1,1,0,-1,-1,-1};
                    yield start.offset(dx[ring],layer,dz[ring]);
                }
                case STAIR, RAMP -> {
                    // Alternate horizontal and vertical segments; slope never exceeds one block per step.
                    int run=player.getXRot()>-12 ? 3 : 2;
                    cursor=index%run==0 ? cursor.above() : cursor.relative(direction);
                    yield cursor;
                }
                case BRIDGE -> {
                    if (player.getXRot() < -25 && index%3==0) cursor=cursor.above();
                    else if (player.getXRot()>25 && index%3==0) cursor=cursor.below();
                    else cursor=cursor.relative(direction);
                    yield cursor;
                }
                default -> start.relative(direction,index);
            };
        }
    }
    private static void impale(ServerPlayer player, Build build, BlockPos pos) {
        double damage=EffectGraph.number(build.spell.construction(),"impale_damage",0);
        Vec3 push=build.plan.heading().scale(build.instant ? 1.1 : .7).add(0,.25,0);
        for (var entity:player.level().getEntitiesOfClass(LivingEntity.class,new AABB(pos),LivingEntity::isAlive)) {
            if (entity==player || !EffectGraph.canHarm(player,entity) || !build.impaled.add(entity.getUUID())) continue;
            if (damage>0) com.restor.magicka.enemy.MagicEnemies.hurt(entity,player.damageSources().indirectMagic(player,player),(float)(build.instant ? damage*1.5 : damage),player,build.spell,MagicStatuses.has(entity,MagicStatuses.Status.FROZEN) || MagicStatuses.has(entity,MagicStatuses.Status.BRITTLE));
            MagicStatuses.apply(entity,MagicStatuses.Status.CHILLED,60,1);
            entity.push(push.x,push.y,push.z);entity.syncVelocity=true;
        }
    }
    public static Block material(SpellDefinition spell) {
        return switch(spell.material()) { case "ice" -> MagicBlocks.ICE.get();case "root" -> MagicBlocks.ROOT.get();case "arcane" -> MagicBlocks.ARCANE.get();case "copper" -> MagicBlocks.COPPER.get();default -> MagicBlocks.EARTH.get(); };
    }
    private void tick(ServerLevel level) {
        var iterator=builds.iterator();
        while(iterator.hasNext()) {
            Build build=iterator.next();
            ServerPlayer player=level.getServer().getPlayerList().getPlayer(build.owner);
            // Fake players used by GameTests are resolved by the runtime owner cache.
            if (player==null) player=SpellRuntime.owner(level,build.owner);
            if (player==null || !player.isAlive() || player.level()!=level) { iterator.remove();continue; }
            if (!build.instant && (level.getGameTime()-build.started)%build.spell.interval()!=0) continue;
            int limit=build.plan.limit();
            int steps=build.instant ? limit : SpellDefinition.integer(build.spell.construction(),"batch",1,1,64);
            for(int step=0;step<steps && build.attempts[0]<limit;step++) {
                int index=build.attempts[0]++;
                BlockPos pos=build.plan.next(player,index);
                if (pos==null) { build.attempts[0]=limit; break; }
                if (build.plan.shape==SpellShape.SPIKE) {
                    if (!WorldPlacement.replaceable(level.getBlockState(pos),true)) continue;
                    impale(player,build,pos);
                }
                Construction existing=structures.get(build.id); BlockPos root=existing==null ? pos : existing.root;
                boolean airAnchor=existing==null && build.spell.floating() && !hasSupport(level,pos) && !MagicConfig.permanent(build.spell.material());
                boolean tesla=build.spell.delivery().has("tesla");
                Block block=airAnchor ? MagicBlocks.ARCANE.get() : tesla && index==build.spell.budget()-1 ? MagicBlocks.COIL.get() : material(build.spell);
                boolean spike=build.plan.shape==SpellShape.SPIKE;
                boolean template=build.plan.template!=null;
                boolean push=build.plan.shape==SpellShape.PILLAR || build.spell.origin()==OriginMode.UNDER_TARGET || build.self || spike || template;
                if (WorldPlacement.placeBlock(player,pos,block.defaultBlockState(),build.id,root,build.spell.floating() || spike || template,push,null)) {
                    var data=(MagicBlocks.MagicBlockEntity)level.getBlockEntity(pos);
                    int defaultLife=switch(build.spell.material()) {case "ice" -> MagicConfig.ICE_LIFE.get();case "root" -> MagicConfig.ROOT_LIFE.get();case "arcane" -> MagicConfig.ARCANE_LIFE.get();default -> MagicConfig.EARTH_LIFE.get();};
                    if (airAnchor) defaultLife=MagicConfig.ARCANE_LIFE.get();
                    int life=(int)Math.clamp(SpellDefinition.integer(build.spell.construction(),"lifetime_ticks",defaultLife,20,72000)*EffectGraph.number(build.spell.construction(),"lifetime_scale",1),20,72000);
                    data.expires=MagicConfig.LIFETIME.get() && !MagicConfig.permanent(airAnchor ? "arcane" : build.spell.material()) ? level.getGameTime()+life : Long.MAX_VALUE;
                    data.integrity=(float)EffectGraph.number(build.spell.construction(),"integrity",8);data.maxIntegrity=data.integrity;
                    data.regenerates=build.spell.construction().has("regenerates") && build.spell.construction().get("regenerates").getAsBoolean();
                    data.floating=pos.equals(root); // Dependents still collapse when a floating anchor is destroyed.
                    data.material=build.spell.material(); data.setChanged();
                    if(existing==null) { existing=new Construction(build.id,build.owner,build.plan.shape.name(),pos,data.expires,data.integrity);structures.put(build.id,existing); }
                    existing.blocks.add(pos.immutable());existing.expires=Math.max(existing.expires,data.expires);setDirty();
                    if (existing.blocks.size()==1 && tesla) SpellRuntime.tesla(player,build.spell,build.id);
                    if(build.plan.shape==SpellShape.GROWTH)for(var entity:level.getEntitiesOfClass(LivingEntity.class,new AABB(build.plan.start).inflate(1.8)))
                        EffectGraph.entity(player,entity,build.spell,build.self?build.spell.selfEffects():build.spell.effects(),net.minecraft.world.phys.Vec3.atCenterOf(build.plan.start),build.self);
                    MagicNetwork.construction(player,build.id,existing.blocks.size(),limit-build.attempts[0]);
                }
            }
            if(build.attempts[0]>=build.plan.limit()) iterator.remove();
        }
        if(level.getGameTime()%20==0) {
            var all=structures.values().iterator();
            while(all.hasNext()) {
                Construction c=all.next();
                boolean removed=c.blocks.removeIf(p -> level.hasChunkAt(p) && (!(level.getBlockEntity(p) instanceof MagicBlocks.MagicBlockEntity d) || !d.construction.equals(c.id)));
                if(removed) setDirty();
                if(c.blocks.isEmpty()) { all.remove();setDirty(); }
            }
        }
    }
    private static boolean hasSupport(ServerLevel level, BlockPos pos) {
        for(Direction direction:Direction.values()) if(level.hasChunkAt(pos.relative(direction)) && !level.getBlockState(pos.relative(direction)).isAir()) return true;
        return false;
    }
    @SubscribeEvent public void levelTick(LevelTickEvent.Post event) { if(event.getLevel() instanceof ServerLevel level) get(level).tick(level); }
    CompoundTag save() {
        CompoundTag tag=new CompoundTag();ListTag list=new ListTag();
        for(Construction c:structures.values()) {
            var n=new CompoundTag();n.store("Id",net.minecraft.core.UUIDUtil.CODEC,c.id);n.store("Owner",net.minecraft.core.UUIDUtil.CODEC,c.owner);n.putString("Type",c.type);n.putLong("Root",c.root.asLong());n.putLong("Expires",c.expires);n.putFloat("Integrity",c.integrity);
            n.putLongArray("Blocks",c.blocks.stream().mapToLong(BlockPos::asLong).toArray());list.add(n);
        }
        tag.put("Structures",list);return tag;
    }
    private static MagicConstructionManager load(CompoundTag tag) {
        var manager=new MagicConstructionManager();
        for(Tag t:tag.getListOrEmpty("Structures")) {
            CompoundTag n=(CompoundTag)t;
            var id=n.read("Id",net.minecraft.core.UUIDUtil.CODEC);var owner=n.read("Owner",net.minecraft.core.UUIDUtil.CODEC);if(id.isEmpty()||owner.isEmpty())continue;
            Construction c=new Construction(id.get(),owner.get(),n.getStringOr("Type",""),BlockPos.of(n.getLongOr("Root",0L)),n.getLongOr("Expires",0L),n.getFloatOr("Integrity",0f));
            for(long p:n.getLongArray("Blocks").orElse(new long[0])) c.blocks.add(BlockPos.of(p));manager.structures.put(c.id,c);
        }
        return manager;
    }
}
