"""Hand-tuned shapes, radii and mechanics for signature spells, applied on top of the generated recipe."""


def node(kind, **kwargs):
    return dict(type=kind, **kwargs)


def status(name, ticks, strength=1):
    return node('apply_status', status=name, duration_ticks=ticks, strength=strength)


ZONES = {
    'Conductive Flood': (5, 160), 'Conductive Ice': (3.5, 200), 'Flash Freeze Lake': (5, 60), 'Flooded Circuit': (6, 200),
    'Frozen Ground': (4, 160), 'Greater Mend': (5, 100), 'Healing Pool': (2.5, 300), 'Preservation Field': (4, 200),
    'Quicksilt': (3, 200), 'Regenerate': (3, 240), 'Regeneration Mist': (4.5, 200), 'Sanctuary': (5, 240),
    'Shock Field': (4, 160), 'Stasis Field': (4, 160), 'Static Stasis': (5, 200), 'Storm Puddle': (2.5, 300),
    'Thermal Fracture': (2, 60), 'Volcanic Vent': (3, 120), 'Steam Mine': (2.5, 600), 'Guardian Stone': (6, 400),
    'Healing Spring': (4, 300),
}
EXPLOSIONS = {'Arcane Detonation': 3.5, 'Mend Burst': 4, 'Plasma Detonation': 4, 'Thunder Nova': 4.5, 'Arcane Meteor': 4}
BURSTS = {'Firebomb': 2.5, 'Thunderflame': 3, 'Fire Nova Bomb': 5, 'Volatile Magma Core': 3.5, 'Molten Boulder': 2,
          'Magma Boulder': 3, 'Plasma Orb': 3.5, 'Arcane Meteor': 4, 'Deep Freeze': 2.5}


def find(effects, kind):
    return next(e for e in effects if e['type'] == kind)


def apply(recipe):
    name, d, c = recipe['name'], recipe['delivery'], recipe['construction']
    eff = recipe['effects']
    if name in ZONES:
        d['radius'], d['duration_ticks'] = ZONES[name]
    if name in EXPLOSIONS:
        d['radius'] = EXPLOSIONS[name]
    if name in BURSTS:
        d['burst_radius'] = BURSTS[name]

    if name == 'Firebolt':
        recipe['shape'] = 'projectile'; d['speed'] = 1.4
    if name == 'Flame Lance':
        recipe['shape'] = 'beam'; d['beam_width'] = .3
    if name == 'Freezing Ray':
        recipe['shape'] = 'beam'; d['beam_width'] = .2
        find(eff, 'modify_temperature')['amount'] = -60
    if name == 'Deep Freeze':
        recipe['shape'] = 'burst_projectile'
    if name == 'Arcane Bolt':
        recipe['shape'] = 'projectile'; recipe['origin'] = 'caster_front'; d['speed'] = 1.5
    if name == 'Arcane Lance':
        recipe.update(shape='beam', cast_mode='instant'); d.update(beam_width=.35, end_burst=2.5)
        find(eff, 'deal_damage')['amount'] = 10
    if name == 'Water Cannon':
        find(eff, 'apply_force')['amount'] = 1.2
        find(recipe['self']['effects'], 'apply_force')['amount'] = 1.2

    if name == 'Bolt':
        find(eff, 'chain_targets').update(count=3, range=4, amount=3)
    if name == 'Fork':
        find(eff, 'chain_targets').update(count=6, range=5, amount=3)
    if name == 'Thunder Lance':
        d.update(pierce=True, beam_width=.3)
        find(eff, 'deal_damage')['amount'] = 10
        find(eff, 'chain_targets').update(count=3, range=5, amount=4)

    if name == 'Tesla Spire':
        c.update(material='copper', budget=5, interval_ticks=4, lifetime_ticks=600, integrity=12)
        d.pop('conductive', None)
        d.update(tesla=True, radius=8, targets=3, duration_ticks=600, interval_ticks=20)
        recipe['effects'] = [node('create_construction'), node('deal_damage', amount=4, element='lightning'), node('add_charge', amount=35)]
        recipe['self'] = dict(mode='construct', effects=[])
        recipe['tags'] = 'BUILD/DMG/AOE'

    if name == 'Frozen Ground':
        recipe['name'] = 'Hoarfrost'
        d.pop('ice_surface', None)
        recipe['effects'] = [node('modify_temperature', amount=-35), node('deal_damage', amount=2, element='cold'), status('rooted', 20)]
        recipe['self'] = dict(mode='zone', effects=recipe['effects'])
        recipe['tags'] = 'AOE/CC/DMG'
    if name == 'Flash Freeze Lake':
        recipe['effects'] = [e for e in eff if e['type'] != 'apply_force']
        recipe['self']['effects'] = [e for e in recipe['self']['effects'] if e['type'] != 'apply_force']
    if name == 'Shock Field':
        recipe['effects'] = [e for e in eff if e['type'] not in ('set_wet', 'apply_force')]
        recipe['self']['effects'] = recipe['effects']
    if name == 'Regenerate':
        find(eff, 'heal')['amount'] = 2
        find(eff, 'apply_status')['duration_ticks'] = 120

    if name == 'Floating Platform':
        recipe['origin'] = 'caster'
        c.update(under_caster=True, width=3, budget=9, floating=True, material='earth')
        recipe['self'] = dict(mode='construct', effects=[])
    if name == 'Root Ledge':
        recipe.update(name='Climbing Roots', shape='pillar', tags='BUILD/MOVE')
        c.update(budget=8, width=1, material='root', regenerates=True)
        recipe['self'] = dict(mode='construct', effects=[])

    if name == 'Bubble Lift':
        d.update(area=True, radius=3, lift_ticks=60)
        recipe['effects'] = [node('set_wet', duration_ticks=80), status('slow_falling', 160)]
        recipe['self'] = dict(mode='summon', effects=recipe['effects'])
        recipe['origin'] = 'aim_ground'
    if name == 'Buoyant Bubble':
        d['lift_ticks'] = 40
        recipe['effects'] = [node('set_wet', duration_ticks=60), status('slow_falling', 400), status('regenerating', 100, 1)]
        recipe['self'] = dict(mode='summon', effects=recipe['effects'])

    if name == 'Overcharge':
        recipe['effects'] = [status('haste', 100, 3), node('apply_effect', effect='minecraft:jump_boost', duration_ticks=100, strength=2),
                             node('delay', ticks=100, effects=[node('add_charge', amount=60), status('chilled', 60, 2)])]
        recipe['self']['effects'] = recipe['effects']
    if name == 'Haste Surge':
        recipe['effects'] = [status('haste', 300, 2), node('apply_effect', effect='minecraft:haste', duration_ticks=300, strength=2),
                             node('delay', ticks=300, effects=[node('add_charge', amount=30), status('chilled', 40, 1)])]
        recipe['self']['effects'] = recipe['effects']
    return recipe
