"""Generate every five-element recipe: eight authored ultimates plus amplified four-element bases.

Run after import_spell_catalog.py and add_death_spells.py, before localize_spells.py.
"""
import copy
import itertools
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPELLS = ROOT / 'src/main/resources/data/magicka/magicka_spell'
INDEX = ROOT / 'src/main/resources/assets/magicka/spell_index.json'
ELEMENTS = ['fire', 'water', 'cold', 'lightning', 'earth', 'life', 'arcane', 'death']
TITLE = {e: e.title() for e in ELEMENTS}
CAST_TICKS = 27


def status(name, ticks, strength=1):
    return {'type': 'apply_status', 'status': name, 'duration_ticks': ticks, 'strength': strength}


def damage(amount, element):
    return {'type': 'deal_damage', 'amount': amount, 'element': element}


BOOST = {
    'fire': [damage(4, 'fire'), {'type': 'modify_temperature', 'amount': 90}],
    'water': [{'type': 'set_wet', 'duration_ticks': 240}, {'type': 'apply_force', 'amount': 0.5, 'lift': 0}],
    'cold': [{'type': 'modify_temperature', 'amount': -80}, status('chilled', 180, 3)],
    'lightning': [damage(6, 'lightning'), {'type': 'add_charge', 'amount': 70}],
    'earth': [damage(6, 'earth'), status('brittle', 180)],
    'life': [{'type': 'heal', 'amount': 8}, status('regenerating', 200, 3)],
    'arcane': [damage(6, 'arcane'), status('arcane_unstable', 180, 3)],
    'death': [status('withering', 180, 3), {'type': 'drain_life', 'amount': 5, 'fraction': 0.6}],
}
SELF_BOOST = {
    'fire': [{'type': 'modify_temperature', 'amount': 30}],
    'water': [{'type': 'set_wet', 'duration_ticks': 120}],
    'cold': [{'type': 'modify_temperature', 'amount': -25}],
    'lightning': [{'type': 'add_charge', 'amount': 20}],
    'earth': [status('stone_shell', 200, 3)],
    'life': [{'type': 'heal', 'amount': 8}, status('regenerating', 200, 3)],
    'arcane': [status('arcane_unstable', 80)],
    'death': [status('withering', 60)],
}
SCALE = {'amount': 1.8, 'duration_ticks': 1.5, 'radius': 1.45, 'burst_radius': 1.45, 'range': 1.4,
         'lift': 1.5, 'count': 2, 'budget': 2.5}
HALVES = {'deal_damage', 'heal', 'drain_life'}


def amplify(node):
    node = copy.deepcopy(node)
    for key, factor in SCALE.items():
        if key not in node or isinstance(node[key], bool) or not isinstance(node[key], (int, float)):
            continue
        value = node[key] * factor
        if key == 'amount' and node.get('type') in HALVES:
            node[key] = round(value * 2) / 2
        elif key == 'count':
            node[key] = min(16, math.ceil(value))
        elif isinstance(node[key], int):
            node[key] = int(round(value))
        else:
            node[key] = round(value, 2)
    for key in ('fraction', 'health_fraction'):
        if key in node:
            node[key] = min(1.0, round(node[key] * 1.3, 2))
    for key in ('effects', 'else_effects'):
        if key in node:
            node[key] = [amplify(child) for child in node[key]]
    return node


def formula_key(counts):
    return tuple(sorted((k, v) for k, v in counts.items() if v))


def load_catalog():
    catalog = {}
    for path in SPELLS.glob('*.json'):
        data = json.loads(path.read_text(encoding='utf-8'))
        catalog[formula_key(data['formula'])] = data
    return catalog


def ultimate(key, element, name, tags, shape, effects, self_effects, self_mode='generic', origin='aim_hit',
             delivery=None, order=()):
    formula = {e: order.count(e) for e in dict.fromkeys(order)}
    incantation = ''.join(str(ELEMENTS.index(e) + 1) for e in order)
    assert len(order) == 5 and formula[element] == max(formula.values()), (key, order)
    return {
        'id': 'magicka:' + key, 'formula': formula, 'incantation': incantation,
        'name': name, 'tier': 'Signature', 'tags': tags,
        'shape': shape, 'origin': origin, 'cast_ticks': CAST_TICKS,
        'delivery': {'radius': 9.0, 'speed': 1.0, 'duration_ticks': 300, 'interval_ticks': 15, 'gravity': 0,
                     'burst_radius': 9.0, **(delivery or {})},
        'effects': effects, 'self': {'mode': self_mode, 'effects': self_effects},
    }


