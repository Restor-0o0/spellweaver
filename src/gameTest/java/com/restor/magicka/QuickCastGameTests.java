package com.restor.magicka;

import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.nio.file.*;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class QuickCastGameTests {
    @GameTest(template="empty")
    public static void gradualFillNeverAutoCasts(GameTestHelper h){
        var p=RecipeGameTests.caster(h);int packed=RecipeGameTests.spell("Stone Stair").formula().prepared().packed();
        CastingServer.quickFill(p,packed);h.assertTrue(CastingServer.snapshot(p).formula()==0,"Fill starts empty");
        var server=new CastingServer();
        for(int tick=1;tick<=20;tick++){
            server.tick(new PlayerTickEvent.Post(p));var state=CastingServer.snapshot(p);
            h.assertTrue(Formula.unpack(state.formula()).size()==Math.min(Formula.unpack(packed).size(),tick/CastingServer.QUEUE_TICKS_PER_ELEMENT),"One element every four ticks");
            h.assertTrue(state.remaining()==0,"Quick fill never commits a cast");
        }
        h.assertTrue(Formula.unpack(CastingServer.snapshot(p).formula()).key().equals(Formula.unpack(packed).key()),"Final formula matches preset");
        CastingServer.input(p,9);h.assertTrue(CastingServer.snapshot(p).remaining()>0,"Player can cast manually after filling");h.succeed();
    }
    @GameTest(template="empty")
    public static void cancelFillAndProtectCommittedCast(GameTestHelper h){
        var p=RecipeGameTests.caster(h);var other=RecipeGameTests.caster(h);int f=RecipeGameTests.spell("Firebomb").formula().prepared().packed();
        CastingServer.quickFill(p,f);var server=new CastingServer();for(int i=0;i<8;i++)server.tick(new PlayerTickEvent.Post(p));
        CastingServer.input(p,8);for(int i=0;i<25;i++)server.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(CastingServer.snapshot(p).formula()==0,"Clear cancels pending fill");
        CastingServer.input(p,0);CastingServer.input(p,10);var before=CastingServer.snapshot(p);
        CastingServer.quickFill(p,f);h.assertTrue(CastingServer.snapshot(p).equals(before),"Preset cannot replace committed cast");
        CastingServer.quickFill(other,f);h.assertTrue(CastingServer.snapshot(p).equals(before),"Other player's fill is independent");h.succeed();
    }
    @GameTest(template="empty")
    public static void presetPersistenceAndValidation(GameTestHelper h){
        Path file=null;
        try{
            file=Files.createTempFile("magicka-grimoire-test",".json");var state=new GrimoireState();state.selectedId="magicka:stone_stair";state.query="#созидание";state.tag="construction";state.page=3;state.scroll=7;state.size=4;state.elements=1|1<<4;
            int formula=RecipeGameTests.spell("Stone Stair").formula().prepared().packed();state.assign(2,formula);state.save(file);
            var restored=GrimoireState.read(file);h.assertTrue(restored.slot(2)==formula && restored.page==3 && restored.scroll==7 && restored.tag.equals("construction") && restored.size==4 && restored.elements==(1|1<<4) && restored.selectedId.equals(state.selectedId) && restored.query.equals(state.query),"Book position, filters and slots survive restart");
            h.assertFalse(GrimoireState.valid(-1),"Negative formula rejected");h.assertFalse(GrimoireState.valid(formula|(1<<24)),"Oversized formula rejected");h.succeed();
        }catch(Exception e){h.fail(e.toString());}finally{if(file!=null)try{Files.deleteIfExists(file);}catch(Exception ignored){}}
    }
    @GameTest(template="empty")
    public static void constructionTagsReflectBehavior(GameTestHelper h){
        h.assertTrue(SpellTags.of(RecipeGameTests.spell("Stone Stair")).contains("construction"),"Stairs have creation tag");
        h.assertTrue(SpellTags.of(RecipeGameTests.spell("Ice Step")).contains("construction"),"Ice foothold has creation tag");
        h.assertFalse(SpellTags.of(RecipeGameTests.spell("Pebble")).contains("construction"),"Earth projectile is not a building spell");
        h.assertTrue(SpellTags.of(RecipeGameTests.spell("Guardian Stone")).contains("summon"),"Summons have their own category");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void instantModeCastsWithoutWindup(GameTestHelper h){
        var p=RecipeGameTests.caster(h);p.setXRot(-45);h.setBlock(new BlockPos(1,1,2),Blocks.STONE);
        var spike=RecipeGameTests.spell("Ice Spike");var plan=new MagicConstructionManager.Plan(p,spike,true,null);
        BlockPos previous=plan.next(p,0);int rises=0;
        for(int i=1;i<plan.mainLength;i++){BlockPos next=plan.next(p,i);h.assertTrue(next.distManhattan(previous)==1,"Spike cells share a face");if(next.getY()>previous.getY())rises++;previous=next;}
        h.assertTrue(plan.mainLength>=5 && rises>=plan.mainLength/4 && rises<=plan.mainLength*3/4,"Diagonal spike climbs like a stair");
        var shapes=new java.util.HashSet<java.util.List<BlockPos>>();for(int i=0;i<8;i++)shapes.add(new MagicConstructionManager.Plan(p,spike,true,null).template);
        h.assertTrue(shapes.size()>1,"Spike shape varies between casts");
        h.assertFalse(spike.channeled(),"Spike is instant");h.assertTrue(RecipeGameTests.spell("Jet").channeled(),"Water spray is channeled");
        h.assertTrue(RecipeGameTests.spell("Thunder Lance").channeled(),"Four-lightning chain is channeled");h.assertFalse(RecipeGameTests.spell("Pebble").channeled(),"Projectile is instant");

        CastingServer.input(p,7);CastingServer.input(p,11);h.assertTrue(CastingServer.snapshot(p).instant(),"Mode toggles on");
        for(int e:new int[]{1,1,2,6})CastingServer.input(p,e);CastingServer.input(p,9);
        var state=CastingServer.snapshot(p);
        h.assertTrue(state.formula()==0 && state.remaining()==0,"Instant cast skips windup");
        h.runAfterDelay(2,()->{
            var placed=MagicConstructionManager.get(h.getLevel()).structures().stream().filter(c->c.owner.equals(p.getUUID())).flatMap(c->c.blocks.stream()).map(h::relativePos).toList();
            h.assertTrue(placed.size()>=5,"Whole spike appears in one tick, got "+placed);h.succeed();
        });
    }
    @GameTest(template="empty")
    public static void channeledSpellsRunWhileHeld(GameTestHelper h){
        var p=RecipeGameTests.caster(h);var server=new CastingServer();
        CastingServer.input(p,7);CastingServer.input(p,11);CastingServer.input(p,1);CastingServer.input(p,1);CastingServer.input(p,9);
        var state=CastingServer.snapshot(p);
        h.assertTrue(state.channeling() && state.remaining()>0 && state.remaining()==state.total(),"Channel starts at once");
        for(int i=0;i<10;i++)server.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(CastingServer.snapshot(p).channeling(),"Channel keeps running");
        CastingServer.input(p,12);state=CastingServer.snapshot(p);
        h.assertTrue(!state.channeling() && state.remaining()==0 && state.formula()==0 && state.instant(),"Release ends the channel");
        CastingServer.input(p,0);CastingServer.input(p,0);CastingServer.input(p,11);
        h.assertFalse(CastingServer.snapshot(p).instant(),"Mode toggles off");CastingServer.input(p,9);
        h.assertTrue(CastingServer.snapshot(p).remaining()>0 && !CastingServer.snapshot(p).channeling(),"Normal mode keeps windup");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void secretSequenceRaisesTower(GameTestHelper h){
        var p=RecipeGameTests.caster(h);var level=h.getLevel();p.setXRot(-30);
        BlockPos base=SecretBuild.base(p);var shape=SecretBuild.shape(base,p.getDirection());
        int bottom=shape.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow(),top=shape.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow();
        h.assertTrue(top-bottom+1==SecretBuild.HEIGHT && shape.size()>400,"Tall solid shape, got "+shape.size());
        BlockPos obstacle=base.above(8);level.setBlock(obstacle,Blocks.STONE.defaultBlockState(),3);
        CastingServer.input(p,7);CastingServer.input(p,11);for(int e:new int[]{4,5,4,5})CastingServer.input(p,e);CastingServer.input(p,9);
        h.assertTrue(CastingServer.snapshot(p).formula()==0,"Wrong order is a normal cast");
        for(int e:new int[]{4,5,5,4})CastingServer.input(p,e);CastingServer.input(p,9);
        h.runAfterDelay(45,()->{
            h.assertTrue(level.getBlockState(obstacle).is(Blocks.PINK_TERRACOTTA),"Obstacle replaced by the shaft, got "+level.getBlockState(obstacle));
            h.assertFalse(level.getBlockState(base.above(SecretBuild.HEIGHT-1)).isAir(),"Tip reaches full height");
            h.assertTrue(RecipeRegistry.recipes().stream().noneMatch(d->d.name().toLowerCase(java.util.Locale.ROOT).contains("secret")),"Hidden from the book");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void tappedSkybridgeKeepsGrowing(GameTestHelper h){
        var p=RecipeGameTests.caster(h);h.setBlock(new BlockPos(1,1,4),Blocks.STONE);p.setXRot(30);var server=new CastingServer();
        CastingServer.input(p,7);CastingServer.input(p,11);for(int e:new int[]{4,4,4,6})CastingServer.input(p,e);CastingServer.input(p,9);CastingServer.input(p,12);
        h.assertTrue(CastingServer.snapshot(p).channeling(),"Releasing does not cut a growing construction");
        for(int t=1;t<=30;t++)h.runAfterDelay(t,()->server.tick(new PlayerTickEvent.Post(p)));
        h.runAfterDelay(31,()->{
            int blocks=MagicConstructionManager.get(h.getLevel()).structures().stream().filter(c->c.owner.equals(p.getUUID())).mapToInt(c->c.blocks.size()).sum();
            h.assertTrue(blocks>=5,"Bridge keeps growing after a tap, got "+blocks);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void everyRecipeInstant(GameTestHelper h){
        var errors=new java.util.ArrayList<String>();
        for(var d:RecipeRegistry.recipes())for(boolean self:new boolean[]{false,true}){
            var p=RecipeGameTests.caster(h);
            try{CastingServer.input(p,7);CastingServer.input(p,11);for(var e:d.formula().prepared().elements())CastingServer.input(p,e==Element.DEATH?13:e.ordinal());CastingServer.input(p,self?10:9);
                var st=CastingServer.snapshot(p);if(st.formula()!=0 && !st.channeling())errors.add(d.name()+(self?" self":"")+" "+st);
                CastingServer.input(p,12);}catch(Exception ex){errors.add(d.name()+(self?" self ":" ")+ex);}
        }
        if(errors.isEmpty())h.succeed();else h.fail(String.join("; ",errors.subList(0,Math.min(20,errors.size()))));
    }
    @GameTest(template="empty")
    public static void rootsAreClimbable(GameTestHelper h){
        var p=RecipeGameTests.caster(h);p.setPos(h.absoluteVec(new Vec3(1.5,2,1.65)));h.setBlock(new BlockPos(1,2,2),MagicBlocks.ROOT.get());
        p.setDeltaMovement(Vec3.ZERO);p.horizontalCollision=true;
        MagicBlocks.climbRoots(new PlayerTickEvent.Pre(p));
        h.assertTrue(p.getDeltaMovement().y>=.2,"Pushing into roots lifts the player");h.succeed();
    }
    @GameTest(template="empty",batch="distant",timeoutTicks=100)
    public static void meteorBeyondTenChunks(GameTestHelper h){
        var p=RecipeGameTests.caster(h);var level=h.getLevel();
        BlockPos target=h.absolutePos(new BlockPos(1,3,170));var forced=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int z=p.blockPosition().getZ()>>4;z<=(target.getZ()>>4)+1;z++){
            var chunk=new net.minecraft.world.level.ChunkPos(target.getX()>>4,z);
            if(!level.getForcedChunks().contains(chunk.toLong())){level.setChunkForced(chunk.x,chunk.z,true);forced.add(chunk);}
            level.getChunk(chunk.x,chunk.z);
        }
        for(int z=p.blockPosition().getZ()+1;z<target.getZ();z++)for(int y=target.getY();y<=target.getY()+1;y++)level.setBlock(new BlockPos(target.getX(),y,z),Blocks.AIR.defaultBlockState(),3);
        level.setBlock(target,Blocks.STONE.defaultBlockState(),3);p.setYRot(0);p.setXRot(0);
        var hit=SpellAim.hit(p);h.assertTrue(hit.getBlockPos().equals(target) && p.distanceToSqr(hit.getLocation())>160*160,"Aim reaches a target over ten chunks away");
        SpellRuntime.cast(p,RecipeGameTests.spell("Arcane Meteor"),false);
        h.runAfterDelay(45,()->{try{h.assertTrue(level.getBlockState(target).isAir(),"Meteor reaches and destroys distant target");h.succeed();}finally{for(var c:forced)level.setChunkForced(c.x,c.z,false);}});
    }
}
