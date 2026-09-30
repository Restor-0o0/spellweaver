"""Specialist enemy models: art/enemies/<id>.bbmodel is the source of truth.

Run: python tools/gen_enemy_models.py         -> EnemyLayers.java and textures from the .bbmodel files
     python tools/gen_enemy_models.py --seed  -> overwrite the .bbmodel files from the procedural MOBS spec below
Coordinates are absolute Blockbench pixels: 16 px = 1 block, y up from the feet, front faces -z (north).
Every model is validated: all cubes must form one connected piece and walkers must stand on y = 0.
"""
import base64
import json
import math
import os
import random
import re
import sys
import uuid
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/restor/magicka/client/enemy/EnemyLayers.java")
TEXTURES = os.path.join(ROOT, "src/main/resources/assets/magicka/textures/entity")
BBMODELS = os.path.join(ROOT, "art/enemies")
FLYERS = {"cave_wisp", "spark_imp"}


class Cube:
    def __init__(self, origin, size, color=0, pattern=None, uv=None, inflate=0, mirror=False):
        self.origin, self.size, self.color, self.pattern = origin, size, color, pattern
        self.uv, self.inflate, self.mirror = uv, inflate, mirror


class Bone:
    def __init__(self, name, pivot, cubes=(), parent=None, rot=(0, 0, 0)):
        self.name, self.pivot, self.cubes, self.parent, self.rot = name, pivot, list(cubes), parent, rot


def C(origin, size, color, pattern=None):
    return Cube(origin, size, color, pattern)


def mirrored(bone):
    name = bone.name.replace("left", "right")
    parent = bone.parent.replace("left", "right") if bone.parent else None
    px, py, pz = bone.pivot
    cubes = [Cube((-(c.origin[0] + c.size[0]), c.origin[1], c.origin[2]), c.size, c.color, c.pattern) for c in bone.cubes]
    return Bone(name, (-px, py, pz), cubes, parent, (bone.rot[0], -bone.rot[1], -bone.rot[2]))


def sym(*bones):
    out = []
    for b in bones:
        out.append(b)
        if "left" in b.name:
            out.append(mirrored(b))
    return out


# ---------------------------------------------------------------- palettes
BONE_WHITE, BONE_DARK, BLACK, IRON, IRON_DARK = 0xDDD3BC, 0xA89A80, 0x17130F, 0x7E8288, 0x55595E
LEATHER, LEATHER_DARK, WOOD, WOOD_DARK, STRING = 0x6B4A2E, 0x4A3220, 0x7A5A38, 0x553E27, 0xCFC6B0
CHITIN, CHITIN_DARK, CHITIN_PALE, EYE_PALE = 0x4A4038, 0x2E2823, 0x8E8270, 0xB9B09A
BEETLE, BEETLE_SHELL, BEETLE_BELLY, BEETLE_SOFT, HORN = 0x3A3A2E, 0x4A5238, 0x2A2820, 0x7A6450, 0x8C6E44
STONE, STONE_DARK, HIDE, HIDE_LIGHT, MOSS = 0x7D7F83, 0x55575B, 0x6A5A50, 0x8A7666, 0x4F6A32
STEM, STEM_DARK, GILL, CAP, CAP_DARK, WART, SPORE = 0xE6D9C0, 0xB8A98D, 0xC4AE88, 0x8A3F2A, 0x6E3020, 0xE8DCC4, 0xCFC49A
FUR, FUR_DARK, PINK, PINK_DARK, CLAW = 0x3A3438, 0x2A2528, 0xD89A8E, 0xB87A70, 0xE8E0CC
MOTH, MOTH_DARK, MOTH_WING, MOTH_WING_DARK = 0x9A8A70, 0x6E6250, 0xA89A7E, 0x7E7058
GRAVEL, GRAVEL_DARK, GRAVEL_BELLY, AMETHYST, AMBER, MOUTH = 0x6E6A64, 0x4E4A45, 0x8A847A, 0x7E6A9A, 0xB8862E, 0x4A2A2A
FROG, FROG_DARK, FROG_BELLY, THROAT, IRIS = 0x5A6E3A, 0x445430, 0xC8C090, 0xD8CCA0, 0xC8A840
BAT, BAT_DARK, BAT_LIGHT, WING = 0x4A4048, 0x302A30, 0x6E6068, 0x5A4A50
GOB_SKIN, GOB_SKIN_DARK, GOB_OLD, GOB_OLD_DARK, GOB_EYE = 0x6E8B3D, 0x56702F, 0x6A7A48, 0x525E38, 0xD6B84A
GOB_GREEN, GOB_GREEN_DARK, PANTS, BOOT = 0x4F6B3A, 0x3C5230, 0x5E4A36, 0x3A2C20
RED_ROBE, RED_ROBE_DARK, HAT, EMBER = 0x7A2E2A, 0x5A2020, 0x4A3550, 0xC0502A
GREY_HIDE, GREY_HIDE_DARK, CLOAK, FEATHER_RED = 0x6A6258, 0x4E4840, 0x5E4632, 0x9A3A2A
PURPLE, PURPLE_DARK = 0x4E3A5E, 0x3A2A48
FROST_ROBE, FROST_ROBE_DARK, FUR_WHITE, ICE, ICE_LIGHT = 0x6E8FA8, 0x4E6A80, 0xE2E0D6, 0x9CC8DC, 0xD4ECF4
STORM_ROBE, STORM_ROBE_DARK, COPPER, VERDIGRIS = 0x3A4868, 0x2A3450, 0xB8703A, 0x5A9A80
BOMB, BRASS, APRON = 0x2E2A28, 0xB08A3A, 0x7A6248
SHAMAN_HIDE, SHAMAN_HIDE_DARK, WARPAINT = 0x7A5A3A, 0x5A4028, 0x9A2A22

MOBS = {}


def skitter_leg(name, x0, z, rot=(0, 0, 0)):
    return Bone(name, (x0, 5.5, z), [C((x0 - 3, 5, z - 0.5), (3, 1, 1), CHITIN_DARK), C((x0 - 5, 5, z - 0.5), (2, 2, 1), CHITIN),
                                     C((x0 - 6, 0, z - 0.5), (1, 7, 1), CHITIN_DARK), C((x0 - 7, 0, z - 0.5), (1, 1, 1), CLAW)], "body", rot)


MOBS["cave_skitter"] = sym(
    Bone("body", (0, 4, 0), [C((-3, 3, -5), (6, 4, 6), CHITIN), C((-4, 3, 1), (8, 5, 7), CHITIN, "plates"),
                             C((-2, 8, 2), (4, 1, 5), CHITIN_PALE, "spots"), C((-3, 5, 8), (6, 2, 1), CHITIN_DARK),
                             C((-2, 2, -4), (4, 1, 4), CHITIN_DARK)]),
    Bone("head", (0, 5, -5), [C((-2.5, 3, -8), (5, 3, 3), CHITIN_DARK), C((-2, 5, -8.5), (1, 1, 1), EYE_PALE),
                              C((1, 5, -8.5), (1, 1, 1), EYE_PALE), C((-1.5, 6, -7), (3, 1, 2), CHITIN)], "body"),
    Bone("antenna_left", (-1, 6, -7), [C((-2, 6, -9), (1, 1, 2), CHITIN_DARK), C((-2.5, 7, -11), (1, 1, 3), CHITIN_PALE)], "head"),
    Bone("mandible_left", (-1.5, 4, -8), [C((-2, 3, -10), (1, 1, 2), CLAW)], "head"),
    skitter_leg("leg_left_front", -3, -4, (0, 0.35, 0)),
    skitter_leg("leg_left_mid", -3, -1.5),
    skitter_leg("leg_left_back", -4, 2.5, (0, -0.35, 0)),
)


def beetle_leg(name, z, rot=(0, 0, 0)):
    return Bone(name, (-6, 7, z), [C((-10, 6, z - 1), (4, 2, 2), BEETLE), C((-12, 0, z - 1), (2, 7, 2), BEETLE_BELLY),
                                   C((-13, 4, z - 0.5), (1, 1, 1), HORN), C((-13, 0, z - 1), (3, 1, 2), CHITIN_DARK)], "body", rot)


MOBS["shield_beetle"] = sym(
    Bone("body", (0, 7, 0), [C((-6, 4, -7), (12, 7, 14), BEETLE, "plates"), C((-6, 11, -6), (6, 2, 12), BEETLE_SHELL, "stripes"),
                             C((0, 11, -6), (6, 2, 12), BEETLE_SHELL, "stripes"), C((-5, 3, -7), (10, 1, 14), BEETLE_BELLY),
                             C((-4, 5, 7), (8, 5, 3), BEETLE_SOFT, "plates")]),
    Bone("shield", (0, 9, -8), [C((-8, 4, -11), (16, 12, 4), BEETLE_SHELL, "plates"), C((-9, 5, -10), (1, 9, 3), BEETLE),
                                C((8, 5, -10), (1, 9, 3), BEETLE), C((-6, 16, -10), (12, 1, 3), BEETLE),
                                C((-1, 12, -12), (2, 3, 1), HORN), C((-0.5, 15, -12), (1, 2, 1), HORN)], "body"),
    Bone("head", (0, 3, -7), [C((-3, 1, -11), (6, 3, 4), BEETLE_BELLY), C((-3.5, 2, -10), (1, 1, 1), BLACK),
                              C((2.5, 2, -10), (1, 1, 1), BLACK)], "body"),
    Bone("mandible_left", (-2, 1.5, -11), [C((-3, 1, -13), (1, 1, 2), HORN), C((-2, 1, -14), (1, 1, 1), HORN)], "head"),
    beetle_leg("leg_left_front", -4, (0, 0.4, 0)),
    beetle_leg("leg_left_mid", 0),
    beetle_leg("leg_left_back", 4, (0, -0.4, 0)),
)

