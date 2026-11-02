package com.restor.magicka;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/** A creature killed by a spell bursts into its lead element: ash, ice shards, sparks, rubble, spray, petals, light or souls. */
public final class DeathFx {
    public static void play(LivingEntity e, SpellFormula f) {
        if (!(e.level() instanceof ServerLevel level) || e instanceof Player || f.size() == 0) return;
        double x = e.getX(), y = e.getY() + e.getBbHeight() * .5, z = e.getZ(), w = e.getBbWidth() * .5, h = e.getBbHeight() * .4;
        ParticleOptions main, extra;
        SoundEvent sound;
        switch (FusionRules.lead(f)) {
            case FIRE -> { main = ParticleTypes.LARGE_SMOKE; extra = ParticleTypes.LAVA; sound = SoundEvents.FIRE_EXTINGUISH; }
            case COLD, ICE -> { main = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()); extra = ParticleTypes.SNOWFLAKE; sound = SoundEvents.GLASS_BREAK; }
            case LIGHTNING -> { main = ParticleTypes.ELECTRIC_SPARK; extra = ParticleTypes.SMOKE; sound = SoundEvents.LIGHTNING_BOLT_IMPACT; }
            case EARTH -> { main = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()); extra = ParticleTypes.POOF; sound = SoundEvents.STONE_BREAK; }
            case WATER -> { main = ParticleTypes.SPLASH; extra = ParticleTypes.BUBBLE_POP; sound = SoundEvents.GENERIC_SPLASH; }
            case STEAM -> { main = ParticleTypes.CLOUD; extra = ParticleTypes.SPLASH; sound = SoundEvents.LAVA_EXTINGUISH; }
            case LIFE -> { main = ParticleTypes.CHERRY_LEAVES; extra = ParticleTypes.HAPPY_VILLAGER; sound = SoundEvents.AZALEA_LEAVES_BREAK; }
            case ARCANE -> { main = ParticleTypes.END_ROD; extra = ParticleTypes.WITCH; sound = SoundEvents.AMETHYST_CLUSTER_BREAK; }
            case DEATH -> { main = ParticleTypes.SOUL; extra = ParticleTypes.SCULK_SOUL; sound = SoundEvents.SOUL_ESCAPE.value(); }
            default -> { return; }
        }
        EpicMagic.fx(level, main, x, y, z, 40, w, h, w, .12);
        EpicMagic.fx(level, extra, x, y, z, 12, w, h, w, .05);
        level.playSound(null, e.blockPosition(), sound, SoundSource.HOSTILE, 1f, .8f + level.getRandom().nextFloat() * .4f);
    }
}
