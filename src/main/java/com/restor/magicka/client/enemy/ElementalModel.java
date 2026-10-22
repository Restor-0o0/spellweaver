package com.restor.magicka.client.enemy;

import com.restor.magicka.ElementalTitan;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import java.util.HashMap;
import java.util.Map;

public class ElementalModel extends EntityModel<ElementalState> {
    private static final ModelPart NONE = new ModelPart(java.util.List.of(), Map.of());
    private final java.util.function.Function<String, ModelPart> lookup;
    private final String id;
    private final Map<String, ModelPart> parts = new HashMap<>();

    public ElementalModel(ModelPart root, String id) { super(root); this.lookup = root.createPartLookup(); this.id = id; }
    private ModelPart part(String name) { return parts.computeIfAbsent(name, n -> java.util.Objects.requireNonNullElse(lookup.apply(n), NONE)); }
    private void mirror(String left, float x, float y, float z) {
        ModelPart l = part(left), r = part(left.replace("left", "right"));
        l.xRot += x; l.yRot += y; l.zRot += z;
        r.xRot += x; r.yRot -= y; r.zRot -= z;
    }
    private static float ease(float t) { t = Mth.clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    @Override public void setupAnim(ElementalState mob) {
        super.setupAnim(mob);
        float swing = mob.walkAnimationPos, amount = mob.walkAnimationSpeed, age = mob.ageInTicks, yaw = mob.yRot, pitch = mob.xRot;
        float t = mob.actionTime;
        if (mob.titan) titan(mob, age, t);
        else companion(mob, swing, amount, age, yaw, pitch, t);
    }

    private void companion(ElementalState mob, float swing, float amount, float age, float yaw, float pitch, float t) {
        float walk = Mth.cos(swing * .7f) * amount, bob = Mth.sin(age * .1f);
        float attack = mob.action != 0 ? ease(t / 6f) : mob.previousAction != 0 ? 1 - ease(t / 6f) : 0;
        ModelPart head = part("head");
        head.yRot += yaw * Mth.DEG_TO_RAD * .6f; head.xRot += pitch * Mth.DEG_TO_RAD * .5f;
        mirror("leg_left", walk * .8f, 0, 0); part("leg_right").xRot -= 2 * walk * .8f;
        switch (id) {
            case "fire_elemental" -> {
                part("body").y -= bob * 1.2f;
                ModelPart crest = part("crest");
                crest.yScale = 1 + Mth.sin(age * .6f) * .12f; crest.xScale = crest.zScale = 1 - Mth.sin(age * .6f) * .06f;
                crest.xRot += Mth.sin(age * .23f) * .1f;
                mirror("arm_left", Mth.sin(age * .15f) * .15f, 0, .15f + Mth.sin(age * .2f) * .1f);
            }
            case "water_elemental" -> {
                ModelPart base = part("base");
                base.xScale = base.zScale = 1 + Mth.sin(age * .12f) * .06f;
                part("body").zRot += Mth.sin(age * .09f) * .06f; part("body").y += Mth.sin(age * .12f) * .4f;
                mirror("arm_left", -walk * .5f + Mth.sin(age * .1f) * .12f, 0, .1f);
            }
            case "cold_elemental", "life_elemental" -> mirror("arm_left", -walk * .6f + Mth.sin(age * .08f) * .05f, 0, .08f);
            case "earth_elemental" -> {
                part("body").xRot += .1f + Math.abs(walk) * .05f;
                mirror("arm_left", -walk * .5f, 0, .05f);
            }
            case "lightning_elemental" -> {
                part("body").y -= bob * 1.5f;
                part("ring").yRot += age * .35f;
                part("tail").zRot += Mth.sin(age * .5f) * .25f; part("tail").xRot += Mth.sin(age * .37f) * .15f;
                mirror("arm_left", 0, 0, .2f + Mth.sin(age * .7f) * .15f);
            }
            case "arcane_elemental" -> {
                part("body").y -= bob * 1.5f;
                part("body").yRot += age * .03f;
                part("ring").yRot += age * .12f;
                mirror("shard_left", age * .2f, 0, 0);
            }
            case "death_elemental" -> {
                part("body").y -= bob * 1.3f; part("body").zRot += Mth.sin(age * .07f) * .06f;
                mirror("arm_left", -.3f + Mth.sin(age * .11f) * .12f, 0, .1f);
            }
            default -> {}
        }
        if (attack > 0) {
            boolean earth = id.equals("earth_elemental");
            if (earth) {
                boolean struck = mob.action == 0;
                float lift = struck ? attack * .4f : attack;
                mirror("arm_left", -2.6f * lift, 0, .15f * lift);
                part("body").xRot += (struck ? .35f : -.15f) * attack;
            } else if (id.equals("lightning_elemental") || id.equals("arcane_elemental")) {
                part("body").xRot -= .2f * attack;
                mirror("arm_left", -1.4f * attack, 0, -.2f * attack);
                part("ring").yRot += age * .5f * attack;
            } else {
                part("arm_left").xRot -= 1.6f * attack;
                part("arm_right").xRot -= .5f * attack;
                part("body").yRot -= .15f * attack;
            }
        }
    }

    private void titan(ElementalState mob, float age, float t) {
        ModelPart body = part("body"), head = part("head");
        float breath = Mth.sin(age * .06f);
        body.y += breath * .5f; body.xRot += .04f + breath * .015f;
        head.xRot += Mth.sin(age * .045f) * .04f; head.yRot += Mth.sin(age * .025f) * .12f;
        mirror("arm_left", Mth.sin(age * .05f) * .03f, 0, Mth.sin(age * .06f) * .02f);
        switch (id) {
            case "fire_titan", "cold_titan", "life_titan" -> { ModelPart crown = part("crown"); crown.yScale = 1 + Mth.sin(age * .4f) * (id.equals("fire_titan") ? .1f : .02f); }
            case "water_titan" -> part("crest").xRot += Mth.sin(age * .1f) * .08f;
            case "lightning_titan" -> part("halo").yRot += age * .15f;
            case "arcane_titan" -> part("halo").zRot += age * .05f;
            default -> {}
        }
        int action = mob.action, previous = mob.previousAction;
        if (action == ElementalTitan.THROW) {
            float wind = ease(t / ElementalTitan.THROW_TICKS);
            part("arm_left").xRot -= 3.3f * wind; part("arm_left").zRot += .2f * wind;
            part("forearm_left").xRot -= .9f * wind;
            body.yRot += .25f * wind; body.xRot -= .12f * wind;
        } else if (action == ElementalTitan.SLAM) {
            float wind = ease(t / ElementalTitan.SLAM_TICKS), shake = t > ElementalTitan.SLAM_TICKS * .7f ? Mth.sin(age * 2.2f) * .03f : 0;
            mirror("arm_left", -3f * wind + shake, 0, -.25f * wind);
            mirror("forearm_left", -.7f * wind, 0, 0);
            body.xRot -= .22f * wind; head.xRot -= .2f * wind;
        } else if (action == 0 && t < 10) {
            float back = 1 - ease(t / 10f);
            if (previous == ElementalTitan.THROW) {
                part("arm_left").xRot -= 1.5f * back; part("forearm_left").xRot -= .2f * back;
                body.yRot -= .2f * back; body.xRot += .12f * back;
            } else if (previous == ElementalTitan.SLAM) {
                body.xRot += .3f * back; head.xRot += .15f * back;
                mirror("arm_left", -.35f * back, 0, .1f * back);
                body.y += back * 2;
            }
        }
    }
}
