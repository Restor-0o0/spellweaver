import itertools, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPELLS = ROOT / 'src/main/resources/data/magicka/magicka_spell'
INDEX = ROOT / 'src/main/resources/assets/magicka/spell_index.json'
BASE_ELEMENTS = ['fire', 'water', 'cold', 'lightning', 'earth', 'life', 'arcane']
SHORT = {'fire': 'F', 'water': 'W', 'cold': 'C', 'lightning': 'L', 'earth': 'E', 'life': 'Li', 'arcane': 'A'}


def status(name, ticks, strength=1):
    return {'type': 'apply_status', 'status': name, 'duration_ticks': ticks, 'strength': strength}


def damage(amount, element='death'):
    return {'type': 'deal_damage', 'amount': amount, 'element': element}


def summon(entity, count, ticks):
    return {'type': 'summon_undead', 'entity': 'minecraft:' + entity, 'count': count, 'duration_ticks': ticks}


def spell(key, formula, name, tags, shape, primary, self_text, effects, self_effects, self_mode='generic', origin='aim_hit', delivery=None, cast=None):
    data = {
        'id': 'magicka:' + key, 'formula': formula, 'name': name, 'tier': 'Signature', 'tags': tags, 'shape': shape, 'origin': origin,
        'delivery': {'radius': 3.0, 'speed': 0.9, 'duration_ticks': 160, 'interval_ticks': 20, 'gravity': 0, 'burst_radius': 3.0, **(delivery or {})},
        'effects': effects, 'self': {'mode': self_mode, 'effects': self_effects},
        'primary': primary, 'self_description': self_text,
    }
    if cast:
        data['cast_ticks'] = cast
    return data


