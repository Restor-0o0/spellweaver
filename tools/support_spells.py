"""Support spells that replace weak generated variants on the same formulas. Imported by import_spell_catalog.py."""


def effect(name, ticks, strength=1):
    return {'type': 'apply_effect', 'effect': 'minecraft:' + name, 'duration_ticks': ticks, 'strength': strength}


def spell(key, formula, name, shape, effects, self_effects, origin='aim_hit', delivery=None):
    size = sum(formula.values())
    return {
        'id': 'magicka:' + key, 'formula': formula, 'name': name, 'tier': 'Signature', 'tags': 'UTIL', 'shape': shape, 'origin': origin,
        'cast_ticks': [0, 5, 9, 14, 20, 27][size],
        'delivery': {'radius': 2.0, 'speed': 1.2, 'duration_ticks': 160, 'interval_ticks': 20, 'gravity': 0, 'burst_radius': 2.0, **(delivery or {})},
        'construction': {}, 'effects': effects, 'self': {'mode': 'generic', 'effects': self_effects},
        'primary': '', 'self_description': '',
    }


SUPPORT = {
    'Unstable Soul': spell('wisp_light', {'life': 1, 'arcane': 2}, 'Wisp Light', 'projectile',
                           [{'type': 'place_light'}], [{'type': 'place_light', 'offset_y': 2}], origin='caster_front'),
    'Convergence F / Li / A': spell('lantern_trail', {'fire': 1, 'life': 1, 'arcane': 1}, 'Lantern Trail', 'direct_target',
                                    [{'type': 'light_path', 'spacing': 6, 'count': 10}], [{'type': 'light_ring', 'radius': 6, 'count': 8}]),
    'Convergence F / E / Li': spell('glowstone', {'fire': 1, 'earth': 1, 'life': 1}, 'Glowstone', 'direct_target',
                                    [{'type': 'place_glowstone'}], [{'type': 'place_glowstone', 'offset_y': 2}]),
    'Charged Vital Spark': spell('cats_eye', {'life': 1, 'lightning': 2}, "Cat's Eye", 'direct_target',
                                 [effect('night_vision', 3600)], [effect('night_vision', 3600)]),
    'Flooded Spring': spell('gills', {'water': 2, 'life': 1}, 'Gills', 'direct_target',
                            [effect('water_breathing', 2400), effect('dolphins_grace', 600)], [effect('water_breathing', 2400), effect('dolphins_grace', 600)]),
    'Scorching Cautery': spell('fire_ward', {'fire': 2, 'life': 1}, 'Fire Ward', 'direct_target',
                               [{'type': 'remove_status', 'status': 'burning'}, effect('fire_resistance', 1200)],
                               [{'type': 'remove_status', 'status': 'burning'}, effect('fire_resistance', 1200)]),
    'Unstable Arc Lightning': spell('revealing_flash', {'lightning': 1, 'arcane': 2}, 'Revealing Flash', 'direct_target',
                                    [{'type': 'reveal', 'radius': 24, 'duration_ticks': 600}], [{'type': 'reveal', 'radius': 32, 'duration_ticks': 600}]),
    'Convergence W / C / Li': spell('douse', {'water': 1, 'cold': 1, 'life': 1}, 'Douse', 'direct_target',
                                    [{'type': 'extinguish', 'radius': 6}], [{'type': 'extinguish', 'radius': 6}]),
    'Charged Thunderstone': spell('miners_vigor', {'earth': 1, 'lightning': 2}, "Miner's Vigor", 'direct_target',
                                  [effect('haste', 2400, 2), effect('night_vision', 600)], [effect('haste', 2400, 2), effect('night_vision', 600)]),
}