MOBS["stone_crawler"] = sym(
    Bone("body", (0, 12, 0), [C((-7, 8, -8), (14, 10, 15), HIDE, "spots")]),
    Bone("armor", (0, 12, 0), [C((-8, 17, -7), (16, 5, 14), STONE, "cracks"), C((-6, 22, -4), (12, 2, 9), STONE, "cracks"),
                               C((-6, 8, -10), (12, 9, 2), STONE, "cracks"), C((-9, 9, -6), (2, 8, 12), STONE_DARK),
                               C((7, 9, -6), (2, 8, 12), STONE_DARK), C((-5, 24, -3), (6, 1, 5), MOSS, "spots"),
                               C((2, 22, 5), (4, 1, 2), MOSS), C((-8, 22, -7), (3, 1, 3), MOSS)], "body"),
    Bone("head", (0, 14, -10), [C((-3, 11, -14), (6, 6, 4), HIDE), C((-3.5, 16, -14.5), (7, 2, 2), STONE),
                                C((-2, 11, -16), (4, 3, 2), HIDE_LIGHT), C((-2.5, 15, -14.5), (1, 1, 1), BLACK),
                                C((1.5, 15, -14.5), (1, 1, 1), BLACK), C((-1.5, 10, -15.5), (1, 1, 1), BONE_WHITE),
                                C((0.5, 10, -15.5), (1, 1, 1), BONE_WHITE)], "body"),
    Bone("arm_left", (-8, 19, -5), [C((-12, 4, -8), (5, 15, 5), STONE_DARK, "cracks"), C((-12.5, 0, -8.5), (6, 4, 6), STONE),
                                    C((-13, 15, -9), (7, 4, 7), STONE, "cracks"), C((-12, 19, -8), (4, 1, 4), MOSS)], "body"),
    Bone("leg_left", (-5, 8, 5), [C((-8, 1, 3), (5, 7, 6), STONE_DARK), C((-8.5, 0, 2), (6, 2, 7), STONE),
                                  C((-7.5, 4, 2), (4, 3, 1), STONE, "cracks")], "body"),
)

MOBS["mushroom_nurse"] = sym(
    Bone("body", (0, 3, 0), [C((-2.5, 3, -2.5), (5, 11, 5), STEM, "stripes"), C((-3, 3, -3), (6, 3, 6), STEM_DARK),
                             C((-3.5, 12, -3.5), (7, 2, 7), STEM_DARK), C((-2, 14, -2), (4, 3, 4), STEM),
                             C((-1.5, 15, -2.5), (1, 1, 1), BLACK), C((0.5, 15, -2.5), (1, 1, 1), BLACK)]),
    Bone("foot_left", (-1.5, 3, 0), [C((-3, 0, -2), (3, 3, 4), STEM_DARK), C((-3, 0, -3), (3, 1, 1), STEM)], "body"),
    Bone("cap", (0, 17, 0), [C((-7, 17, -7), (14, 1, 14), GILL, "stripes"), C((-8, 18, -8), (16, 4, 16), CAP, "spots"),
                             C((-6, 22, -6), (12, 3, 12), CAP, "spots"), C((-3, 25, -3), (6, 2, 6), CAP_DARK),
                             C((-5, 25, -5), (2, 1, 2), WART), C((3, 25, 1), (2, 1, 2), WART), C((-1, 27, -1), (2, 1, 2), WART),
                             C((5, 22, -5), (2, 1, 2), WART), C((-7, 22, 3), (2, 1, 2), WART)], "body"),
    Bone("arm_left", (-2.5, 11, 0), [C((-4.5, 5, -1), (2, 7, 2), STEM_DARK), C((-5, 3, -1.5), (3, 2, 3), STEM)], "body"),
    Bone("sac_0", (-6, 18, -4), [C((-7, 14, -5), (2, 4, 2), SPORE, "spots")], "cap"),
    Bone("sac_1", (6, 18, 2), [C((5, 13, 1), (2, 5, 2), SPORE, "spots")], "cap"),
    Bone("sac_2", (-1, 18, 6), [C((-2, 15, 5), (2, 3, 2), SPORE, "spots")], "cap"),
) + [Bone("bundle", (4, 4, 0), [C((2, 1, -2.5), (4, 3, 3), MOSS, "fur"), C((3, 4, -2), (2, 1, 2), STRING)], "arm_right")]

MOBS["burrower"] = sym(
    Bone("seg_0", (0, 4, -4), [C((-4, 0, -8), (8, 7, 8), FUR, "fur"), C((-3, 7, -7), (6, 1, 6), FUR_DARK, "fur"),
                               C((-3, 5, -8.5), (1, 1, 1), BLACK), C((2, 5, -8.5), (1, 1, 1), BLACK)]),
    Bone("head", (0, 3, -8), [C((-2, 1, -11), (4, 4, 3), FUR, "fur"), C((-1.5, 2, -13), (3, 3, 2), PINK),
                              C((-1, 3, -13.5), (2, 1, 1), PINK_DARK)], "seg_0"),
    Bone("claw_left_front", (-4, 3, -6), [C((-6, 1, -8), (2, 3, 3), FUR, "fur"), C((-8, 0, -9), (4, 1, 4), PINK),
                                          C((-8, 0, -11), (1, 1, 2), CLAW), C((-6.5, 0, -11), (1, 1, 2), CLAW),
                                          C((-5, 0, -11), (1, 1, 2), CLAW)], "seg_0"),
    Bone("seg_1", (0, 4, 0), [C((-4, 0, 0), (8, 6, 6), FUR, "fur"), C((-3, 6, 1), (6, 1, 4), FUR_DARK)], "seg_0"),
    Bone("claw_left_back", (-4, 2, 3), [C((-6, 0, 2), (2, 2, 3), FUR_DARK, "fur"), C((-6, 0, 1), (2, 1, 1), PINK)], "seg_1"),
    Bone("seg_2", (0, 4, 6), [C((-3.5, 0, 6), (7, 5, 5), FUR, "fur"), C((-2.5, 5, 7), (5, 1, 3), FUR_DARK)], "seg_1"),
    Bone("tail", (0, 2.5, 11), [C((-0.5, 2, 11), (1, 1, 4), PINK)], "seg_2"),
)

MOBS["cave_wisp"] = sym(
    Bone("body", (0, 8, 0), [C((-2, 6, -3), (4, 4, 4), MOTH, "fur"), C((-1.5, 5, 1), (3, 3, 6), MOTH_DARK, "stripes"),
                             C((-2.5, 9, -3.5), (5, 2, 3), MOTH, "fur")]),
    Bone("head", (0, 8, -3), [C((-1.5, 6, -5), (3, 3, 2), MOTH_DARK, "fur"), C((-2, 7, -5), (1, 1, 1), BLACK),
                              C((1, 7, -5), (1, 1, 1), BLACK)], "body"),
    Bone("antenna_left", (-1, 9, -4.5), [C((-3, 9, -8), (2, 1, 4), MOTH_WING_DARK, "stripes")], "head"),
    Bone("wing_left", (-2, 9.5, -2), [C((-14, 9, -5), (12, 1, 8), MOTH_WING, "spots"), C((-11, 9, 2), (9, 1, 5), MOTH_WING_DARK, "stripes"),
                                      C((-10, 9.5, -4), (3, 1, 3), MOTH_WING_DARK)], "body"),
    Bone("leg_left", (-2, 6.5, -1), [C((-3, 3, -2.5), (1, 4, 1), MOTH_DARK), C((-3, 3, -0.5), (1, 4, 1), MOTH_DARK)], "body"),
)

MOBS["stone_eater"] = sym(
    Bone("body", (0, 9, 3), [C((-6, 5, -4), (12, 9, 15), GRAVEL, "plates"), C((-5, 14, -2), (10, 2, 10), GRAVEL_DARK, "cracks"),
                             C((-5, 4, -3), (10, 1, 12), GRAVEL_BELLY), C((-2, 16, 0), (2, 3, 2), AMETHYST),
                             C((1, 16, 3), (2, 2, 2), AMETHYST), C((-3, 16, 5), (2, 2, 2), AMETHYST)]),
    Bone("tail", (0, 9.5, 11), [C((-3, 7, 11), (6, 5, 6), GRAVEL, "plates"), C((-1.5, 8, 17), (3, 3, 5), GRAVEL_DARK)], "body"),
    Bone("head", (0, 11, -4), [C((-6, 11, -13), (12, 5, 9), GRAVEL, "cracks"), C((-6.5, 15, -12), (13, 2, 6), GRAVEL_DARK),
                               C((-6.5, 13, -11), (1, 1, 1), AMBER), C((5.5, 13, -11), (1, 1, 1), AMBER),
                               *[C((x, 10, -13), (1, 1, 1), BONE_WHITE) for x in (-5, -2, 1, 4)]], "body"),
    Bone("jaw", (0, 10, -5), [C((-6.5, 6, -14), (13, 4, 10), GRAVEL, "cracks"), C((-5.5, 10, -13), (11, 1, 8), MOUTH),
                              *[C((x, 10, -14), (1, 1, 1), BONE_WHITE) for x in (-5.5, -1.5, 2.5)]], "head"),
    Bone("leg_left_front", (-5, 7, -2), [C((-8, 1, -4), (4, 6, 4), GRAVEL_DARK, "cracks"), C((-8.5, 0, -4.5), (5, 1, 5), GRAVEL),
                                         C((-8.5, 0, -5.5), (1, 1, 1), BONE_WHITE), C((-6.5, 0, -5.5), (1, 1, 1), BONE_WHITE),
                                         C((-4.5, 0, -5.5), (1, 1, 1), BONE_WHITE)], "body"),
    Bone("leg_left_back", (-5, 7, 9), [C((-8, 1, 7), (4, 6, 4), GRAVEL_DARK, "cracks"), C((-8.5, 0, 6.5), (5, 1, 5), GRAVEL),
                                       C((-8.5, 0, 5.5), (1, 1, 1), BONE_WHITE), C((-6.5, 0, 5.5), (1, 1, 1), BONE_WHITE)], "body"),
)