UNDEAD = {'object_kind': 'undead', 'radius': 1.5}
SIGNATURES = [
    spell('soul_siphon', {'death': 1}, 'Soul Siphon', 'DMG/HEAL/DEBUFF', 'direct_target',
          'Soul Siphon. Вытягивает жизнь из цели: урон и лечение заклинателя, иссушение.',
          'Кровавый договор: немного урона себе, взамен ускорение.',
          [{'type': 'drain_life', 'amount': 4, 'fraction': 0.6}, status('withering', 80)],
          [damage(2), status('haste', 160, 2)]),
    spell('grave_call', {'death': 2}, 'Grave Call', 'SUMMON', 'summon_object',
          'Grave Call. Из земли у прицела встают два зомби-приспешника на 30 секунд.',
          'Зомби встают рядом с заклинателем.',
          [summon('zombie', 2, 600)], [summon('zombie', 2, 600)], 'summon', delivery=UNDEAD),
    spell('raise_dead', {'death': 3}, 'Raise Dead', 'SUMMON/REVIVE', 'summon_object',
          'Raise Dead. Поднимает недавно павших существ у прицела как нежить-приспешников (до 4) и призывает скелета.',
          'Поднимает павших вокруг заклинателя.',
          [{'type': 'raise_dead', 'count': 4, 'radius': 8, 'duration_ticks': 900}, summon('skeleton', 1, 900)],
          [{'type': 'raise_dead', 'count': 4, 'radius': 8, 'duration_ticks': 900}, summon('skeleton', 1, 900)], 'summon', delivery=UNDEAD),
    spell('legion_of_the_dead', {'death': 4}, 'Legion of the Dead', 'SUMMON', 'summon_object',
          'Legion of the Dead. Армия: два зомби, два скелета и два крипера на 45 секунд.',
          'Легион встаёт вокруг заклинателя.',
          [summon('zombie', 2, 900), summon('skeleton', 2, 900), summon('creeper', 2, 900)],
          [summon('zombie', 2, 900), summon('skeleton', 2, 900), summon('creeper', 2, 900)], 'summon', delivery=UNDEAD, cast=26),
    spell('soul_return', {'life': 1, 'death': 1}, 'Soul Return', 'HEAL/REVIVE', 'direct_target',
          'Soul Return. Возвращает душу павшего игрока или существа у прицела с половиной здоровья.',
          'Лечит и снимает иссушение.',
          [{'type': 'revive', 'health_fraction': 0.5}, {'type': 'heal', 'amount': 4}],
          [{'type': 'heal', 'amount': 6}, {'type': 'remove_status', 'status': 'withering'}], delivery={'radius': 4}),
    spell('resurrection', {'life': 2, 'death': 1}, 'Resurrection', 'HEAL/REVIVE', 'direct_target',
          'Resurrection. Полное воскрешение павшего в радиусе 6 блоков и сильное лечение.',
          'Сильное лечение и регенерация.',
          [{'type': 'revive', 'health_fraction': 1.0}, {'type': 'heal', 'amount': 8}, status('regenerating', 160, 2)],
          [{'type': 'heal', 'amount': 10}, status('regenerating', 160, 2)], delivery={'radius': 6}),
    spell('hellfire_skull', {'fire': 1, 'death': 1}, 'Hellfire Skull', 'DMG/DOT', 'projectile',
          'Hellfire Skull. Горящий череп: огненный урон, поджог и иссушение.',
          'Огонь и иссушение на себе.',
          [damage(5, 'fire'), {'type': 'modify_temperature', 'amount': 40}, status('withering', 100, 2)],
          [damage(2, 'fire'), status('withering', 60)], origin='caster_front', delivery={'speed': 1.1, 'burst_radius': 2.0}),
    spell('grave_chill', {'cold': 1, 'death': 1}, 'Grave Chill', 'CC/DOT', 'targeted_zone',
          'Grave Chill. Могильный холод у прицела: замедление, заморозка и иссушение.',
          'Холод и иссушение на себе.',
          [{'type': 'modify_temperature', 'amount': -30}, status('withering', 60), damage(1)],
          [{'type': 'modify_temperature', 'amount': -30}, status('withering', 60)], delivery={'duration_ticks': 120}),
    spell('plague_mist', {'water': 1, 'death': 1}, 'Plague Mist', 'DOT/DEBUFF', 'spray',
          'Plague Mist. Поток чумной воды: мокрость и иссушение.',
          'Иссушение и мокрость на себе.',
          [{'type': 'set_wet', 'duration_ticks': 120}, status('withering', 60), damage(1)],
          [{'type': 'set_wet', 'duration_ticks': 120}, status('withering', 60)], origin='caster_front'),
    spell('soul_storm', {'lightning': 1, 'death': 1}, 'Soul Storm', 'DMG/CC', 'chain',
          'Soul Storm. Цепная молния душ: перескакивает между целями и иссушает.',
          'Заряд и иссушение на себе.',
          [damage(4, 'lightning'), {'type': 'add_charge', 'amount': 40}, status('withering', 60), {'type': 'chain_targets', 'count': 4, 'range': 5, 'amount': 3}],
          [{'type': 'add_charge', 'amount': 40}, status('withering', 60)]),
    spell('grasping_dead', {'earth': 1, 'death': 1}, 'Grasping Dead', 'CC/DOT', 'targeted_zone',
          'Grasping Dead. Руки мертвецов из земли держат врагов на месте и иссушают.',
          'Каменная кожа, но иссушение.',
          [status('rooted', 30), status('withering', 80), damage(2, 'earth')],
          [status('stone_shell', 160, 2), status('withering', 60)], delivery={'duration_ticks': 100, 'radius': 2.5}),
    spell('death_ray', {'arcane': 1, 'death': 1}, 'Death Ray', 'DMG/HEAL', 'beam',
          'Death Ray. Луч смерти: урон, похищение жизни и иссушение, пока держишь.',
          'Нестабильность и иссушение на себе.',
          [damage(3, 'arcane'), {'type': 'drain_life', 'amount': 1, 'fraction': 0.5}, status('withering', 40)],
          [status('arcane_unstable', 80), status('withering', 60)], origin='caster_front'),
    spell('drowned_rising', {'water': 1, 'death': 2}, 'Drowned Rising', 'SUMMON', 'summon_object',
          'Drowned Rising. Призывает двух утопленников.',
          'Утопленники встают рядом с заклинателем.',
          [summon('drowned', 2, 700)], [summon('drowned', 2, 700)], 'summon', delivery=UNDEAD),
    spell('frozen_host', {'cold': 1, 'death': 2}, 'Frozen Host', 'SUMMON/CC', 'summon_object',
          'Frozen Host. Призывает двух зимогоров-лучников, их стрелы замедляют.',
          'Зимогоры встают рядом с заклинателем.',
          [summon('stray', 2, 700)], [summon('stray', 2, 700)], 'summon', delivery=UNDEAD),
    spell('creeper_graves', {'fire': 1, 'death': 2}, 'Creeper Graves', 'SUMMON/DMG/AOE', 'summon_object',
          'Creeper Graves. Три крипера-слуги бегут к врагам и взрываются. Хозяина и других слуг взрыв не ранит.',
          'Криперы встают рядом с заклинателем.',
          [summon('creeper', 3, 700)], [summon('creeper', 3, 700)], 'summon', delivery=UNDEAD),
    spell('wraith_swarm', {'arcane': 1, 'death': 2}, 'Wraith Swarm', 'SUMMON/DMG', 'summon_object',
          'Wraith Swarm. Три летающих духа-вредины проходят сквозь стены к врагам.',
          'Духи появляются рядом с заклинателем.',
          [summon('vex', 3, 400)], [summon('vex', 3, 400)], 'summon', delivery=UNDEAD),
    spell('crypt_husks', {'earth': 1, 'death': 2}, 'Crypt Husks', 'SUMMON', 'summon_object',
          'Crypt Husks. Три кадавра, которые не горят на солнце и накладывают голод.',
          'Кадавры встают рядом с заклинателем.',
          [summon('husk', 3, 700)], [summon('husk', 3, 700)], 'summon', delivery=UNDEAD),
    spell('tomb_collapse', {'earth': 2, 'death': 1}, 'Tomb Collapse', 'DMG/BREAK/AOE', 'targeted_explosion',
          'Tomb Collapse. Обрушивает склеп у прицела: ломает блоки, бьёт и иссушает всех рядом.',
          'Каменная кожа и иссушение.',
          [damage(5, 'earth'), status('withering', 80), {'type': 'destroy_blocks', 'budget': 14, 'radius': 2.5}],
          [status('stone_shell', 160, 2), status('withering', 60)], delivery={'radius': 3.0}),
    spell('doom', {'lightning': 1, 'death': 2}, 'Doom', 'DMG/DEBUFF', 'direct_target',
          'Doom. Метит цель смертью: через 3 секунды она получает огромный урон.',
          'Метка на себе: не делай так.',
          [status('withering', 60), {'type': 'delay', 'ticks': 60, 'effects': [damage(14)]}],
          [{'type': 'delay', 'ticks': 60, 'effects': [damage(6)]}]),
]