ULTIMATES = [
    ultimate('firestorm', 'fire', 'Firestorm', 'DMG/DOT/AOE', 'targeted_zone',
             [damage(12, 'fire'), {'type': 'modify_temperature', 'amount': 220}, status('obscured', 120),
              {'type': 'apply_force', 'amount': 0.4, 'lift': 0.3}],
             [{'type': 'modify_temperature', 'amount': 60}, status('haste', 300, 3)], 'zone',
             delivery={'radius': 9.0, 'duration_ticks': 320, 'interval_ticks': 12}, order=('arcane', 'fire', 'fire', 'lightning', 'fire')),
    ultimate('deluge', 'water', 'Deluge', 'CC/AOE', 'spray',
             [{'type': 'set_wet', 'duration_ticks': 400}, {'type': 'apply_force', 'amount': 2.6, 'lift': 0.55},
              damage(6, 'water'), {'type': 'extinguish', 'radius': 12}],
             [{'type': 'set_wet', 'duration_ticks': 300}, {'type': 'extinguish', 'radius': 12},
              status('slow_falling', 300)], origin='caster_front', delivery={'radius': 9.0}, order=('earth', 'water', 'water', 'water', 'cold')),
    ultimate('absolute_zero', 'cold', 'Absolute Zero', 'CC/DMG/AOE', 'burst_projectile',
             [{'type': 'modify_temperature', 'amount': -240}, status('chilled', 320, 5), status('rooted', 200),
              status('brittle', 320), damage(9, 'cold')],
             [status('stone_shell', 300, 3), {'type': 'modify_temperature', 'amount': -60}],
             delivery={'burst_radius': 9.0, 'speed': 1.1}, order=('cold', 'water', 'cold', 'cold', 'death')),
    ultimate('thunderstorm', 'lightning', 'Thunderstorm', 'DMG/CHAIN/AOE', 'chain',
             [damage(14, 'lightning'), {'type': 'add_charge', 'amount': 160},
              {'type': 'chain_targets', 'count': 12, 'range': 10, 'amount': 9}],
             [{'type': 'add_charge', 'amount': 40}, status('haste', 300, 3)],
             delivery={'radius': 9.0, 'duration_ticks': 200}, order=('water', 'arcane', 'lightning', 'lightning', 'lightning')),
    ultimate('earthquake', 'earth', 'Earthquake', 'DMG/BREAK/AOE', 'targeted_zone',
             [damage(14, 'earth'), {'type': 'brittle_terrain', 'radius': 9},
              {'type': 'destroy_blocks', 'budget': 24, 'radius': 2.5},
              {'type': 'heave_blocks', 'budget': 60, 'radius': 9, 'lift': .75}, status('rooted', 160), status('brittle', 320),
              {'type': 'apply_force', 'amount': 0.8, 'lift': 0.5}],
             [status('stone_shell', 300, 3), status('rooted', 40)], 'zone',
             delivery={'radius': 9.0, 'duration_ticks': 220, 'interval_ticks': 20}, order=('earth', 'earth', 'fire', 'cold', 'earth')),
    ultimate('font_of_life', 'life', 'Font of Life', 'HEAL/REVIVE/AOE', 'targeted_zone',
             [{'type': 'heal', 'amount': 18}, status('regenerating', 400, 4),
              {'type': 'revive', 'health_fraction': 1.0}, {'type': 'grow_plants', 'budget': 96}],
             [{'type': 'heal', 'amount': 20}, status('regenerating', 400, 4)], 'zone',
             delivery={'radius': 9.0, 'duration_ticks': 400, 'interval_ticks': 15}, order=('water', 'life', 'arcane', 'life', 'life')),
    ultimate('singularity', 'arcane', 'Singularity', 'DMG/CC/AOE', 'targeted_zone',
             [damage(15, 'arcane'), {'type': 'apply_force', 'amount': -2.6, 'lift': 0.25},
              status('arcane_unstable', 300, 4), status('levitating', 140)],
             [status('arcane_unstable', 120, 2), status('levitating', 100), status('slow_falling', 300)], 'zone',
             delivery={'radius': 9.0, 'duration_ticks': 240, 'interval_ticks': 12}, order=('arcane', 'cold', 'death', 'arcane', 'arcane')),
    ultimate('apocalypse', 'death', 'Apocalypse', 'SUMMON/DMG/DEBUFF', 'summon_object',
             [{'type': 'summon_undead', 'entity': 'minecraft:zombie', 'count': 6, 'duration_ticks': 200},
              {'type': 'summon_undead', 'entity': 'minecraft:skeleton', 'count': 6, 'duration_ticks': 200},
              {'type': 'raise_dead', 'count': 8, 'radius': 16, 'duration_ticks': 200},
              status('withering', 300, 4), {'type': 'drain_life', 'amount': 12, 'fraction': 0.8}],
             [{'type': 'summon_undead', 'entity': 'minecraft:zombie', 'count': 6, 'duration_ticks': 200},
              {'type': 'summon_undead', 'entity': 'minecraft:skeleton', 'count': 6, 'duration_ticks': 200},
              {'type': 'raise_dead', 'count': 8, 'radius': 16, 'duration_ticks': 200}],
             'summon', delivery={'object_kind': 'undead', 'radius': 3.0}, order=('death', 'death', 'fire', 'death', 'earth')),
]