MOBS["flood_caster"] = sym(
    Bone("body", (0, 7, 0), [C((-5, 3, -3), (10, 7, 10), FROG, "spots"), C((-4, 10, -2), (8, 2, 8), FROG_DARK, "spots"),
                             C((-4, 3, -4), (8, 5, 1), FROG_BELLY)]),
    Bone("head", (0, 8, -3), [C((-5, 6, -9), (10, 5, 6), FROG, "spots"), C((-4.5, 7, -9.5), (9, 1, 1), FROG_DARK),
                              C((-5, 11, -8), (3, 2, 3), FROG_DARK), C((2, 11, -8), (3, 2, 3), FROG_DARK),
                              C((-4.5, 12, -8.5), (2, 1, 1), IRIS, "eye"), C((2.5, 12, -8.5), (2, 1, 1), IRIS, "eye")], "body"),
    Bone("sac", (0, 6, -7), [C((-3, 4, -9), (6, 2, 4), THROAT)], "head"),
    Bone("arm_left", (-4, 7, -5), [C((-6, 2, -6), (2, 5, 2), FROG), C((-7, 0, -7), (4, 2, 3), FROG_DARK)], "body"),
    Bone("leg_left", (-4, 5, 3), [C((-8, 1, 0), (4, 6, 7), FROG, "spots"), C((-9, 0, -3), (4, 1, 5), FROG_DARK)], "body"),
)

MOBS["spark_imp"] = sym(
    Bone("body", (0, 8, 0), [C((-2, 6, -2), (4, 5, 4), BAT, "fur"), C((-1.5, 6, -2.5), (3, 4, 1), BAT_LIGHT, "fur")]),
    Bone("head", (0, 11, -1), [C((-2, 11, -3), (4, 3, 4), BAT, "fur"), C((-1, 11, -4), (2, 2, 1), PINK_DARK),
                               C((-1.5, 13, -3.5), (1, 1, 1), BLACK), C((0.5, 13, -3.5), (1, 1, 1), BLACK),
                               C((-1, 10, -3.5), (1, 1, 1), BONE_WHITE), C((0, 10, -3.5), (1, 1, 1), BONE_WHITE)], "body"),
    Bone("ear_left", (-1.5, 14, -1.5), [C((-2, 14, -2), (1, 3, 1), BAT_DARK)], "head"),
    Bone("wing_left", (-2, 10, 0), [C((-12, 10, -1.5), (10, 1, 1), BAT_DARK), C((-11, 9, -1), (9, 1, 6), WING, "stripes"),
                                    C((-13, 9, -1.5), (2, 1, 3), BAT_DARK)], "body"),
    Bone("leg_left", (-1, 6.5, 1), [C((-1.5, 3, 0.5), (1, 4, 1), BAT_DARK)], "body"),
    Bone("tail", (0, 7, 2), [C((-0.5, 6, 2), (1, 1, 4), BAT_DARK)], "body"),
)


def goblin(tunic, tunic_dark, pants, skin=GOB_SKIN, skin_dark=GOB_SKIN_DARK, body=(), head=(), arm=(), items=()):
    return sym(
        Bone("body", (0, 8, 0), [C((-3.5, 8, -2), (7, 7, 4), tunic, "cloth"), C((-4, 8, -2.5), (8, 1, 5), LEATHER),
                                 C((-4, 6, -2.5), (8, 2, 5), tunic_dark, "cloth"), C((-0.5, 8, -3), (1, 1, 1), IRON), *body]),
        Bone("leg_left", (-2, 7, 0), [C((-3.5, 2, -1.5), (3, 5, 3), pants, "cloth"), C((-3.5, 0, -2.5), (3, 2, 4), BOOT)], "body"),
        Bone("head", (0, 15, 0), [C((-3.5, 15, -3.5), (7, 6, 6), skin), C((-1, 16, -5.5), (2, 3, 2), skin_dark),
                                  C((-3, 18, -4), (2, 1, 1), GOB_EYE, "eye"), C((1, 18, -4), (2, 1, 1), GOB_EYE, "eye"),
                                  C((-3.5, 19, -4), (7, 1, 1), skin_dark), C((-2, 15, -4), (1, 1, 1), BONE_WHITE),
                                  C((1, 15, -4), (1, 1, 1), BONE_WHITE), C((-7.5, 17, -1), (4, 2, 1), skin),
                                  C((-9.5, 18, -1), (2, 1, 1), skin_dark), C((3.5, 17, -1), (4, 2, 1), skin),
                                  C((7.5, 18, -1), (2, 1, 1), skin_dark), *head], "body"),
        Bone("arm_left", (-4.5, 14, 0), [C((-6, 11, -1.5), (3, 4, 3), tunic, "cloth"), C((-5.5, 7, -1), (2, 4, 2), skin),
                                         C((-6, 5, -1.5), (3, 2, 3), skin_dark), *arm], "body"),
    ) + list(items)


HOOD = [C((-4, 20, -4), (8, 2, 7), None), C((-4, 15, 2.5), (8, 5, 1), None)]


def hood(color):
    return [C(c.origin, c.size, color, "cloth") for c in HOOD]


MOBS["goblin_warrior"] = goblin(
    LEATHER, LEATHER_DARK, PANTS,
    body=[C((-4, 10, -2.5), (8, 4, 1), LEATHER_DARK, "plates"), C((-2, 9, 2), (4, 5, 1), LEATHER_DARK)],
    head=[C((-4, 20, -4), (8, 2, 7), IRON, "plates"), C((-0.5, 22, -1), (1, 2, 1), IRON_DARK),
          C((-4, 17, -3), (1, 3, 5), IRON), C((3, 17, -3), (1, 3, 5), IRON)],
    arm=[C((-6.5, 14, -2), (4, 2, 4), IRON, "plates")],
    items=[Bone("shield", (-6.5, 6, 0), [C((-7, 3, -3.5), (1, 8, 7), WOOD, "wood"), C((-8, 6, -1), (1, 2, 2), IRON),
                                         C((-7.5, 10, -3.5), (1, 1, 7), IRON_DARK), C((-7.5, 3, -3.5), (1, 1, 7), IRON_DARK)], "arm_left"),
           Bone("weapon", (4.5, 6, 0), [C((4, 5, -4), (1, 1, 3), WOOD_DARK), C((4, 3, -9), (1, 4, 5), IRON, "plates")], "arm_right")],
)

MOBS["goblin_archer"] = goblin(
    GOB_GREEN, GOB_GREEN_DARK, PANTS,
    body=[C((-4.5, 13, -2.5), (9, 2, 5), GOB_GREEN_DARK, "cloth"), C((-3.5, 10, -2.5), (1, 5, 1), LEATHER_DARK)],
    head=hood(GOB_GREEN_DARK) + [C((-1, 22, -1), (2, 1, 3), GOB_GREEN_DARK)],
    items=[Bone("quiver", (1.5, 12, 3), [C((0, 7, 2), (3, 8, 2), LEATHER, "stripes"), C((0.5, 15, 2.5), (1, 3, 1), STRING),
                                         C((1.5, 15, 2.5), (1, 2, 1), STRING), C((0.5, 18, 2.5), (1, 1, 1), FEATHER_RED)], "body"),
           Bone("bow", (-4.5, 6, -1.5), [C((-5, 4, -2), (1, 5, 1), WOOD_DARK), C((-5, 9, -1.5), (1, 4, 1), WOOD, "wood"),
                                         C((-5, 0, -1.5), (1, 4, 1), WOOD, "wood"), C((-5, 12, -0.5), (1, 2, 1), WOOD_DARK),
                                         C((-5, 0, -0.5), (1, 2, 1), WOOD_DARK), C((-5, 1, 0.5), (1, 12, 0), STRING)], "arm_left")],
)

MOBS["goblin_mage"] = goblin(
    RED_ROBE, RED_ROBE_DARK, RED_ROBE_DARK,
    body=[C((-4, 1, -2.5), (8, 5, 5), RED_ROBE_DARK, "cloth"), C((-1, 9, -2.5), (2, 5, 1), HAT)],
    head=[C((-5, 20, -5), (10, 1, 9), HAT, "cloth"), C((-3.5, 21, -3.5), (7, 2, 6), HAT, "cloth"),
          C((-2.5, 23, -2.5), (5, 2, 4), HAT), C((-1.5, 25, -1.5), (3, 2, 3), HAT), C((-1, 27, 0), (2, 2, 2), HAT)],
    items=[Bone("staff", (4.5, 6, 0), [C((4, 0, -1), (1, 17, 1), WOOD, "wood"), C((3.5, 17, -1.5), (2, 2, 2), BONE_WHITE, "eye"),
                                       C((4, 19, -1), (1, 1, 1), EMBER)], "arm_right")],
)

MOBS["countermage"] = goblin(
    GREY_HIDE, GREY_HIDE_DARK, GREY_HIDE_DARK, GOB_OLD, GOB_OLD_DARK,
    body=[C((-4, 3, -2.5), (8, 3, 5), GREY_HIDE_DARK, "fur"), C((-4, 4, 2), (8, 11, 1), CLOAK, "fur"),
          C((-3, 13, -2.5), (6, 1, 1), BONE_WHITE), C((-0.5, 12, -2.5), (1, 1, 1), BONE_WHITE)],
    head=[C((-4, 19, -4.5), (8, 1, 8), LEATHER), C((-3, 20, 1), (1, 4, 1), FEATHER_RED), C((-1, 20, 1), (1, 5, 1), BONE_WHITE),
          C((1, 20, 1), (1, 4, 1), FEATHER_RED), C((-1.5, 13, -3.5), (3, 2, 1), GOB_OLD_DARK)],
    items=[Bone("staff", (4.5, 6, 0), [C((4, 0, -1), (1, 21, 1), WOOD, "wood"), C((3.5, 17, -1.5), (2, 3, 2), WOOD_DARK, "stripes"),
                                       C((5, 15, -0.5), (1, 2, 1), FEATHER_RED), C((3.5, 21, -1.5), (2, 1, 2), BONE_WHITE)], "arm_right")],
)

