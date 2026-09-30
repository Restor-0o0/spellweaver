package com.restor.magicka.client.enemy;

import com.restor.magicka.enemy.*;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import java.util.HashMap;
import java.util.Map;

public class EnemyModel<T extends SpecialEnemy> extends HierarchicalModel<T> {
    private static final ModelPart NONE = new ModelPart(java.util.List.of(), Map.of());
    private final ModelPart root;
    private final String id;
    private final Map<String, ModelPart> parts = new HashMap<>();

    public EnemyModel(ModelPart root, String id) { this.root = root; this.id = id; }
    @Override public ModelPart root() { return root; }
    private ModelPart part(String name) { return parts.computeIfAbsent(name, n -> getAnyDescendantWithName(n).orElse(NONE)); }
    private void mirror(String left, float x, float y, float z) {
        ModelPart l = part(left), r = part(left.replace("left", "right"));
        l.xRot += x; l.yRot += y; l.zRot += z;
        r.xRot += x; r.yRot -= y; r.zRot -= z;
    }
    private void look(String name, float yaw, float pitch) { ModelPart p = part(name); p.yRot += yaw * Mth.DEG_TO_RAD; p.xRot += pitch * Mth.DEG_TO_RAD; }

    @Override public void setupAnim(T mob, float swing, float amount, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float walk = Mth.cos(swing * .9f), step = Mth.sin(swing * .9f), cast = mob.action() != 0 ? mob.actionTime(0) : 0;
        switch (id) {
            case "cave_skitter" -> {
                look("head", yaw * .5f, pitch * .5f);
                for (String leg : new String[]{"leg_left_front", "leg_left_mid", "leg_left_back"}) {
                    float phase = leg.endsWith("mid") ? Mth.PI : 0;
                    ModelPart l = part(leg), r = part(leg.replace("left", "right"));
                    l.yRot += Mth.cos(swing * 1.6f + phase) * .45f * amount; r.yRot -= Mth.cos(swing * 1.6f + phase + Mth.PI) * .45f * amount;
                    l.zRot -= Math.abs(Mth.sin(swing * 1.6f + phase)) * .35f * amount; r.zRot += Math.abs(Mth.sin(swing * 1.6f + phase + Mth.PI)) * .35f * amount;
                }
                mirror("mandible_left", 0, Mth.sin(age * .6f) * .2f, 0);
                mirror("antenna_left", Mth.sin(age * .2f) * .15f, Mth.sin(age * .13f) * .15f, 0);
            }
            case "shield_beetle" -> {
                look("head", yaw * .3f, 0);
                mirror("mandible_left", 0, Mth.sin(age * .3f) * .12f, 0);
                for (String leg : new String[]{"leg_left_front", "leg_left_mid", "leg_left_back"}) {
                    float phase = leg.endsWith("mid") ? Mth.PI : 0;
                    ModelPart l = part(leg), r = part(leg.replace("left", "right"));
                    l.yRot += Mth.cos(swing + phase) * .35f * amount; r.yRot -= Mth.cos(swing + phase + Mth.PI) * .35f * amount;
                }
                if (mob.action() == ShieldBeetle.CHARGE) { part("shield").xRot -= .15f; part("body").xRot += .08f; part("shield").z -= Math.min(2, cast * .2f); }
                if (mob.action() == ShieldBeetle.RECOVER) { part("shield").xRot += .55f; part("shield").y += 2; part("body").xRot -= .1f; }
            }
            case "stone_crawler" -> {
                look("head", yaw * .4f, pitch * .4f);
                mirror("leg_left", walk * .8f * amount, 0, 0);
                part("leg_right").xRot -= 2 * walk * .8f * amount;
                part("arm_left").xRot -= walk * .5f * amount; part("arm_right").xRot += walk * .5f * amount;
                if (mob.action() == StoneCrawler.SLAM) {
                    float raise = Math.min(1, cast / 16f);
                    mirror("arm_left", -2.6f * raise, 0, .2f * raise);
                    part("body").xRot -= .15f * raise;
                }
                part("armor").visible = mob.state() == 0;
            }
            case "mushroom_nurse" -> {
                mirror("foot_left", walk * .6f * amount, 0, 0);
                part("foot_right").xRot -= 2 * walk * .6f * amount;
                part("cap").zRot += Mth.sin(age * .08f) * .05f + step * .06f * amount;
                for (int i = 0; i < 3; i++) { ModelPart sac = part("sac_" + i); float s = 1 + Mth.sin(age * .15f + i * 2) * .12f; sac.xScale = sac.zScale = s; sac.yScale = 2 - s; }
                if (mob.action() == MushroomNurse.HEAL) { mirror("arm_left", -2.3f, 0, -.3f); part("cap").y -= Mth.sin(cast * .6f) * .5f; }
                else mirror("arm_left", walk * .4f * amount, 0, 0);
            }
            case "element_binder" -> {
                goblin(mob, swing, amount, age, yaw, pitch);
                part("chain").xRot += Mth.sin(age * .1f) * .12f;
                if (mob.action() == ElementBinder.BIND) {
                    float lift = Math.min(1, cast / 8f);
                    mirror("arm_left", -1.6f * lift - Mth.sin(cast * .5f) * .1f, 0, .3f * lift);
                    part("chain").xRot += Mth.sin(age * .6f) * .4f;
                }
            }
            case "burrower" -> {
                for (int i = 0; i < 3; i++) part("seg_" + i).yRot += Mth.sin(swing * .8f - i * .9f) * .25f * amount;
                part("tail").yRot += Mth.sin(age * .2f) * .3f;
                boolean digging = mob.action() == Burrower.DIG;
                part("head").xRot += Mth.sin(age * (digging ? 1.4f : .25f)) * (digging ? .25f : .08f);
                part("head").yRot += Mth.sin(age * .11f) * .1f;
                float dig = digging ? Mth.sin(age * 1.6f) * .8f : walk * .6f * amount;
                mirror("claw_left_front", dig, 0, 0); mirror("claw_left_back", -dig, 0, 0);
            }
            case "cave_wisp" -> {
                part("body").y -= Mth.sin(age * .12f) * 1.5f;
                float flap = mob.action() == HoverEnemy.CHARGE ? 2f : 1.2f;
                mirror("wing_left", 0, Mth.sin(age * flap) * .12f, Mth.sin(age * flap) * .6f);
                mirror("antenna_left", Mth.sin(age * .15f) * .15f, 0, 0);
                mirror("leg_left", .25f, 0, 0);
            }
            case "stone_eater" -> {
                look("head", yaw * .4f, pitch * .3f);
                mirror("leg_left_front", walk * .7f * amount, 0, 0); part("leg_right_front").xRot -= 2 * walk * .7f * amount;
                mirror("leg_left_back", -walk * .7f * amount, 0, 0); part("leg_right_back").xRot += 2 * walk * .7f * amount;
                part("jaw").xRot += mob.action() == StoneEater.EAT ? .15f + Math.abs(Mth.sin(age * .45f)) * .55f : Mth.sin(age * .05f) * .05f;
            }
            case "flood_caster" -> {
                look("head", yaw * .6f, pitch * .4f);
                part("body").y -= Math.abs(step) * 1.5f * amount;
                mirror("leg_left", walk * .5f * amount, 0, 0); part("leg_right").xRot -= 2 * walk * .5f * amount;
                float fill = mob.action() == FloodCaster.FLOOD ? Math.min(1, cast / 20f) * .45f : Mth.sin(age * .1f) * .06f;
                ModelPart sac = part("sac"); sac.xScale = sac.zScale = 1 + fill; sac.yScale = 1 + fill * 1.4f;
                if (mob.action() != 0) { part("body").xRot -= .18f; mirror("arm_left", -.5f, 0, .15f); }
                else mirror("arm_left", -walk * .4f * amount, 0, 0);
            }
            case "countermage" -> {
                goblin(mob, swing, amount, age, yaw, pitch);
                if (mob.action() == Countermage.WARD_CAST) mirror("arm_left", -2.6f, 0, .35f);
                if (mob.state() == 1) part("arm_right").xRot -= .5f + Mth.sin(age * .2f) * .1f;
            }
            case "spark_imp" -> {
                look("head", yaw * .6f, pitch * .5f);
                float flap = mob.action() == HoverEnemy.CHARGE ? 2f : 1.4f;
                mirror("wing_left", 0, Mth.sin(age * .3f) * .15f, Mth.sin(age * flap) * .8f);
                part("body").y -= Mth.sin(age * flap) * .6f;
                part("tail").yRot += Mth.sin(age * .25f) * .35f;
                mirror("leg_left", .3f + Mth.sin(age * .2f) * .15f, 0, 0);
                mirror("ear_left", 0, 0, Mth.sin(age * .17f) * .1f);
            }
            case "goblin_warrior" -> {
                goblin(mob, swing, amount, age, yaw, pitch);
                if (mob.getTarget() != null || attackTime > 0) mirror("arm_left", -.35f, 0, 0);
            }
            case "goblin_archer" -> {
                goblin(mob, swing, amount, age, yaw, pitch);
                if (mob.action() == GoblinArcher.AIM) {
                    float draw = Math.min(1, cast / 6f);
                    ModelPart l = part("arm_left"), r = part("arm_right");
                    l.xRot = Mth.lerp(draw, l.xRot, -Mth.HALF_PI + pitch * Mth.DEG_TO_RAD); l.yRot = .15f * draw;
                    r.xRot = Mth.lerp(draw, r.xRot, -Mth.HALF_PI + pitch * Mth.DEG_TO_RAD); r.yRot = -.6f * draw;
                    part("bow").xRot += Mth.HALF_PI * draw;
                }
            }
            case "goblin_mage", "goblin_frost_mage", "goblin_storm_mage" -> {
                goblin(mob, swing, amount, age, yaw, pitch);
                if (mob.action() == GoblinMage.CAST) {
                    float lift = Math.min(1, cast / 8f);
                    part("arm_right").xRot = Mth.lerp(lift, part("arm_right").xRot, -2.5f + Mth.sin(cast * .6f) * .12f);
                    part("arm_left").xRot -= .8f * lift;
                }
            }
            case "goblin_bomber" -> {
                goblin(mob, swing, amount, age, yaw, pitch);
                boolean winding = mob.action() == GoblinBomber.THROW;
                part("bomb").visible = winding;
                if (winding) {
                    float wind = Math.min(1, cast / 14f);
                    part("arm_right").xRot = Mth.lerp(wind, part("arm_right").xRot, -2.9f);
                    part("arm_left").xRot -= .6f * wind;
                    part("body").yRot -= .3f * wind;
                }
            }
            case "goblin_shaman" -> {
                goblin(mob, swing, amount, age, yaw, pitch);
                if (mob.action() == GoblinShaman.CHANT) {
                    float beat = Math.abs(Mth.sin(cast * .52f));
                    part("arm_right").xRot = -.9f - beat * .7f;
                    part("arm_left").xRot = -1.1f + beat * .5f;
                    part("body").y += beat * .6f;
                    part("head").xRot -= .35f;
                }
            }
            default -> {}
        }
    }
    private void goblin(T mob, float swing, float amount, float age, float yaw, float pitch) {
        look("head", yaw, pitch);
        float legs = Mth.cos(swing * .6662f) * 1.1f * amount, idle = .06f + Mth.sin(age * .07f) * .04f;
        part("leg_left").xRot += legs; part("leg_right").xRot -= legs;
        part("arm_left").xRot -= legs * .7f; part("arm_right").xRot += legs * .7f;
        part("arm_left").zRot += idle; part("arm_right").zRot -= idle;
        if (attackTime > 0) {
            float a = Mth.sin(Mth.sqrt(attackTime) * Mth.PI);
            part("arm_right").xRot -= a * 1.9f; part("arm_right").yRot -= a * .4f; part("body").yRot += a * .25f;
        }
    }
}