def derive(base, element, count):
    data = copy.deepcopy(base)
    stem = base['id'].split(':', 1)[1]
    suffix = str(count) if count > 1 else ''
    data['id'] = 'magicka:' + stem + '_' + element + suffix
    data['formula'] = {**base['formula'], element: base['formula'].get(element, 0) + 1}
    data['name'] = base['name'] + ' · ' + TITLE[element] + (' ×%d' % count if count > 1 else '')
    data['tier'] = 'Generic Fusion'
    data['cast_ticks'] = CAST_TICKS
    data['effects'] = [amplify(node) for node in base['effects']] + copy.deepcopy(BOOST[element])
    self_block = data.setdefault('self', {'mode': 'generic', 'effects': []})
    self_block['effects'] = [amplify(node) for node in self_block.get('effects', [])] + copy.deepcopy(SELF_BOOST[element])
    for block, keys in (('delivery', ('radius', 'burst_radius', 'duration_ticks', 'range')),
                        ('construction', ('budget',))):
        if block in data:
            scaled = amplify({k: v for k, v in data[block].items() if k in keys})
            data[block] = {**data[block], **scaled}
    if 'construction' in data:
        data['construction']['uncapped'] = True
    for key in ('primary', 'self_description'):
        data.pop(key, None)
    return data


def main():
    for path in list(SPELLS.glob('*.json')):
        if sum(json.loads(path.read_text(encoding='utf-8'))['formula'].values()) == 5:
            path.unlink()
    catalog = load_catalog()
    assert len(catalog) == 494, len(catalog)
    base_ids = {data['id'] for data in catalog.values()}
    ultimates = {formula_key(u['formula']): u for u in ULTIMATES}
    written = []
    for combo in itertools.combinations_with_replacement(ELEMENTS, 5):
        counts = {e: combo.count(e) for e in dict.fromkeys(combo)}
        key = formula_key(counts)
        if key in ultimates:
            data = ultimates.pop(key)
        else:
            element = min(counts, key=lambda e: (counts[e], ELEMENTS.index(e)))
            rest = {e: c - (e == element) for e, c in counts.items() if c - (e == element)}
            data = derive(catalog[formula_key(rest)], element, counts[element])
        path = SPELLS / (data['id'].split(':', 1)[1] + '.json')
        assert not path.exists(), path
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        written.append(data['id'])
    assert not ultimates, ultimates
    assert len(written) == 792, len(written)
    index = [i for i in json.loads(INDEX.read_text(encoding='utf-8')) if i in base_ids]
    INDEX.write_text(json.dumps(index + written, indent=2) + '\n', encoding='utf-8')
    print(len(written), 'five-element recipes,', len(index) + len(written), 'total')


main()
