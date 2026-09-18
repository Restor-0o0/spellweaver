import json, glob, collections, re, pathlib

root = pathlib.Path(__file__).resolve().parent.parent.as_posix() + '/'
ru = json.load(open(root + 'src/main/resources/assets/magicka/lang/ru_ru.json', encoding='utf-8'))
order = ['fire', 'water', 'cold', 'lightning', 'earth', 'life', 'arcane', 'death']
el = {'fire': 'Огонь', 'water': 'Вода', 'cold': 'Холод', 'lightning': 'Молния', 'earth': 'Земля', 'life': 'Жизнь', 'arcane': 'Аркана', 'death': 'Смерть'}
key = {e: str(i + 1) for i, e in enumerate(order)}
shape_ru = {k.split('.')[-1]: v for k, v in ru.items() if k.startswith('book.magicka.shape.')}
shape_ru.setdefault('targeted_explosion', 'Взрыв')
origin_ru = {'caster': 'от заклинателя', 'caster_feet': 'от ног', 'caster_front': 'перед заклинателем', 'aim_hit': 'точка/цель прицела',
             'aim_block': 'блок прицела', 'aim_point': 'точка прицела', 'target_entity': 'существо в прицеле', 'under_target': 'под целью',
             'corpse_target': 'душа павшего у прицела', 'aim_surface': 'поверхность прицела', 'aim_ground': 'земля в точке прицела'}
self_ru = {'generic': 'эффекты на себя', 'effects': 'эффекты на себя', 'nova': 'выброс вокруг себя', 'dash': 'рывок', 'zone': 'зона под собой',
           'summon': 'призыв рядом', 'construct': 'постройка под собой', 'meteor': 'метеор на себя'}
tier_ru = {'Signature': 'Именное', 'Family Variant': 'Вариант семейства', 'Generic Fusion': 'Общий сплав'}
tag_ru = {k.split('.')[-1]: v for k, v in ru.items() if k.startswith('book.magicka.tag.')}
CONS = ('platform', 'pillar', 'wall', 'bridge', 'stair', 'ramp', 'spike', 'growth')


def has(d, t):
    return any(n.get('type') == t for n in d.get('effects', []))


def tags(d):
    s = d['tags'].upper()
    sh = d['shape']
    sm = d.get('self', {}).get('mode', 'generic')
    t = []
    if 'DMG' in s or 'DOT' in s or has(d, 'deal_damage'): t.append('damage')
    if 'HEAL' in s or has(d, 'heal') or has(d, 'revive'): t.append('heal')
    if 'CC' in s or 'CTRL' in s or has(d, 'modify_temperature') or has(d, 'add_charge') or any(
            re.fullmatch('rooted|stunned|chilled|frozen|obscured', n.get('status', '')) for n in d.get('effects', [])): t.append('control')
    if sh in CONS or has(d, 'create_construction') or 'ice_surface' in d.get('delivery', {}) or sm == 'construct' or has(d, 'place_glowstone'): t.append('construction')
    if 'UTIL' in s or any(has(d, x) for x in ('apply_effect', 'place_light', 'light_path', 'reveal', 'extinguish')): t.append('utility')
    if 'BREAK' in s or has(d, 'destroy_blocks') or has(d, 'heave_blocks') or has(d, 'brittle_terrain'): t.append('destruction')
    if sh == 'summon_object' or sm == 'summon' or has(d, 'summon_undead') or has(d, 'raise_dead'): t.append('summon')
    if sh == 'dash' or 'MOVE' in s or 'TRAV' in s or sm == 'dash': t.append('movement')
    if sh in ('targeted_zone', 'mine') or has(d, 'spawn_zone') or sm == 'zone': t.append('zone')
    if sh in ('projectile', 'burst_projectile'): t.append('projectile')
    return t or ['utility']


def channel(d):
    m = d.get('cast_mode', 'default')
    if m != 'default': return m == 'channel'
    return d['shape'] in ('beam', 'spray', 'bridge', 'stair', 'ramp', 'growth') or (d['shape'] == 'chain' and sum(d['formula'].values()) == 4)


def ftxt(f): return ' + '.join(el[e] + ('×' + str(f[e]) if f[e] > 1 else '') for e in order if e in f)
def fkeys(f): return ''.join(key[e] * f[e] for e in order if e in f)
def fsort(d): return (sum(d['formula'].values()), [-d['formula'].get(e, 0) for e in order])
def ticks(d): return d.get('cast_ticks', [0, 5, 9, 14, 20, 27][sum(d['formula'].values())])
def secs(t): return ('%g' % (t / 20)).replace('.', ',')
def sid(d): return d['id'].split(':')[1]
def quote(t): return '\n'.join(('> ' + l) if l else '>' for l in t.split('\n'))