MOBS["element_binder"] = goblin(
    PURPLE, PURPLE_DARK, PURPLE_DARK,
    body=[C((-4, 1, -2.5), (8, 5, 5), PURPLE_DARK, "cloth"), C((-4.5, 9, -3), (9, 1, 6), IRON_DARK, "stripes")],
    head=hood(PURPLE_DARK) + [C((-1, 22, 0), (2, 2, 2), PURPLE_DARK)],
    items=[Bone("chain", (-4.5, 5, 0), [C((-5, 2, -0.5), (1, 3, 1), IRON, "stripes"), C((-5.5, 0, -1), (2, 2, 2), IRON_DARK)], "arm_left"),
           Bone("bag", (4.5, 5, 0), [C((3.5, 2, -1), (2, 3, 2), LEATHER, "stripes"), C((4, 5, -0.5), (1, 1, 1), STRING)], "arm_right")],
)

MOBS["goblin_frost_mage"] = goblin(
    FROST_ROBE, FROST_ROBE_DARK, FROST_ROBE_DARK,
    body=[C((-4, 1, -2.5), (8, 5, 5), FROST_ROBE_DARK, "cloth"), C((-4.5, 13, -3), (9, 2, 6), FUR_WHITE, "fur"),
          C((-1, 9, -2.5), (2, 4, 1), FUR_WHITE)],
    head=hood(FROST_ROBE_DARK) + [C((-4.5, 20, -4.5), (9, 1, 1), FUR_WHITE, "fur"), C((-1, 22, -1), (2, 1, 2), FUR_WHITE)],
    items=[Bone("staff", (4.5, 6, 0), [C((4, 0, -1), (1, 17, 1), WOOD, "wood"), C((3.5, 17, -1.5), (2, 3, 2), ICE, "cracks"),
                                       C((4, 20, -1), (1, 2, 1), ICE_LIGHT), C((2.5, 18, -1), (1, 1, 1), ICE)], "arm_right")],
)

MOBS["goblin_storm_mage"] = goblin(
    STORM_ROBE, STORM_ROBE_DARK, STORM_ROBE_DARK,
    body=[C((-4, 1, -2.5), (8, 5, 5), STORM_ROBE_DARK, "cloth"), C((-3.5, 12, -2.5), (7, 1, 1), COPPER)],
    head=[C((-5, 20, -5), (10, 1, 9), STORM_ROBE_DARK, "cloth"), C((-3.5, 21, -3.5), (7, 1, 6), COPPER, "spots"),
          C((-3.5, 22, -3.5), (7, 1, 6), STORM_ROBE, "cloth"), C((-2.5, 23, -2.5), (5, 2, 4), STORM_ROBE),
          C((-1.5, 25, -1), (3, 2, 3), STORM_ROBE_DARK), C((-1, 27, 1), (2, 1, 2), STORM_ROBE_DARK)],
    items=[Bone("staff", (4.5, 6, 0), [C((4, 0, -1), (1, 17, 1), WOOD_DARK, "wood"), C((3.5, 16, -1.5), (2, 1, 2), COPPER),
                                       C((4, 17, -1), (1, 3, 1), COPPER), C((3, 19, -1), (1, 3, 1), COPPER, "spots"),
                                       C((5, 19, -1), (1, 3, 1), COPPER, "spots"), C((4, 20, -1), (1, 1, 1), VERDIGRIS)], "arm_right")],
)

MOBS["goblin_bomber"] = goblin(
    APRON, LEATHER_DARK, PANTS,
    body=[C((-3, 3, -2.5), (6, 6, 1), APRON, "cloth"), *[C((x, 8, -3.5), (2, 2, 1), BOMB) for x in (-3, 1)],
          C((-2.5, 8, 2), (5, 6, 3), WOOD, "wood"), C((-3, 9, 1.5), (6, 1, 4), IRON_DARK), C((-3, 12, 1.5), (6, 1, 4), IRON_DARK),
          C((-0.5, 14, 3), (1, 1, 1), STRING)],
    head=[C((-4, 20, -4), (8, 1, 7), LEATHER_DARK), C((-3, 19.5, -5), (2, 2, 1), BRASS, "eye"), C((1, 19.5, -5), (2, 2, 1), BRASS, "eye"),
          C((-1, 20, -4.5), (2, 1, 1), BRASS)],
    items=[Bone("bomb", (4.5, 5, 0), [C((3, 2, -1.5), (3, 3, 3), BOMB, "spots"), C((4, 1.5, -0.5), (1, 1, 1), STRING)], "arm_right")],
)

MOBS["goblin_shaman"] = goblin(
    SHAMAN_HIDE, SHAMAN_HIDE_DARK, SHAMAN_HIDE_DARK,
    body=[C((-4, 3, -2.5), (8, 3, 5), SHAMAN_HIDE_DARK, "fur"), C((-4.5, 13, -3), (9, 2, 6), SHAMAN_HIDE_DARK, "fur"),
          *[C((x, 12, -3), (1, 1, 1), BONE_WHITE) for x in (-2.5, -0.5, 1.5)],
          C((-3, 8, 2), (4, 4, 2), LEATHER, "stripes"), C((-3, 12, 2), (4, 0.5, 2), BONE_WHITE)],
    head=[C((-4.5, 20, -1), (1, 3, 1), BONE_WHITE), C((-5.5, 22, -1), (1, 2, 1), BONE_WHITE),
          C((3.5, 20, -1), (1, 3, 1), BONE_WHITE), C((4.5, 22, -1), (1, 2, 1), BONE_WHITE),
          C((-3, 17, -4.2), (2, 1, 1), WARPAINT), C((1, 17, -4.2), (2, 1, 1), WARPAINT), C((-4, 20, -4), (8, 1, 7), SHAMAN_HIDE_DARK, "fur")],
    items=[Bone("staff", (4.5, 6, 0), [C((4, 0, -1), (1, 18, 1), WOOD, "wood"), C((3, 18, -2), (3, 3, 3), BONE_WHITE, "eye"),
                                       C((3.5, 16, -1.5), (2, 1, 2), FEATHER_RED), C((5, 14, -0.5), (1, 2, 1), FEATHER_RED)], "arm_right")],
)


# ---------------------------------------------------------------- elementals
FL_CORE, FL_HOT, FL, FL_DARK, CHAR, CHAR_DARK = 0xFFF2B0, 0xFFC43A, 0xFF7A1E, 0xC43A12, 0x3A2A24, 0x241A16
WA_LIGHT, WA, WA_DARK, FOAM = 0x8ED4F2, 0x3E9AD8, 0x2A64A8, 0xE6F6FC
ICE_DEEP, SNOW, FROST_EYE = 0x5A9CC4, 0xF4FAFC, 0x2AE0FF
STORM, STORM_DARK, SPARK, BOLT = 0x4E4E80, 0x303056, 0xF4F0FF, 0xB89CFF
GRANITE, EARTH_DARK = 0x8A7A6E, 0x4A3E36
BARK, BARK_DARK, LEAF, LEAF_LIGHT, BLOSSOM, SAP = 0x6A4A2E, 0x4A321E, 0x3E8A34, 0x78C050, 0xF0A0C8, 0xC8FF8A
ARC, ARC_DARK, ARC_DEEP, ARC_LIGHT, GOLD = 0xE070D0, 0x6A2A7A, 0x3E1A4A, 0xFFC8F4, 0xE8C050
SHROUD, SHROUD_DARK, SOUL = 0x2E2A36, 0x1A171F, 0x5AE8E0

FLYERS |= {"fire_elemental", "lightning_elemental", "arcane_elemental", "death_elemental"}

MOBS["fire_elemental"] = sym(
    Bone("body", (0, 10, 0), [C((-3, 8, -2.5), (6, 7, 5), FL_HOT, "flame"), C((-2, 4, -2), (4, 4, 4), FL, "flame"),
                              C((-1, 1, -1), (2, 3, 2), FL_DARK, "flame"), C((-3.5, 11, -3), (7, 2, 1), CHAR, "lava"),
                              C((-1, 10, -3.5), (2, 2, 1), FL_CORE), C((-5, 13, -2), (2, 2, 4), CHAR, "lava"),
                              C((3, 13, -2), (2, 2, 4), CHAR, "lava"), C((-2.5, 9, 2.5), (5, 5, 1), FL, "flame")]),
    Bone("head", (0, 15, 0), [C((-3, 15, -3), (6, 5, 6), FL_HOT, "flame"), C((-3.5, 18, -3.5), (7, 1, 1), CHAR),
                              C((-2, 16.5, -3.5), (1, 1, 1), FL_CORE), C((1, 16.5, -3.5), (1, 1, 1), FL_CORE),
                              C((-1.5, 15, -3.5), (3, 1, 1), FL_DARK)], "body"),
    Bone("crest", (0, 20, 0), [C((-2, 20, -2), (4, 3, 4), FL, "flame"), C((-1, 23, -1), (2, 3, 2), FL_HOT, "flame"),
                               C((-0.5, 26, 0), (1, 2, 1), FL_CORE), C((-3, 19, 0), (1, 3, 2), FL, "flame"),
                               C((2, 19, 0), (1, 3, 2), FL, "flame"), C((-1.5, 19, 3), (3, 2, 2), FL_DARK, "flame")], "head"),
    Bone("arm_left", (-5, 14, 0), [C((-7, 9, -1.5), (2, 5, 3), FL, "flame"), C((-7.5, 6, -2), (3, 3, 4), FL_HOT, "flame"),
                                   C((-7, 5, -1.5), (2, 1, 3), FL_CORE)], "body"),
)

