"""Replace placeholder formulas with hand-designed signature spells.

Runs last, after import_spell_catalog.py, add_death_spells.py and localize_spells.py:
each entry takes over a formula that previously held a generic fusion or a family
variant, so the catalog size never changes.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPELLS = ROOT / 'src/main/resources/data/magicka/magicka_spell'
INDEX = ROOT / 'src/main/resources/assets/magicka/spell_index.json'
LANG = ROOT / 'src/main/resources/assets/magicka/lang'


def status(name, ticks, strength=1):
    return {'type': 'apply_status', 'status': name, 'duration_ticks': ticks, 'strength': strength}


def effect(name, ticks, strength=1):
    return {'type': 'apply_effect', 'effect': 'minecraft:' + name, 'duration_ticks': ticks, 'strength': strength}


def damage(amount, element):
    return {'type': 'deal_damage', 'amount': amount, 'element': element}


def spell(key, formula, name, ru, tags, shape, effects, self_effects, primary, self_text,
          ru_primary, ru_self, origin='aim_hit', self_mode='generic', delivery=None, construction=None, cast=14):
    data = {
        'id': 'magicka:' + key, 'formula': formula, 'name': name, 'tier': 'Signature', 'tags': tags,
        'shape': shape, 'origin': origin, 'cast_ticks': cast,
        'delivery': {'radius': 3.0, 'speed': 0.95, 'duration_ticks': 160, 'interval_ticks': 20,
                     'gravity': 0, 'burst_radius': 3.0, **(delivery or {})},
        'construction': {'material': 'earth', 'budget': 3, 'interval_ticks': 4, 'width': 1, 'height': 3,
                         'floating': False, 'integrity': 8, **(construction or {})},
        'effects': effects, 'self': {'mode': self_mode, 'effects': self_effects},
        'primary': primary, 'self_description': self_text, 'balanced': True,
    }
    return data, {name: ru, 'desc': ru_primary, 'self': ru_self}


SPELLS_NEW = [
    spell('sheep', {'earth': 1, 'life': 1, 'death': 1}, 'Sheep', 'Овца', 'CC', 'direct_target',
          [{'type': 'polymorph', 'entity': 'minecraft:sheep', 'duration_ticks': 600}],
          [{'type': 'polymorph', 'entity': 'minecraft:sheep', 'duration_ticks': 200}],
          'Sheep. The target becomes a sheep for 30 s.\n'
          '• Keeps its own health, so a tough mob stays a tough sheep\n'
          '• Any damage that lands breaks the shape and brings the mob back\n'
          '• Works on players too; only bosses are immune',
          'On you: you become a sheep yourself for 10 s. No hands, no spells, just bleating.',
          'Овца. Цель на 30 с превращается в барашка.\n'
          '• ХП остаётся родное, так что жирный моб становится жирным барашком\n'
          '• Любой прошедший урон снимает превращение и возвращает моба\n'
          '• Игроки тоже превращаются, не превращаются только боссы',
          'На себя: сам становишься барашком на 10 с. Ни рук, ни заклинаний, только бекать.'),

    spell('hex', {'water': 1, 'life': 1, 'death': 1}, 'Hex', 'Хекс', 'CC', 'direct_target',
          [{'type': 'polymorph', 'entity': 'minecraft:frog', 'duration_ticks': 600}],
          [{'type': 'polymorph', 'entity': 'minecraft:frog', 'duration_ticks': 200}],
          'Hex. The target becomes a frog for 30 s.\n'
          '• Keeps its own health, so a tough mob stays a tough frog\n'
          '• Any damage that lands breaks the shape and brings the mob back\n'
          '• Works on players too; only bosses are immune',
          'On you: you become a frog yourself for 10 s. No hands, no spells, just croaking.',
          'Хекс. Цель на 30 с превращается в лягушку.\n'
          '• ХП остаётся родное, так что жирный моб становится жирной лягушкой\n'
          '• Любой прошедший урон снимает превращение и возвращает моба\n'
          '• Игроки тоже превращаются, не превращаются только боссы',
          'На себя: сам становишься лягушкой на 10 с. Ни рук, ни заклинаний, только квакать.'),

    spell('curse', {'arcane': 2, 'death': 1}, 'Curse', 'Проклятие', 'DEBUFF/DMG', 'direct_target',
          [status('withering', 220, 2), status('arcane_unstable', 220, 2),
           effect('weakness', 220, 2), effect('slowness', 220, 1), effect('mining_fatigue', 220, 1),
           {'type': 'drain_life', 'amount': 2, 'fraction': 1.0}],
          [effect('strength', 400, 2), effect('speed', 400, 1), status('withering', 160, 1)],
          'Curse. A hex settles on the target instead of a burst of damage.\n'
          '• Withering 11 s and arcane instability 11 s\n'
          '• Weakness II, Slowness and Mining Fatigue for 11 s\n'
          '• Drains 1 ❤ and heals you for the same',
          'On you: a bargain — Strength II and Speed for 20 s, paid for with withering for 8 s.',
          'Проклятие. Вместо вспышки урона на цель садится порча.\n'
          '• Иссушение 11 с и арканная нестабильность 11 с\n'
          '• Слабость II, замедление и усталость 11 с\n'
          '• Вытягивает 1 ❤ и столько же лечит тебя',
          'На себя: сделка — сила II и скорость на 20 с ценой иссушения на 8 с.',
          delivery={'radius': 4.0}),

    spell('thorn_spike', {'earth': 1, 'life': 2}, 'Thorn Spike', 'Терновый шип', 'BUILD/DMG/CC', 'spike',
          [{'type': 'create_construction'}, damage(3, 'earth'), status('rooted', 90, 2)],
          [status('stone_shell', 160, 2), status('regenerating', 160, 2)],
          'Thorn Spike. A spike of living wood bursts out of the ground at the aim point.\n'
          '• Impales for 2 ❤ on the way up\n'
          '• 1.5 ❤ earth damage and roots for 4.5 s\n'
          '• The spike stays and can be climbed',
          'On you: the spike grows from your feet, plus a bark shell and regeneration for 8 s.',
          'Шип. Из земли в точке прицела вырастает шип живого дерева.\n'
          '• Пронзает на 2 ❤ при росте\n'
          '• 1.5 ❤ урона землёй и обездвиживание на 4.5 с\n'
          '• Шип остаётся и по нему можно карабкаться',
          'На себя: шип растёт из-под ног, кора-оболочка и регенерация на 8 с.',
          origin='caster_feet', self_mode='construct',
          construction={'material': 'root', 'budget': 10, 'interval_ticks': 1, 'integrity': 8, 'impale_damage': 4}),

    spell('shatter_bolt', {'fire': 1, 'cold': 1, 'arcane': 1}, 'Shatter Bolt', 'Раскол', 'DMG/SETUP', 'projectile',
          [damage(3, 'cold'), status('brittle', 120, 2),
           {'type': 'conditional', 'condition': 'brittle',
            'effects': [damage(6, 'arcane'), {'type': 'apply_force', 'amount': 0.8, 'lift': 0.3}]},
           {'type': 'brittle_terrain', 'radius': 2.5}],
          [status('brittle', 80, 1), status('chilled', 120, 2)],
          'Shatter Bolt. Makes the target brittle, then shatters whatever is already brittle.\n'
          '• 1.5 ❤ cold damage and brittle for 6 s\n'
          '• A target that was already brittle or frozen takes another 3 ❤ arcane and is thrown back\n'
          '• Weakens stone in a 2.5 block radius',
          'On you: brittle for 4 s and chilled for 6 s.',
          'Раскол. Делает цель хрупкой, а всё уже хрупкое — разбивает.\n'
          '• 1.5 ❤ урона холодом и хрупкость на 6 с\n'
          '• Уже хрупкая или замороженная цель получает ещё 3 ❤ арканом и отлетает\n'
          '• Ослабляет камень в радиусе 2.5 блока',
          'На себя: хрупкость на 4 с и озноб на 6 с.'),

    spell('magnetic_lash', {'earth': 1, 'lightning': 1, 'arcane': 1}, 'Magnetic Lash', 'Магнитный хлыст',
          'CC/DMG', 'projectile',
          [{'type': 'apply_force', 'amount': -1.15, 'lift': 0.25}, {'type': 'add_charge', 'amount': 40},
           damage(2, 'lightning'), status('shocked', 80, 1)],
          [{'type': 'add_charge', 'amount': 20}, status('haste', 200, 2)],
          'Magnetic Lash. Drags the target to you instead of pushing it away.\n'
          '• Pulls the target towards you and lifts it slightly\n'
          '• 1 ❤ lightning damage and charge 40\n'
          '• Shocked for 4 s',
          'On you: charge 20 and Haste II for 10 s.',
          'Магнитный хлыст. Притягивает цель к тебе, а не отталкивает.\n'
          '• Тянет цель к себе и слегка подбрасывает\n'
          '• 1 ❤ урона молнией и заряд 40\n'
          '• Оглушение-шок на 4 с',
          'На себя: заряд 20 и спешка II на 10 с.'),

    spell('overgrowth', {'water': 1, 'earth': 1, 'life': 1}, 'Overgrowth', 'Заросли', 'CC/BUILD/HEAL', 'targeted_zone',
          [{'type': 'grow_plants', 'budget': 16}, status('rooted', 120, 2), {'type': 'set_wet', 'duration_ticks': 160}],
          [{'type': 'grow_plants', 'budget': 16}, status('regenerating', 200, 2)],
          'Overgrowth. A thicket erupts from the ground and holds everything in it.\n'
          '• Plants in a 3.5 block radius grow at once\n'
          '• Everyone inside is rooted for 6 s and soaked\n'
          '• The zone keeps working for 8 s',
          'On you: the thicket grows around you and you regenerate for 10 s.',
          'Заросли. Из земли вырывается чаща и держит всё, что в ней.\n'
          '• Растения в радиусе 3.5 блока вырастают разом\n'
          '• Все внутри обездвижены на 6 с и промокают\n'
          '• Зона работает ещё 8 с',
          'На себя: чаща растёт вокруг тебя, регенерация на 10 с.',
          delivery={'radius': 3.5, 'duration_ticks': 160}),

    spell('soul_chain', {'life': 1, 'arcane': 1, 'death': 1}, 'Soul Chain', 'Цепь душ', 'DMG/HEAL', 'chain',
          [{'type': 'drain_life', 'amount': 3, 'fraction': 0.8}, status('withering', 120, 1),
           {'type': 'chain_targets', 'count': 4, 'range': 5, 'amount': 2}],
          [{'type': 'heal', 'amount': 4}, {'type': 'remove_status', 'status': 'withering'}],
          'Soul Chain. A thread of stolen life runs from target to target.\n'
          '• Drains 1.5 ❤ from the first target and heals you for 1.2 ❤\n'
          '• Withering for 6 s\n'
          '• Jumps to 4 targets within 5 blocks, 1 ❤ each',
          'On you: heals 2 ❤ and clears withering.',
          'Цепь душ. Нить украденной жизни идёт от цели к цели.\n'
          '• Вытягивает 1.5 ❤ из первой цели и лечит тебя на 1.2 ❤\n'
          '• Иссушение на 6 с\n'
          '• Перепрыгивает на 4 цели в радиусе 5 блоков, по 1 ❤',
          'На себя: лечит 2 ❤ и снимает иссушение.',
          delivery={'radius': 5.0}),

    spell('flare', {'fire': 1, 'arcane': 1}, 'Flare', 'Фальшфейер', 'UTIL', 'projectile',
          [{'type': 'place_light'}],
          [{'type': 'place_light'}],
          'Flare. A burning ember you simply throw.\n'
          '• Flies in an arc as far as you can throw it and sticks where it lands\n'
          '• Light level 15, stays until something breaks it\n'
          '• No damage and no marking — just light for a dark cave',
          'On you: the flare drops at your feet and lights the ground around you.',
          'Фальшфейер. Горящий огонёк, который просто кидаешь.\n'
          '• Летит по дуге настолько далеко, насколько бросишь, и остаётся там, где упал\n'
          '• Уровень света 15, висит, пока его не сломают\n'
          '• Никакого урона и подсветки мобов — просто свет для тёмной пещеры',
          'На себя: фальшфейер падает под ноги и освещает землю вокруг.',
          origin='caster_front', cast=9,
          delivery={'radius': 2.0, 'speed': 1.4, 'gravity': 0.03, 'lobbed': True, 'duration_ticks': 200}),

    spell('sheepocalypse', {'earth': 1, 'life': 2, 'death': 2}, 'Sheepocalypse', 'Овцепокалипсис',
          'CC/AOE', 'targeted_zone',
          [{'type': 'polymorph', 'entity': 'minecraft:sheep', 'duration_ticks': 900},
           status('slow_falling', 300, 1)],
          [{'type': 'polymorph', 'entity': 'minecraft:sheep', 'duration_ticks': 300},
           status('slow_falling', 300, 1)],
          'Sheepocalypse. A pasture opens up where you aim and everything in it turns into livestock.\n'
          '• Everyone inside a 6 block radius becomes a sheep for 45 s\n'
          '• The zone keeps working for 10 s, so latecomers get their wool too\n'
          '• Health stays the same and any damage breaks the shape — the flock is still dangerous',
          'On you: the pasture opens around you, you get 15 s of sheep and slow falling.',
          'Овцепокалипсис. В точке прицела открывается пастбище, и всё в нём становится скотом.\n'
          '• Все в радиусе 6 блоков становятся барашками на 45 с\n'
          '• Зона работает ещё 10 с, так что опоздавшие тоже получат шерсть\n'
          '• ХП остаётся прежним, любой урон снимает превращение — отара всё ещё опасна',
          'На себя: пастбище открывается вокруг тебя, 15 с барашком и медленное падение.',
          self_mode='zone', cast=27,
          delivery={'radius': 6.0, 'duration_ticks': 200, 'interval_ticks': 20}),
]


def formula_key(formula):
    return tuple(sorted((k, v) for k, v in formula.items() if v))


def main():
    existing = {}
    for path in SPELLS.glob('*.json'):
        data = json.loads(path.read_text(encoding='utf-8'))
        existing[formula_key(data['formula'])] = (path, data['id'])
    index = json.loads(INDEX.read_text(encoding='utf-8'))
    langs = {name: json.loads((LANG / f'{name}.json').read_text(encoding='utf-8')) for name in ('en_us', 'ru_ru')}

    plan = []
    for data, ru in SPELLS_NEW:
        key = formula_key(data['formula'])
        entry = existing.get(key)
        if entry is None:
            assert data['id'] in index, f'no formula {key} to replace'
            entry = (None, data['id'])
        plan.append((data, ru, *entry))

    # Every removal happens before the first write: a new file can land on the path
    # of a placeholder that a later entry in the plan still points at.
    for data, _, old_path, old_id in plan:
        if old_path is None or old_id == data['id']:
            continue
        old_path.unlink()
        index[index.index(old_id)] = data['id']
        for lang in langs.values():
            for suffix in ('', '.desc', '.self'):
                lang.pop(f'spell.magicka.{old_id.split(":")[1]}{suffix}', None)

    for data, ru, _, _ in plan:
        slug = data['id'].split(':')[1]
        (SPELLS / f'{slug}.json').write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        langs['en_us'][f'spell.magicka.{slug}'] = data['name']
        langs['en_us'][f'spell.magicka.{slug}.desc'] = data['primary']
        langs['en_us'][f'spell.magicka.{slug}.self'] = data['self_description']
        langs['ru_ru'][f'spell.magicka.{slug}'] = ru[data['name']]
        langs['ru_ru'][f'spell.magicka.{slug}.desc'] = ru['desc']
        langs['ru_ru'][f'spell.magicka.{slug}.self'] = ru['self']

    files = sorted(json.loads(path.read_text(encoding='utf-8'))['id'] for path in SPELLS.glob('*.json'))
    assert len(index) == len(set(index)) == 1286, len(index)
    assert files == sorted(index), set(index) ^ set(files)
    INDEX.write_text(json.dumps(index, indent=2) + '\n', encoding='utf-8')
    for name, lang in langs.items():
        (LANG / f'{name}.json').write_text(
            json.dumps(dict(sorted(lang.items())), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(len(SPELLS_NEW), 'signature spells replaced placeholders')


main()
