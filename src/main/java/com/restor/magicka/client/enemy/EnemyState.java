package com.restor.magicka.client.enemy;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class EnemyState extends LivingEntityRenderState {
    public int action, state;
    public float actionTime, attackTime;
    public boolean targeting, burrowed;
}