MOBS["water_elemental"] = sym(
    Bone("base", (0, 0, 0), [C((-5, 0, -5), (10, 1, 10), WA_DARK, "water"), C((-3.5, 1, -3.5), (7, 3, 7), WA, "water"),
                             C((-2.5, 4, -2.5), (5, 4, 5), WA, "water"), C((-6, 0, -2), (1, 1, 4), FOAM), C((5, 0, -3), (1, 1, 5), FOAM),
                             C((-2, 0, 5), (5, 1, 1), FOAM)]),
    Bone("body", (0, 8, 0), [C((-3.5, 8, -3), (7, 7, 6), WA, "water"), C((-2.5, 12, -3.5), (5, 2, 1), FOAM),
                             C((-3, 14, -2.5), (6, 1, 5), WA_LIGHT)], "base"),
    Bone("head", (0, 15, 0), [C((-3, 15, -3), (6, 5, 6), WA_LIGHT, "water"), C((-2, 17, -3.5), (1, 1, 1), FOAM),
                              C((1, 17, -3.5), (1, 1, 1), FOAM), C((-1, 20, -2), (2, 2, 5), FOAM),
                              C((-0.5, 18, 3), (1, 3, 2), WA_LIGHT), C((-1.5, 19, 2), (3, 1, 2), WA)], "body"),
    Bone("arm_left", (-3.5, 14, 0), [C((-6, 9, -1.5), (3, 6, 3), WA, "water"), C((-6.5, 7, -2), (4, 2, 4), WA_LIGHT, "water"),
                                     C((-6, 6, -1), (1, 1, 2), FOAM)], "body"),
)

MOBS["cold_elemental"] = sym(
    Bone("body", (0, 8, 0), [C((-5, 8, -3), (10, 9, 6), ICE, "crystal"), C((-4, 8, -3.5), (8, 2, 1), ICE_DEEP),
                             C((-1.5, 12, -3.5), (3, 3, 1), FROST_EYE), C((-4, 11, 3), (8, 5, 1), ICE_DEEP, "crystal")]),
    Bone("spike_left", (-4, 17, 0), [C((-6, 17, -2), (3, 3, 3), ICE_LIGHT, "crystal"), C((-5.5, 20, -1.5), (2, 3, 2), SNOW),
                                     C((-5, 23, -1), (1, 2, 1), ICE_LIGHT)], "body"),
    Bone("back_spike", (0, 16, 3), [C((-1, 14, 4), (2, 5, 2), ICE_LIGHT, "crystal"), C((-0.5, 19, 4.5), (1, 2, 1), SNOW)], "body"),
    Bone("head", (0, 17, 0), [C((-3, 17, -3.5), (6, 5, 6), ICE_LIGHT, "crystal"), C((-2.5, 19, -4), (2, 1, 1), FROST_EYE),
                              C((0.5, 19, -4), (2, 1, 1), FROST_EYE), C((-2, 22, -2), (1, 3, 1), SNOW),
                              C((0, 22, -1), (1, 4, 1), ICE_LIGHT), C((1.5, 22, -2), (1, 2, 1), SNOW),
                              C((-2, 17, -4), (4, 1, 1), ICE_DEEP)], "body"),
    Bone("arm_left", (-5, 16, 0), [C((-8, 8, -2), (3, 8, 4), ICE, "crystal"), C((-8.5, 5, -2.5), (4, 4, 5), ICE_LIGHT, "crystal"),
                                   C((-8, 4, -1), (1, 1, 1), SNOW), C((-6, 4, 0), (1, 1, 1), SNOW)], "body"),
    Bone("leg_left", (-3, 8, 0), [C((-5, 1, -2), (4, 7, 4), ICE_DEEP, "crystal"), C((-5.5, 0, -3), (5, 2, 5), ICE, "crystal")], "body"),
)

MOBS["lightning_elemental"] = sym(
    Bone("body", (0, 10, 0), [C((-4, 7, -4), (8, 7, 8), STORM, "cloud"), C((-6, 8, -3), (2, 5, 6), STORM_DARK, "cloud"),
                              C((4, 8, -3), (2, 5, 6), STORM_DARK, "cloud"), C((-3, 14, -3), (6, 2, 6), STORM, "cloud"),
                              C((-3, 5, -3), (6, 2, 6), STORM_DARK, "cloud"), C((-2.5, 11, -4.5), (2, 1, 1), SPARK),
                              C((0.5, 11, -4.5), (2, 1, 1), SPARK), C((-1.5, 9, -4.5), (3, 1, 1), BOLT),
                              C((-2, 16, -2), (2, 1, 3), STORM_DARK, "cloud"), C((1, 16, 0), (2, 1, 2), STORM, "cloud")]),
    Bone("ring", (0, 10, 0), [C((-7, 10, -7), (14, 1, 1), BOLT, "rune"), C((-7, 10, 6), (14, 1, 1), BOLT, "rune"),
                              C((-7, 10, -6), (1, 1, 12), BOLT, "rune"), C((6, 10, -6), (1, 1, 12), BOLT, "rune")], "body"),
    Bone("tail", (0, 5, 0), [C((-0.5, 2, -0.5), (1, 3, 1), SPARK), C((0.5, 0, -0.5), (1, 2, 1), BOLT), C((-1.5, 3, -0.5), (1, 1, 1), BOLT)], "body"),
    Bone("arm_left", (-6, 10, 0), [C((-9, 9, -1), (3, 2, 2), BOLT), C((-10, 6, -1), (2, 3, 2), SPARK), C((-11, 4, -1), (2, 2, 2), BOLT)], "body"),
)

MOBS["earth_elemental"] = sym(
    Bone("body", (0, 7, 0), [C((-7, 7, -4), (14, 10, 8), STONE, "cracks"), C((-6, 17, -3), (12, 1, 6), MOSS, "spots"),
                             C((-5, 9, -4.5), (10, 3, 1), STONE_DARK, "cracks"), C((-2, 18, -1), (2, 3, 2), AMETHYST),
                             C((1, 18, 0), (2, 2, 2), AMETHYST), C((-6, 10, 4), (12, 6, 1), GRANITE, "cracks")]),
    Bone("head", (0, 15, -4), [C((-3, 12, -8), (6, 5, 4), GRANITE, "cracks"), C((-2, 14, -8.5), (1, 1, 1), AMBER),
                               C((1, 14, -8.5), (1, 1, 1), AMBER), C((-3.5, 16, -8.5), (7, 1, 2), STONE_DARK),
                               C((-2, 12, -8.5), (4, 1, 1), EARTH_DARK)], "body"),
    Bone("arm_left", (-7, 15, 0), [C((-11, 13, -3), (5, 5, 6), STONE, "cracks"), C((-10.5, 6, -2.5), (4, 7, 5), STONE_DARK, "cracks"),
                                   C((-11.5, 1, -3.5), (6, 5, 7), STONE, "cracks"), C((-10, 18, -1), (2, 2, 2), MOSS)], "body"),
    Bone("leg_left", (-3.5, 7, 0), [C((-7, 0, -3), (5, 7, 5), STONE_DARK, "cracks")], "body"),
)

MOBS["life_elemental"] = sym(
    Bone("body", (0, 7, 0), [C((-3.5, 7, -2.5), (7, 9, 5), BARK, "wood"), C((-1, 11, -3), (2, 2, 1), SAP),
                             C((-3, 13, -3), (1, 3, 1), LEAF), C((2, 8, -3), (1, 4, 1), LEAF)]),
    Bone("head", (0, 16, 0), [C((-3, 16, -3), (6, 6, 6), BARK, "wood"), C((-2, 18, -3.5), (1, 1, 1), SAP),
                              C((1, 18, -3.5), (1, 1, 1), SAP), C((-1, 16.5, -3.5), (2, 1, 1), BARK_DARK)], "body"),
    Bone("crown", (0, 22, 0), [C((-6, 22, -6), (12, 4, 12), LEAF, "spots"), C((-4, 26, -4), (8, 3, 8), LEAF_LIGHT, "spots"),
                               C((-5, 26, -5), (2, 1, 2), BLOSSOM), C((3, 26, 2), (2, 1, 2), BLOSSOM), C((-1, 29, -1), (2, 1, 2), BLOSSOM),
                               C((5, 24, -6.5), (2, 1, 1), BLOSSOM), C((-6.5, 23, 3), (1, 1, 2), BLOSSOM)], "head"),
    Bone("arm_left", (-3.5, 15, 0), [C((-5.5, 8, -1), (2, 8, 2), BARK, "wood"), C((-6.5, 6, -1.5), (3, 2, 3), LEAF, "spots"),
                                     C((-6, 14, -1.5), (2, 2, 3), LEAF_LIGHT)], "body"),
    Bone("leg_left", (-2, 7, 0), [C((-3.5, 1, -1.5), (3, 6, 3), BARK, "wood"), C((-4.5, 0, -2.5), (5, 1, 5), BARK_DARK, "wood")], "body"),
)