def formula_key(counts):
    return tuple(sorted((k, v) for k, v in counts.items() if v))


def load_catalog():
    catalog = {}
    for path in SPELLS.glob('*.json'):
        data = json.loads(path.read_text(encoding='utf-8'))
        catalog[formula_key(data['formula'])] = data
    return catalog


def death_nodes(death):
    return [status('withering', 60 + 20 * death, death), {'type': 'drain_life', 'amount': death, 'fraction': 0.4}]


def generic(base, death):
    data = json.loads(json.dumps(base))
    stem = base['id'].split(':', 1)[1]
    data['id'] = f"magicka:{'death' if death == 1 else f'death{death}'}_{stem}"
    data['formula'] = {**base['formula'], 'death': death}
    data['name'] = base['name'] + (' · Death' if death == 1 else f' · Death ×{death}')
    data['tier'] = 'Generic Fusion'
    data['tags'] = (base.get('tags', '') + '/DEATH').strip('/')
    size = sum(data['formula'].values())
    data['cast_ticks'] = max(base.get('cast_ticks', 0), [0, 5, 9, 14, 20, 27][size])
    data['effects'] = base['effects'] + death_nodes(death)
    self_block = data.setdefault('self', {'mode': 'generic'})
    if 'effects' in self_block:
        self_block['effects'] = self_block['effects'] + [status('withering', 60, death)]
    data['primary'] = base.get('primary', '') + ' Смерть: иссушение и похищение жизни.'
    data['self_description'] = base.get('self_description', '') + ' Смерть: иссушение.'
    return data


def main():
    for old in SPELLS.glob('*.json'):
        if json.loads(old.read_text(encoding='utf-8'))['formula'].get('death'):
            old.unlink()
    catalog = load_catalog()
    assert len(catalog) == 329
    signatures = {formula_key(s['formula']): s for s in SIGNATURES}
    assert len(signatures) == len(SIGNATURES)
    written = []
    for size in range(1, 5):
        for combo in itertools.combinations_with_replacement(BASE_ELEMENTS + ['death'], size):
            death = combo.count('death')
            if not death:
                continue
            counts = {e: combo.count(e) for e in set(combo)}
            key = formula_key(counts)
            if key in signatures:
                data = signatures.pop(key)
            else:
                rest = {e: c for e, c in counts.items() if e != 'death'}
                assert rest, f'pure death formula {counts} needs a signature'
                data = generic(catalog[formula_key(rest)], death)
            path = SPELLS / (data['id'].split(':', 1)[1] + '.json')
            assert not path.exists(), path
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
            written.append(data['id'])
    assert not signatures, signatures
    index = [i for i in json.loads(INDEX.read_text(encoding='utf-8')) if i in {d['id'] for d in catalog.values()}]
    INDEX.write_text(json.dumps(index + written, indent=2) + '\n', encoding='utf-8')
    print(len(written), 'death recipes,', len(index) + len(written), 'total')


main()
