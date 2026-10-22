package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.UUID;

public final class MagicBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Magicka.ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Magicka.ID);
    public static final DeferredBlock<Block> EARTH = BLOCKS.registerBlock("magic_earth", TemporaryBlock::new,
            () -> properties().strength(1.5F).sound(SoundType.STONE));
    public static final DeferredBlock<Block> ICE = BLOCKS.registerBlock("magic_ice", TemporaryBlock::new,
            () -> properties().strength(0.4F).friction(0.98F).sound(SoundType.GLASS));
    public static final DeferredBlock<Block> ROOT = BLOCKS.registerBlock("magic_root", TemporaryBlock::new,
            () -> properties().strength(0.8F).sound(SoundType.WOOD));
    public static final DeferredBlock<Block> ARCANE = BLOCKS.registerBlock("magic_arcane", TemporaryBlock::new,
            () -> properties().strength(1F).lightLevel(s -> 10).sound(SoundType.AMETHYST));
    public static final DeferredBlock<Block> PUDDLE = BLOCKS.registerBlock("magic_puddle", TemporaryBlock::new,
            () -> properties().strength(0F).noCollision().noOcclusion().sound(SoundType.SLIME_BLOCK));
    public static final DeferredBlock<Block> LIGHT = BLOCKS.registerBlock("magic_light", TemporaryBlock::new,
            () -> properties().strength(0F).noCollision().noOcclusion().lightLevel(s -> 15).sound(SoundType.AMETHYST_CLUSTER));
    public static final DeferredBlock<Block> GLOW = BLOCKS.registerBlock("magic_glowstone", TemporaryBlock::new,
            () -> properties().strength(0.3F).lightLevel(s -> 15).sound(SoundType.GLASS));
    public static final DeferredBlock<Block> COPPER = BLOCKS.registerBlock("magic_copper", TemporaryBlock::new,
            () -> properties().strength(1.5F).sound(SoundType.COPPER));
    public static final DeferredBlock<Block> COIL = BLOCKS.registerBlock("magic_tesla_coil", TemporaryBlock::new,
            () -> properties().strength(1.5F).lightLevel(s -> 13).sound(SoundType.COPPER_BULB));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagicBlockEntity>> DATA = ENTITIES.register(
            "construction", () -> new BlockEntityType<>(MagicBlockEntity::new, EARTH.get(), ICE.get(), ROOT.get(), ARCANE.get(), PUDDLE.get(), LIGHT.get(), GLOW.get(), COPPER.get(), COIL.get()));

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().noLootTable().pushReaction(PushReaction.IMMOVEABLE);
    }

    public static final class TemporaryBlock extends Block implements EntityBlock {
        TemporaryBlock(Properties properties) { super(properties); }
        @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
            if (state.is(PUDDLE.get())) return Block.box(0, 0, 0, 16, 1, 16);
            if (state.is(ROOT.get())) return Block.box(2, 0, 2, 14, 16, 14);
            if (state.is(LIGHT.get())) return Block.box(5, 5, 5, 11, 11, 11);
            return super.getShape(state, level, pos, context);
        }
        @Override protected void entityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effects, boolean precise) {
            if (!level.isClientSide() && state.is(PUDDLE.get()) && entity instanceof net.minecraft.world.entity.LivingEntity living) {
                MagicStatuses.apply(living, MagicStatuses.Status.WET, 100, 1); living.clearFire();
            }
            if (state.is(ROOT.get()) && entity instanceof net.minecraft.world.entity.Mob living) {
                living.makeStuckInBlock(state, new net.minecraft.world.phys.Vec3(0.6, 1, 0.6));
            }
        }
        @Override protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
            return state.is(LIGHT.get()) ? net.minecraft.world.level.block.RenderShape.INVISIBLE : super.getRenderShape(state);
        }
        @Override public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
            if (state.is(COIL.get()) && random.nextInt(4) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, .05, 0);
            if (!state.is(LIGHT.get())) return;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.getX() + .5 + (random.nextDouble() - .5) * .2, pos.getY() + .5 + (random.nextDouble() - .5) * .2, pos.getZ() + .5 + (random.nextDouble() - .5) * .2, 0, .005, 0);
            if (random.nextInt(3) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.WAX_OFF, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 0, 0, 0);
        }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MagicBlockEntity(pos, state); }
        @Override public boolean isLadder(BlockState state,net.minecraft.world.level.LevelReader level,BlockPos pos,net.minecraft.world.entity.LivingEntity entity){return state.is(ROOT.get());}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide() || type != DATA.get()) return null;
            return (world, pos, block, data) -> ((MagicBlockEntity) data).tick((ServerLevel) world);
        }
    }

    /** Players climb root blocks like vines by pushing into them; runs on both sides because player movement is client-driven. */
    public static void climbRoots(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre event) {
        var player=event.getEntity();
        if (!player.horizontalCollision || player.isSpectator() || player.getAbilities().flying || !touchesRoot(player)) return;
        var motion=player.getDeltaMovement();
        player.setDeltaMovement(motion.x,player.isShiftKeyDown() ? Math.max(motion.y,-.15) : Math.max(motion.y,.2),motion.z);
        player.resetFallDistance();
    }
    static boolean touchesRoot(net.minecraft.world.entity.player.Player player) {
        var box=player.getBoundingBox().inflate(.1,0,.1);
        return BlockPos.betweenClosedStream(box).anyMatch(pos->player.level().getBlockState(pos).is(ROOT.get()));
    }

    public static final class MagicBlockEntity extends BlockEntity {
        public UUID owner = new UUID(0, 0);
        public UUID construction = UUID.randomUUID();
        public BlockPos root;
        public long expires = Long.MAX_VALUE;
        public boolean floating;
        public float integrity = 8, maxIntegrity = 8, heat;
        public String material = "earth";
        public boolean regenerates;

        public MagicBlockEntity(BlockPos pos, BlockState state) { super(DATA.get(), pos, state); root = pos; }
        public void initialize(UUID owner, UUID construction, BlockPos root, long expires, boolean floating) {
            this.owner = owner; this.construction = construction; this.root = root.immutable();
            this.expires = expires; this.floating = floating; setChanged();
            material=getBlockState().is(ICE.get())?"ice":getBlockState().is(ROOT.get())?"root":getBlockState().is(ARCANE.get())?"arcane":getBlockState().is(LIGHT.get())?"light":getBlockState().is(GLOW.get())?"glowstone":getBlockState().is(COPPER.get()) || getBlockState().is(COIL.get())?"copper":"earth";
        }
        private void tick(ServerLevel world) {
            if (integrity <= 0) { world.removeBlock(worldPosition, false); return; }
            if (world.getGameTime() % 40 == 0 && regenerates && integrity < maxIntegrity) { integrity = Math.min(maxIntegrity,integrity+1); setChanged(); }
            if (heat > 0 && world.getGameTime() % 20 == 0) {
                for (var entity : world.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(worldPosition).inflate(.05))) {
                    var caster=world.getServer().getPlayerList().getPlayer(owner);
                    if (caster != null && EffectGraph.canHarm(caster,entity)) MagicStatuses.apply(entity,MagicStatuses.Status.BURNING,40,1);
                }
                heat=Math.max(0,heat-10);setChanged();
            }
            if (world.getGameTime() >= expires && !MagicConfig.permanent(material)) { world.removeBlock(worldPosition, false); return; }
            if (world.getGameTime() % 10 != 0 || floating || !MagicConfig.COLLAPSE.get() || root.equals(worldPosition)) return;
            // Never force-load the root chunk just to check its support.
            if (world.hasChunkAt(root) && (!(world.getBlockEntity(root) instanceof MagicBlockEntity anchor)
                    || !anchor.construction.equals(construction))) world.removeBlock(worldPosition, false);
        }
        @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
            super.saveAdditional(tag);
            tag.store("Owner", net.minecraft.core.UUIDUtil.CODEC, owner); tag.store("Construction", net.minecraft.core.UUIDUtil.CODEC, construction);
            tag.putLong("Root", root.asLong()); tag.putLong("Expires", expires); tag.putBoolean("Floating", floating);
            tag.putFloat("Integrity",integrity);tag.putFloat("MaxIntegrity",maxIntegrity);tag.putFloat("Heat",heat);tag.putString("Material",material);tag.putBoolean("Regenerates",regenerates);
        }
        @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
            super.loadAdditional(tag);
            tag.read("Owner", net.minecraft.core.UUIDUtil.CODEC).ifPresent(id -> owner = id);
            tag.read("Construction", net.minecraft.core.UUIDUtil.CODEC).ifPresent(id -> construction = id);
            root = tag.getLong("Root").map(BlockPos::of).orElse(worldPosition);
            expires = tag.getLongOr("Expires", 0);
            floating = tag.getBooleanOr("Floating", false);
            integrity=tag.getFloatOr("Integrity", 8);maxIntegrity=tag.getFloatOr("MaxIntegrity", 8);
            heat=tag.getFloatOr("Heat", 0);material=tag.getStringOr("Material", "earth");regenerates=tag.getBooleanOr("Regenerates", false);
        }
    }
    private MagicBlocks() {}
}