MOBS["arcane_elemental"] = sym(
    Bone("body", (0, 12, 0), [C((-2, 4, -2), (4, 2, 4), ARC_DARK), C((-3, 6, -3), (6, 3, 6), ARC, "crystal"),
                              C((-4, 9, -4), (8, 6, 8), ARC, "crystal"), C((-3, 15, -3), (6, 3, 6), ARC, "crystal"),
                              C((-2, 18, -2), (4, 2, 4), ARC_LIGHT), C((-1, 20, -1), (2, 2, 2), ARC_LIGHT),
                              C((-2, 11, -4.5), (4, 3, 1), GOLD, "eye")]),
    Bone("ring", (0, 12, 0), [C((-8, 12, -8), (16, 1, 1), GOLD, "rune"), C((-8, 12, 7), (16, 1, 1), GOLD, "rune"),
                              C((-8, 12, -7), (1, 1, 14), GOLD, "rune"), C((7, 12, -7), (1, 1, 14), GOLD, "rune"),
                              C((-7, 12, -0.5), (3, 1, 1), GOLD), C((4, 12, -0.5), (3, 1, 1), GOLD)], "body", (0.3, 0, 0.25)),
    Bone("shard_left", (-7.5, 12, 0), [C((-8.5, 6, -1), (2, 4, 2), ARC, "crystal"), C((-8, 10, -0.5), (1, 2, 1), ARC_LIGHT),
                                       C((-8, 5, -0.5), (1, 1, 1), ARC_LIGHT)], "ring"),
)

MOBS["death_elemental"] = sym(
    Bone("body", (0, 14, 0), [C((-4, 8, -3), (8, 10, 6), SHROUD, "cloth"), C((-3.5, 3, -2.5), (7, 5, 5), SHROUD_DARK, "cloth"),
                              C((-3, 0, -2), (2, 3, 2), SHROUD_DARK), C((1, 1, -1), (2, 2, 2), SHROUD_DARK), C((-1, 1, 1), (2, 2, 2), SHROUD_DARK),
                              C((-1, 12, -3.5), (2, 2, 1), SOUL), C((-3, 15, -3.5), (6, 1, 1), BONE_DARK)]),
    Bone("head", (0, 18, 0), [C((-3.5, 18, -3.5), (7, 7, 7), SHROUD, "cloth"), C((-2.5, 19, -4), (5, 4, 1), BONE_WHITE),
                              C((-2, 21, -4.5), (1, 1, 1), SOUL), C((1, 21, -4.5), (1, 1, 1), SOUL),
                              C((-1.5, 19, -4.5), (3, 1, 1), BONE_DARK), C((-2, 25, -1), (4, 2, 4), SHROUD_DARK, "cloth")], "body"),
    Bone("arm_left", (-4, 17, 0), [C((-7, 11, -2), (3, 7, 4), SHROUD, "cloth"), C((-6.5, 8, -1.5), (2, 3, 3), BONE_WHITE),
                                   C((-6.5, 7, -2.5), (1, 1, 1), BONE_WHITE), C((-5.5, 7, -1), (1, 1, 1), BONE_WHITE)], "body"),
)


def titan(skin, dark, light, eye, pat, head=None, body=(), arm=(), extra=()):
    return sym(
        Bone("body", (0, 12, 0), [C((-9, 0, -6), (18, 10, 12), dark, pat), C((-11, 10, -7), (22, 12, 14), skin, pat),
                                  C((-13, 21, -6), (26, 5, 12), skin, pat), C((-4, 26, -4), (8, 3, 7), dark, pat), *body]),
        Bone("head", (0, 29, -1), head or [C((-5, 28, -7), (10, 10, 10), skin, pat), C((-5.5, 34, -7.5), (11, 2, 2), dark),
                                           C((-4, 32, -7.5), (3, 1, 1), eye), C((1, 32, -7.5), (3, 1, 1), eye),
                                           C((-4, 28, -7.5), (8, 2, 1), dark)], "body"),
        Bone("arm_left", (-13, 24, 0), [C((-19, 14, -4), (6, 11, 8), skin, pat), C((-20, 22, -5), (8, 6, 10), dark, pat), *arm], "body"),
        Bone("forearm_left", (-16, 14, 0), [C((-19.5, 6, -4.5), (7, 8, 9), dark, pat), C((-20, 0, -5), (8, 6, 10), skin, pat)], "arm_left"),
        *extra,
    )


MOBS["fire_titan"] = titan(
    CHAR, CHAR_DARK, FL_HOT, FL_CORE, "lava",
    head=[C((-5, 28, -7), (10, 10, 10), CHAR, "lava"), C((-5.5, 34, -7.5), (11, 2, 2), CHAR_DARK),
          C((-4, 32, -7.5), (3, 1, 1), FL_CORE), C((1, 32, -7.5), (3, 1, 1), FL_CORE), C((-3, 28, -7.5), (6, 2, 1), FL_HOT, "flame"),
          C((-3.5, 38, -5), (7, 2, 7), FL, "flame")],
    body=[C((-4, 13, -7.5), (8, 6, 1), FL_HOT, "flame"), C((-2, 15, -8), (4, 2, 1), FL_CORE), C((-10, 0, -7), (20, 2, 14), FL_DARK, "flame")],
    arm=[C((-19, 28, -3), (4, 4, 6), FL, "flame"), C((-18, 32, -2), (2, 3, 4), FL_HOT, "flame")],
    extra=[Bone("crown", (0, 40, -2), [C((-2, 40, -4), (4, 5, 4), FL_HOT, "flame"), C((-1, 45, -3), (2, 4, 2), FL_CORE),
                                       C((-5, 40, -2), (2, 4, 3), FL, "flame"), C((3, 40, -2), (2, 4, 3), FL, "flame"),
                                       C((-3, 40, 1), (6, 3, 3), FL, "flame"), C((-2, 36, 3), (4, 5, 3), FL_DARK, "flame")], "head")],
)

MOBS["water_titan"] = titan(
    WA, WA_DARK, FOAM, FOAM, "water",
    head=[C((-5, 28, -7), (10, 10, 10), WA_LIGHT, "water"), C((-5.5, 34, -7.5), (11, 2, 2), WA),
          C((-4, 32, -7.5), (3, 1, 1), FOAM), C((1, 32, -7.5), (3, 1, 1), FOAM), C((-4, 28, -7.5), (8, 2, 1), WA_DARK)],
    body=[C((-12, 0, -8), (24, 2, 16), FOAM, "water"), C((-10, 2, -7.5), (20, 2, 1), WA_LIGHT), C((-6, 16, -7.5), (12, 3, 1), WA_LIGHT, "water")],
    arm=[C((-20, 28, -4), (6, 2, 8), FOAM)],
    extra=[Bone("crest", (0, 38, -2), [C((-1.5, 38, -6), (3, 3, 12), FOAM, "water"), C((-1, 41, -3), (2, 3, 9), WA_LIGHT, "water"),
                                       C((-1, 36, 3), (2, 4, 6), WA_LIGHT, "water"), C((-0.5, 32, 7), (1, 6, 3), FOAM)], "head")],
)

MOBS["cold_titan"] = titan(
    ICE, ICE_DEEP, SNOW, FROST_EYE, "crystal",
    head=[C((-5, 28, -7), (10, 10, 10), ICE_LIGHT, "crystal"), C((-5.5, 34, -7.5), (11, 2, 2), ICE_DEEP),
          C((-4, 32, -7.5), (3, 1, 1), FROST_EYE), C((1, 32, -7.5), (3, 1, 1), FROST_EYE),
          C((-4, 25, -7.5), (2, 3, 1), SNOW), C((-1, 24, -7.5), (2, 4, 1), ICE_LIGHT), C((2, 25, -7.5), (2, 3, 1), SNOW),
          C((-4, 28, -7.5), (8, 2, 1), ICE_DEEP)],
    body=[C((-2, 14, -7.5), (4, 4, 1), FROST_EYE), C((-10, 0, -7), (20, 2, 14), SNOW), C((-3, 18, 6), (6, 8, 3), ICE_LIGHT, "crystal")],
    arm=[C((-19, 28, -3), (4, 5, 4), ICE_LIGHT, "crystal"), C((-18, 33, -2), (2, 4, 2), SNOW), C((-15, 28, 0), (2, 3, 2), ICE_LIGHT)],
    extra=[Bone("crown", (0, 38, -2), [C((-4, 38, -5), (2, 4, 2), SNOW), C((-1, 38, -6), (2, 6, 2), ICE_LIGHT, "crystal"),
                                       C((2, 38, -5), (2, 4, 2), SNOW), C((-4, 38, 0), (8, 2, 2), ICE_LIGHT), C((-0.5, 40, 0.5), (1, 3, 1), SNOW)], "head"),
           Bone("back_spike_left", (-6, 22, 6), [C((-8, 20, 6), (3, 7, 3), ICE_LIGHT, "crystal"), C((-7.5, 27, 6.5), (2, 3, 2), SNOW)], "body")],
)

MOBS["lightning_titan"] = titan(
    STORM, STORM_DARK, BOLT, SPARK, "cloud",
    head=[C((-5, 28, -7), (10, 10, 10), STORM, "cloud"), C((-5.5, 34, -7.5), (11, 2, 2), STORM_DARK),
          C((-4, 32, -7.5), (3, 1, 1), SPARK), C((1, 32, -7.5), (3, 1, 1), SPARK), C((-3, 28, -7.5), (6, 1, 1), BOLT)],
    body=[C((-2, 12, -7.5), (2, 3, 1), SPARK), C((0, 15, -7.5), (2, 3, 1), SPARK), C((-2, 18, -7.5), (2, 3, 1), SPARK),
          C((-11, 8, -6.5), (22, 1, 1), BOLT, "rune"), C((-12, 0, -8), (24, 3, 16), STORM_DARK, "cloud")],
    arm=[C((-20, 20, -5.5), (8, 1, 11), BOLT, "rune")],
    extra=[Bone("horn_left", (-5, 36, -2), [C((-7, 36, -3), (2, 2, 2), BOLT), C((-8, 38, -3), (2, 3, 2), SPARK), C((-7, 41, -3), (2, 2, 2), BOLT)], "head"),
           Bone("halo", (0, 40, -2), [C((-7, 43, -9), (14, 1, 1), BOLT, "rune"), C((-7, 43, 4), (14, 1, 1), BOLT, "rune"),
                                      C((-8, 43, -9), (1, 1, 14), BOLT, "rune"), C((7, 43, -9), (1, 1, 14), BOLT, "rune"),
                                      C((-0.5, 38, -2.5), (1, 5, 1), SPARK)], "head")],
)

