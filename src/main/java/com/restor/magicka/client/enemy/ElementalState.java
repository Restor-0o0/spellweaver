package com.restor.magicka.client.enemy;

import com.restor.magicka.Element;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class ElementalState extends LivingEntityRenderState {
    public int action, previousAction;
    public float actionTime, emergence;
    public boolean titan;
    public Element element = Element.FIRE;
    public Identifier texture;
}