def entry(d):
    i = sid(d)
    sh = d['shape']
    if not channel(d): inst = 'срабатывает сразу'
    elif sh in CONS: inst = 'растёт сразу, достраивается сама'
    else: inst = 'поток, пока зажата ЛКМ'
    sm = d.get('self', {}).get('mode', 'generic')
    ct = ticks(d)
    return '\n'.join([
        f"#### {ru['spell.magicka.' + i]}", '',
        f"`{i}` · **{ftxt(d['formula'])}** · клавиши `{fkeys(d['formula'])}` · {tier_ru[d['tier']]}", '',
        f"- Форма: {shape_ru.get(sh, sh)} · источник: {origin_ru.get(d.get('origin', 'caster'), d.get('origin'))} · подготовка: {secs(ct)} с ({ct} т.)",
        f"- Мгновенный режим: {inst} · Self-режим: {self_ru.get(sm, sm)}",
        f"- Теги книги: {', '.join(tag_ru[t] for t in tags(d))} · метки дизайна: `{d['tags']}`", '',
        '**Primary (ЛКМ)**', '', quote(ru['spell.magicka.' + i + '.desc']), '',
        '**Self (ПКМ)**', '', quote(ru['spell.magicka.' + i + '.self']), ''])


spells = sorted((json.load(open(p, encoding='utf-8')) for p in glob.glob(root + 'src/main/resources/data/magicka/magicka_spell/*.json')), key=fsort)
sig = [d for d in spells if d['tier'] == 'Signature']
rest = [d for d in spells if d['tier'] != 'Signature']
cnt = collections.Counter(d['tier'] for d in spells)

out = ['# Magicka — каталог заклинаний', '',
       f"Сгенерирован из `data/magicka/magicka_spell/*.json` и `assets/magicka/lang/ru_ru.json`. Всего формул: **{len(spells)}**: "
       f"именных — {cnt['Signature']}, вариантов семейств — {cnt['Family Variant']}, общих сплавов — {cnt['Generic Fusion']}.", '',
       'Механики, на которые ссылаются описания (мокрость, заряд, накопленный холод, хрупкость, постройки, души и т. д.), описаны в [GAME_DESIGN.md](GAME_DESIGN.md).', '',
       '- ❤ = 2 единицы здоровья. В JSON урон записан в единицах, в описаниях — в сердцах.',
       '- Клавиши: 1 Огонь, 2 Вода, 3 Холод, 4 Молния, 5 Земля, 6 Жизнь, 7 Аркана, 8 Смерть. Порядок ввода не важен.',
       '- Числа указаны при всех характеристиках 100% и `castTimeMultiplier = 1`. Урон по игрокам дополнительно ×0,5 (конфиг).',
       '- Теги книги считаются кодом (`SpellTags`) по меткам дизайна и эффектам; по ним работают вкладки и поиск `#тег`.', '',
       '## Содержание', '',
       '1. [Сводная таблица](#сводная-таблица)',
       '2. [Именные заклинания](#именные-заклинания) — видны в гримуаре',
       '3. [Варианты семейств и общие сплавы](#варианты-семейств-и-общие-сплавы) — только ручной набор', '',
       '## Сводная таблица', '',
       '| Формула | Клавиши | Название | Ранг | Форма | Подготовка |', '|---|---|---|---|---|---|']
for d in spells:
    out.append(f"| {ftxt(d['formula'])} | `{fkeys(d['formula'])}` | {ru['spell.magicka.' + sid(d)]} | {tier_ru[d['tier']]} | {shape_ru.get(d['shape'], d['shape'])} | {secs(ticks(d))} с |")
out += ['', '## Именные заклинания', '', f"{len(sig)} заклинаний с собственным дизайном — только они показываются в гримуаре. Внутри группы — по формуле.", '']
for n in range(1, 5):
    g = [d for d in sig if sum(d['formula'].values()) == n]
    if g: out += [f"### {n} стих. — {len(g)}", ''] + [entry(d) for d in g]
out += ['## Варианты семейств и общие сплавы', '',
        '*Вариант семейства* — модификация именного заклинания той же формы с другими числами или дополнительным эффектом стихии. '
        '*Общий сплав* — собран генератором из эффектов входящих стихий (типовые имена вида «Bolt · Death ×2», «Convergence F / W / C»). '
        'В книге не показываются, но срабатывают при ручном наборе.', '']
for n in range(1, 5):
    g = [d for d in rest if sum(d['formula'].values()) == n]
    if g: out += [f"### {n} стих. — {len(g)}", ''] + [entry(d) for d in g]
open(root + 'docs/SPELLS.md', 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print(len(out), cnt)