MOBS["earth_titan"] = titan(
    STONE, STONE_DARK, MOSS, AMBER, "cracks",
    head=[C((-5, 28, -8), (10, 8, 10), GRANITE, "cracks"), C((-6, 34, -8.5), (12, 3, 3), STONE_DARK, "cracks"),
          C((-4, 32, -8.5), (3, 1, 1), AMBER), C((1, 32, -8.5), (3, 1, 1), AMBER), C((-5, 28, -8.5), (10, 2, 2), STONE_DARK),
          C((-4, 36, -6), (8, 2, 7), MOSS, "spots")],
    body=[C((-12, 25, -5), (24, 2, 10), MOSS, "spots"), C((-4, 26, 2), (3, 4, 3), AMETHYST), C((2, 26, 3), (2, 3, 2), AMETHYST),
          C((-11, 0, -8), (22, 2, 16), EARTH_DARK, "cracks"), C((5, 12, -7.5), (4, 5, 1), GRANITE, "cracks")],
    arm=[C((-21, 22, -6), (10, 8, 12), STONE, "cracks"), C((-19, 30, -4), (6, 2, 8), MOSS, "spots"), C((-18, 32, -1), (2, 3, 2), AMETHYST)],
)

MOBS["life_titan"] = titan(
    BARK, BARK_DARK, LEAF, SAP, "wood",
    head=[C((-5, 28, -7), (10, 10, 10), BARK, "wood"), C((-5.5, 34, -7.5), (11, 2, 2), BARK_DARK),
          C((-4, 32, -7.5), (3, 1, 1), SAP), C((1, 32, -7.5), (3, 1, 1), SAP), C((-3, 28, -7.5), (6, 2, 1), LEAF, "spots")],
    body=[C((-3, 14, -7.5), (6, 6, 1), SAP), C((-8, 18, -7.5), (2, 4, 1), LEAF), C((6, 11, -7.5), (2, 5, 1), LEAF),
          C((-12, 0, -8), (24, 2, 16), LEAF, "spots"), *[C((x, 2, -8.5), (2, 1, 1), BLOSSOM) for x in (-9, -2, 5)]],
    arm=[C((-20, 26, -5), (8, 3, 10), LEAF_LIGHT, "spots"), C((-18, 29, -1), (2, 1, 2), BLOSSOM)],
    extra=[Bone("crown", (0, 38, -2), [C((-10, 38, -10), (20, 5, 16), LEAF, "spots"), C((-7, 43, -8), (14, 4, 12), LEAF_LIGHT, "spots"),
                                       C((-3, 47, -4), (6, 2, 6), LEAF, "spots"), *[C(p, (2, 1, 2), BLOSSOM) for p in
                                       ((-8, 43, -9), (5, 43, -6), (-2, 47, -5), (7, 42, 2), (-6, 47, 0), (1, 49, -1))]], "head"),
           Bone("antler_left", (-5, 36, -2), [C((-9, 36, -3), (4, 2, 2), BARK_DARK, "wood"), C((-11, 38, -3), (2, 5, 2), BARK_DARK, "wood"),
                                              C((-13, 41, -3), (2, 2, 2), BARK, "wood")], "head")],
)

MOBS["arcane_titan"] = titan(
    ARC_DARK, ARC_DEEP, ARC, ARC_LIGHT, "rune",
    head=[C((-5, 28, -7), (10, 10, 10), ARC_DARK, "rune"), C((-5.5, 34, -7.5), (11, 2, 2), ARC_DEEP),
          C((-4, 32, -7.5), (3, 1, 1), ARC_LIGHT), C((1, 32, -7.5), (3, 1, 1), ARC_LIGHT),
          C((-1, 35, -8), (2, 2, 1), GOLD, "eye"), C((-4, 28, -7.5), (8, 2, 1), ARC_DEEP)],
    body=[C((-3, 13, -8), (6, 6, 1), ARC, "crystal"), C((-2, 14, -8.5), (4, 4, 1), ARC_LIGHT), C((-11, 9, -7.5), (22, 1, 1), GOLD, "rune"),
          C((-11, 0, -7), (22, 1, 14), GOLD, "rune")],
    arm=[C((-19, 28, -3), (4, 6, 5), ARC, "crystal"), C((-18, 34, -2), (2, 4, 3), ARC_LIGHT), C((-20, 20, -5.5), (8, 1, 11), GOLD)],
    extra=[Bone("halo", (0, 38, 3), [C((-6, 44, 3), (12, 1, 1), GOLD, "rune"), C((-6, 32, 3), (12, 1, 1), GOLD, "rune"),
                                     C((-7, 32, 3), (1, 13, 1), GOLD, "rune"), C((6, 32, 3), (1, 13, 1), GOLD, "rune"),
                                     C((-1, 40, 3), (2, 2, 1), ARC_LIGHT), C((-0.5, 38, 3), (1, 6, 1), GOLD)], "head")],
)

MOBS["death_titan"] = titan(
    SHROUD, SHROUD_DARK, BONE_WHITE, SOUL, "cloth",
    head=[C((-6, 27, -6), (12, 12, 11), SHROUD, "cloth"), C((-4, 28, -7), (8, 8, 1), BONE_WHITE),
          C((-3, 32, -7.5), (2, 2, 1), SOUL), C((1, 32, -7.5), (2, 2, 1), SOUL), C((-0.5, 30, -7.5), (1, 1, 1), BLACK),
          C((-3, 28, -7.5), (6, 1, 1), BONE_DARK), C((-4, 39, -3), (8, 2, 6), SHROUD_DARK, "cloth")],
    body=[*[C((-7, y, -7.5), (14, 1, 1), BONE_WHITE) for y in (12, 15, 18)], C((-0.5, 11, -8), (1, 10, 1), BONE_DARK),
          C((-1, 14, -8.5), (2, 2, 1), SOUL), C((-10, 0, -7), (20, 3, 14), SHROUD_DARK, "cloth")],
    arm=[C((-19, 28, -3), (4, 3, 6), BONE_WHITE), C((-18, 31, -2), (2, 3, 2), BONE_DARK)],
    extra=[Bone("horn_left", (-6, 36, -1), [C((-9, 36, -2), (3, 2, 2), BONE_WHITE), C((-10, 38, -2), (2, 3, 2), BONE_WHITE),
                                            C((-10, 41, -1), (2, 2, 2), BONE_DARK)], "head")],
)


# ---------------------------------------------------------------- validation
def rotate(rot, p):
    rx, ry, rz = rot
    x, y, z = p
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return x, y, z


def offset(bone, pivots):
    px, py, pz = bone.pivot
    if bone.parent:
        qx, qy, qz = pivots[bone.parent].pivot
        return px - qx, qy - py, pz - qz
    return px, 24 - py, pz


def world_box(bone, cube, bones):
    (x, y, z), (w, h, d) = cube.origin, cube.size
    px, py, pz = bone.pivot
    pts = [(cx - px, py - cy, cz - pz) for cx in (x, x + w) for cy in (y, y + h) for cz in (z, z + d)]
    b = bone
    while b:
        o = offset(b, bones)
        pts = [tuple(o[i] + v for i, v in enumerate(rotate(b.rot, p))) for p in pts]
        b = bones[b.parent] if b.parent else None
    pts = [(px_, 24 - py_, pz_) for px_, py_, pz_ in pts]
    return [min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)]


def validate(name, bones):
    index = {b.name: b for b in bones}
    boxes = [(b.name, world_box(b, c, index)) for b in bones for c in b.cubes]
    parent = list(range(len(boxes)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    eps = 0.05
    for i, (_, (lo, hi)) in enumerate(boxes):
        for j in range(i):
            lo2, hi2 = boxes[j][1]
            if all(lo[k] <= hi2[k] + eps and lo2[k] <= hi[k] + eps for k in range(3)):
                parent[find(i)] = find(j)
    groups = {}
    for i, (bone, _) in enumerate(boxes):
        groups.setdefault(find(i), set()).add(bone)
    errors = []
    if len(groups) > 1:
        errors.append("detached parts: " + " | ".join(",".join(sorted(g)) for g in groups.values()))
    bottom = min(lo[1] for _, (lo, _) in boxes)
    if bottom < -0.6:
        errors.append(f"sinks below the ground to y={bottom:.2f}")
    if name not in FLYERS:
        if bottom > 0.05:
            errors.append(f"floats at y={bottom:.2f}")
        for b in bones:
            if b.name.startswith(("leg", "foot")) and min(world_box(b, c, index)[0][1] for c in b.cubes) > 0.1:
                errors.append(f"{b.name} does not reach the ground")
    if errors:
        raise SystemExit(f"{name}: " + "; ".join(errors))


# ---------------------------------------------------------------- UV packing
def footprint(c):
    w, h, d = (int(round(v)) for v in c.size)
    return 2 * (d + w), d + h


def pack(bones):
    cubes = sorted((c for b in bones for c in b.cubes), key=lambda c: -footprint(c)[1])
    width = max(64, 1 << (max(footprint(c)[0] for c in cubes) - 1).bit_length())
    x = y = row = 0
    for c in cubes:
        fw, fh = footprint(c)
        if x + fw > width:
            x, y, row = 0, y + row, 0
        c.uv = (x, y)
        x += fw
        row = max(row, fh)
    height = max(32, 1 << (y + row - 1).bit_length())
    return width, height


# ---------------------------------------------------------------- painting
def rgb(color):
    return (color >> 16) & 255, (color >> 8) & 255, color & 255


def shade(color, k):
    return tuple(max(0, min(255, int(v * k))) for v in color)


def faces(c):
    w, h, d = (int(round(v)) for v in c.size)
    u, v = c.uv
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "west": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
    }


LIGHT = {"top": 1.12, "bottom": 0.66, "north": 1.0, "south": 0.88, "east": 0.84, "west": 0.84}


def paint(img, c, rnd):
    base, p = rgb(c.color), c.pattern
    for face, (x0, y0, w, h) in faces(c).items():
        side = face not in ("top", "bottom")
        streaks = [[rnd.random() for _ in range(h // 2 + 1)] for _ in range(w)]
        knot = (rnd.randrange(max(w, 1)), rnd.randrange(max(h, 1)))
        for dx in range(w):
            for dy in range(h):
                k = LIGHT[face] * (1 + (rnd.random() - 0.5) * 0.12)
                if side and h > 1:
                    k *= 1.06 - 0.16 * dy / (h - 1)
                if w >= 3 and h >= 3 and (dx in (0, w - 1) or dy in (0, h - 1)):
                    k *= 0.82
                if p == "plates" and side and dy % 3 == 2:
                    k *= 0.8
                elif p == "stripes" and dx % 3 == 1:
                    k *= 0.87
                elif p == "cracks" and rnd.random() < 0.07:
                    k *= 0.62
                elif p == "spots" and face != "bottom" and rnd.random() < 0.08:
                    k *= 1.22
                elif p == "fur":
                    k *= 0.84 + 0.28 * streaks[dx][dy // 2]
                elif p == "wood":
                    k *= 0.88 if (dy + dx // 3) % 3 == 0 else 1.0
                    if (dx, dy) == knot and w * h > 6:
                        k *= 0.7
                elif p == "cloth" and (dx + dy) % 2:
                    k *= 0.94
                elif p == "eye" and face == "north" and dx == w // 2:
                    k *= 0.18
                elif p == "flame":
                    k *= (0.78 + 0.5 * dy / max(h - 1, 1) if side else 1.1) + (streaks[dx][dy // 2] - 0.5) * 0.25
                elif p == "water":
                    k *= 1.18 if (dy + (dx // 3) % 2) % 4 == 0 else 0.96 + 0.08 * streaks[dx][dy // 2]
                elif p == "crystal":
                    k *= 1.28 if (dx + dy) % 5 == 0 else 0.88 if (dx - dy) % 7 == 0 else 1.0
                elif p == "cloud":
                    k *= 0.86 + 0.3 * streaks[dx][dy // 2]
                color = base
                if p == "lava" and rnd.random() < 0.09:
                    color, k = rgb(0xFF7A1E), 1.0 + rnd.random() * 0.25
                elif p == "rune" and face != "bottom" and rnd.random() < 0.07:
                    k *= 1.7
                img.putpixel((x0 + dx, y0 + dy), shade(color, k) + (255,))


def texture(name, bones):
    width, height = pack(bones)
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    rnd = random.Random(name)
    for b in bones:
        for c in b.cubes:
            paint(img, c, rnd)
    os.makedirs(TEXTURES, exist_ok=True)
    img.save(os.path.join(TEXTURES, name + ".png"))
    return width, height


# ---------------------------------------------------------------- blockbench
def data_url(path):
    with open(path, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


def write_bbmodel(name, bones, width, height):
    ids = uuid.uuid5(uuid.NAMESPACE_URL, "magicka/" + name)
    uid = lambda key: str(uuid.uuid5(ids, key))
    elements, groups = [], {}
    for b in bones:
        children = []
        for i, c in enumerate(b.cubes):
            (x, y, z), (w, h, d) = c.origin, c.size
            key = f"{b.name}/{i}"
            elements.append({"name": b.name if len(b.cubes) == 1 else f"{b.name}_{i}", "type": "cube", "uuid": uid(key),
                             "box_uv": True, "uv_offset": list(c.uv), "inflate": c.inflate, "mirror_uv": c.mirror, "from": [x, y, z], "to": [x + w, y + h, z + d],
                             "origin": list(b.pivot), "rotation": [0, 0, 0], "color": 0, "faces": {}})
            children.append(uid(key))
        rx, ry, rz = (math.degrees(v) for v in b.rot)
        groups[b.name] = {"name": b.name, "uuid": uid(b.name), "origin": list(b.pivot), "rotation": [-rx, -ry, rz],
                          "export": True, "isOpen": True, "visibility": True, "children": children}
    outliner = []
    for b in bones:
        (groups[b.parent]["children"] if b.parent else outliner).append(groups[b.name])
    path = os.path.join(TEXTURES, f"{name}.png")
    textures = [{"name": f"{name}.png", "id": "0", "uuid": uid("texture/" + name), "path": path, "render_mode": "default",
                 "width": width, "height": height, "uv_width": width, "uv_height": height, "source": data_url(path)}]
    model = {"meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
             "name": name, "model_identifier": name, "modded_entity_version": "1.17",
             "resolution": {"width": width, "height": height},
             "elements": elements, "outliner": outliner, "textures": textures}
    os.makedirs(BBMODELS, exist_ok=True)
    with open(os.path.join(BBMODELS, name + ".bbmodel"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=1)


def read_bbmodel(path):
    with open(path, encoding="utf-8") as f:
        model = json.load(f)
    name = os.path.splitext(os.path.basename(path))[0]
    cubes = {e["uuid"]: e for e in model["elements"]}
    bones = []

    def walk(group, parent):
        bx, by, bz = group.get("rotation", (0, 0, 0))
        bone = Bone(group["name"], tuple(group["origin"]), [], parent, (-math.radians(bx), -math.radians(by), math.radians(bz)))
        bones.append(bone)
        for child in group["children"]:
            if isinstance(child, dict):
                walk(child, bone.name)
                continue
            e = cubes[child]
            if e.get("type", "cube") != "cube" or any(e.get("rotation", (0, 0, 0))):
                raise SystemExit(f"{name}: element '{e['name']}' must be an unrotated cube; rotate its group instead")
            (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
            bone.cubes.append(Cube((x0, y0, z0), (x1 - x0, y1 - y0, z1 - z0), uv=tuple(e.get("uv_offset", (0, 0))),
                                   inflate=e.get("inflate", 0), mirror=e.get("mirror_uv", False)))

    for node in model["outliner"]:
        if not isinstance(node, dict):
            raise SystemExit(f"{name}: cube '{cubes[node]['name']}' sits outside any group")
        walk(node, None)
    if len({b.name for b in bones}) != len(bones):
        raise SystemExit(f"{name}: group names must be unique")
    for t in model["textures"]:
        if os.path.splitext(t["name"])[0] == name:
            with open(os.path.join(TEXTURES, name + ".png"), "wb") as f:
                f.write(base64.b64decode(t["source"].split(",", 1)[1]))
    return name, bones, model["resolution"]["width"], model["resolution"]["height"]


# ---------------------------------------------------------------- java
def num(v):
    v = round(float(v), 4)
    return f"{v:g}F"


def camel(name):
    return re.sub(r"_(\w)", lambda m: m.group(1).upper(), name)


def java_layer(name, bones, width, height):
    pivots = {b.name: b.pivot for b in bones}
    lines = [f"    public static LayerDefinition {camel(name)}() {{",
             "        MeshDefinition mesh = new MeshDefinition();",
             "        PartDefinition root = mesh.getRoot();"]
    for b in bones:
        px, py, pz = b.pivot
        if b.parent:
            qx, qy, qz = pivots[b.parent]
            offset = (px - qx, qy - py, pz - qz)
        else:
            offset = (px, 24 - py, pz)
        cubes = "CubeListBuilder.create()"
        for c in b.cubes:
            (x, y, z), (w, h, d) = c.origin, c.size
            box = f"{num(x - px)}, {num(py - y - h)}, {num(z - pz)}, {num(w)}, {num(h)}, {num(d)}"
            if c.inflate:
                box += f", new CubeDeformation({num(c.inflate)})"
            cubes += f".texOffs({int(c.uv[0])}, {int(c.uv[1])})" + (".mirror()" if c.mirror else "") + f".addBox({box})" + (".mirror(false)" if c.mirror else "")
        pose = f"PartPose.offsetAndRotation({', '.join(num(v) for v in offset)}, {', '.join(num(v) for v in b.rot)})"
        parent = camel(b.parent) if b.parent else "root"
        lines.append(f"        PartDefinition {camel(b.name)} = {parent}.addOrReplaceChild(\"{b.name}\", {cubes}, {pose});")
    lines.append(f"        return LayerDefinition.create(mesh, {width}, {height});")
    lines.append("    }")
    return "\n".join(lines)


def main():
    if "--seed" in sys.argv:
        for name, bones in MOBS.items():
            validate(name, bones)
            write_bbmodel(name, bones, *texture(name, bones))
    methods, entries = [], []
    for path in sorted(os.listdir(BBMODELS)):
        if not path.endswith(".bbmodel"):
            continue
        name, bones, width, height = read_bbmodel(os.path.join(BBMODELS, path))
        validate(name, bones)
        methods.append(java_layer(name, bones, width, height))
        entries.append(f"            Map.entry(\"{name}\", EnemyLayers::{camel(name)})")
    source = f"""package com.restor.magicka.client.enemy;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import java.util.Map;
import java.util.function.Supplier;

/** Generated by tools/gen_enemy_models.py from art/enemies/*.bbmodel; edit the models in Blockbench, not this file. */
@SuppressWarnings("unused")
public final class EnemyLayers {{
    public static final Map<String, Supplier<LayerDefinition>> ALL = Map.ofEntries(
{(","+chr(10)).join(entries)});

{(chr(10)*2).join(methods)}

    private EnemyLayers() {{}}
}}
"""
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(source)
    print(f"{len(entries)} models written")


if __name__ == "__main__":
    main()
