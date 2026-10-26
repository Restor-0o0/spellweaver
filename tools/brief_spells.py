"""Add short spell descriptions (spell.magicka.<id>.brief / .self_brief) condensed from the full ones.

The book shows the brief text by default and the full text while Shift is held. Run after localize_spells.py.
"""
import json, re
from pathlib import Path

LANG = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/magicka/lang'

RULES = {
    'ru': [
        (r'холод (\d[\d,]*): тушит и замедляет, при 100 накопленного холода — заморозка на 4 с \(мокрым ×2,5\); лужи замерзают', r'холод \1'),
        (r'жар: поджигает на ([\d,]+ с) \(мокрых только высушивает\), размораживает; плавит лёд, жжёт корни', r'поджог \1'),
        (r'заряд (\d[\d,]*): слабость, при 65 заряда — оглушение на 1 с \(мокрым ×2\)', r'заряд \1'),
        (r' \((?:молния по мокрым ×2|огонь по мокрым ×0,5|земля по замёрзшим ×2)(?:, [^)]*)?\)', ''),
        (r'намокание на ([\d,]+ с)(?: и лужа на земле)?', r'намокание \1'),
        (r'иссушение: −[\d,]+ ❤ в секунду, ([\d,]+ с)', r'иссушение \1'),
        (r'регенерация: (\+[\d,]+ ❤) каждые 2 с, ([\d,]+ с)', r'регенерация \1/2 с, \2'),
        (r'арканная нестабильность: −[\d,]+ ❤ каждые 2 с, ([\d,]+ с)', r'нестабильность \1'),
        (r'каменная кожа \(сопротивление (\w+)\), ', r'каменная кожа \1, '),
        (r'хрупкость \(урон землёй ×2\)', 'хрупкость'),
        (r'похищение жизни: ([\d,]+ ❤) урона, вы лечитесь на [\d,]+ ❤', r'похищение \1'),
        (r'затем молния перескакивает дальше: до (\d+) целей подряд, прыжок до ([\d,]+) бл\..*', r'цепь до \1 целей, прыжок \2 бл.'),
        (r'(светящийся огонёк|блок магического светокамня) ([^:]+): .*', r'\1 \2, свет 15'),
        (r'; свет 15, остаются навсегда', ''),
        (r'тушит огонь в радиусе ([\d,]+) бл\.: .*', r'тушит огонь в радиусе \1 бл.'),
        (r'подсвечивает всех, кому вы можете навредить, в радиусе ([\d,]+) бл\.: их видно сквозь стены (.*)', r'подсветка врагов в \1 бл. на \2'),
        (r'рост: до (\d+) блоков рядом — .*', r'рост растений (до \1 бл.)'),
        (r' \(хрупких — вдвое больше\)', ''),
        (r'вздыбливает землю: до (\d+) блоков в радиусе ([\d,]+) бл\..*', r'вздыбливает до \1 бл. земли в \2 бл.'),
        (r'воскрешает ближайшего павшего игрока или существо в радиусе ([\d,]+) бл\. с (\d+%) здоровья', r'воскрешает павшего рядом (\2 здоровья)'),
        (r'оставляет зону радиусом ([\d,]+) бл\. на ([\d,]+ с), которая раз в секунду повторяет эти эффекты', r'оставляет зону \1 бл. на \2'),
        (r'Снаряд летит (?:по дуге )?по взгляду и поражает первую цель\.', 'Снаряд.'),
        (r'Снаряд летит (?:по дуге )?по взгляду и при попадании взрывается радиусом ([\d,]+) бл\.', r'Снаряд, взрыв \1 бл.'),
        (r'Медленная сфера летит (по дуге )?по взгляду и взрывается радиусом ([\d,]+) бл\..*?(?= Эффекты:|$)', r'Медленная сфера, взрыв \2 бл.'),
        (r'Огонёк летит (по дуге )?по взгляду до первого блока или существа\.', 'Огонёк по взгляду.'),
        (r' Вокруг разлетаются (\d+) осколка с теми же эффектами по ближайшим существам\.', r' \1 осколка.'),
        (r' Взрыв происходит через ([\d,]+ с) после попадания\.', r' Взрыв через \1.'),
        (r' В мгновенном режиме (бьёт непрерывно|бьёт дважды в секунду), пока держите\.', ''),
        (r'Мгновенный луч до точки прицела бьёт всех на линии и взрывается в конце радиусом ([\d,]+) бл\.', r'Луч со взрывом \1 бл. в конце.'),
        (r'Луч до точки прицела бьёт всех на линии\.', 'Луч бьёт всех на линии.'),
        (r'Расширяющаяся струя \(до 3 бл\. в ширину\) бьёт всех перед вами, в мгновенном режиме — непрерывно, пока держите:', 'Струя перед вами:'),
        (r'Молния бьёт с руки по прямой до точки прицела и поражает всех на линии, затем уходит цепью от последнего\.', 'Молния пронзает всех на линии.'),
        (r'Молния бьёт с руки в существо в прицеле \(или первое на линии взгляда\)\.', 'Молния в цель.'),
        (r'Взрыв в точке прицела радиусом ([\d,]+) бл\. \(вас не задевает\):', r'Взрыв \1 бл. в точке прицела:'),
        (r'Зона в точке прицела радиусом ([\d,]+) бл\. на ([\d,]+ с)(?: \([^)]*\))?\. Раз в секунду на всех внутри, [^:]*:', r'Зона \1 бл. на \2, каждую секунду:'),
        (r'Зона вокруг вас радиусом ([\d,]+) бл\. на ([\d,]+ с)\. Раз в секунду на всех внутри, [^:]*:', r'Зона \1 бл. вокруг вас на \2, каждую секунду:'),
        (r'Действует на существо в прицеле(?:, в том числе на союзника \(на себя — через Self\))?:', 'На цель:'),
        (r'Мина в точке прицела на ([\d,]+ с): взводится за 1 с и взрывается радиусом ([\d,]+) бл\., когда рядом враг:', r'Мина на \1, взрыв \2 бл.:'),
        (r'Взрыв вокруг вас радиусом ([\d,]+) бл\., задевает и вас:', r'Взрыв \1 бл. вокруг вас (и по вам):'),
        (r'Ставит тотем \(([\d,]+ ❤)\) в точке прицела на ([\d,]+ с)\. Раз в секунду в радиусе ([\d,]+) бл\. на ([^:]+):', r'Тотем на \2, каждую секунду в \3 бл. на \4:'),
        (r'Тотем ставится у ваших ног\. Раз в секунду в радиусе ([\d,]+) бл\. ([^:]+):', r'Тотем у ног, каждую секунду в \1 бл. \2:'),
        (r'Через ([\d,]+ с) над точкой прицела появляется метеор, падает с высоты 10 бл\. и ещё через [\d,]+ с взрывается радиусом ([\d,]+) бл\.:', r'Метеор, взрыв \2 бл.:'),
        (r'Полсекунды за вами остаётся след из зон радиусом 1 бл\. на 2 с; раз в секунду [^:]*:', 'След из зон:'),
        (r', тянется вправо поперёк взгляда', ''),
        (r'; поднимите или опустите взгляд — мост пойдёт вверх или вниз', ''),
        (r', со случайным наклоном и боковыми отростками, останавливается о препятствие.*?(?=\. |$)', ''),
        (r' со случайным наклоном и боковыми отростками, останавливается о препятствие.*?(?=\. |$)', ''),
        (r'\. Материал — ([^,.]+)[^.\n]*', r' (\1)'),
        (r'. Каждый выросший блок действует на [^:]+:', ':'),
        (r' Эффекты:', ''),
    ],
    'en': [
        (r'cold (\d[\d.]*): extinguishes and slows, freezes for 4 s at 100 built-up cold \(×2\.5 on wet\); puddles freeze', r'cold \1'),
        (r'heat: sets on fire for ([\d.]+ s) \(wet targets just dry off\), thaws; melts ice and burns roots', r'burning \1'),
        (r'charge (\d[\d.]*): weakness, stuns for 1 s at 65 charge \(×2 on wet\)', r'charge \1'),
        (r' \((?:lightning ×2 on wet|fire ×0\.5 on wet|earth ×2 on frozen)(?:, [^)]*)?\)', ''),
        (r'soaked for ([\d.]+ s)(?: and leaves a puddle)?', r'soaked \1'),
        (r'withering: −[\d.]+ ❤ per second for ([\d.]+ s)', r'withering \1'),
        (r'regeneration: (\+[\d.]+ ❤) every 2 s for ([\d.]+ s)', r'regeneration \1/2 s, \2'),
        (r'arcane instability: −[\d.]+ ❤ every 2 s for ([\d.]+ s)', r'instability \1'),
        (r'stone skin \(Resistance (\w+)\) for', r'stone skin \1,'),
        (r'brittle \(earth damage ×2\)', 'brittle'),
        (r'life drain: ([\d.]+ ❤) damage, you heal [\d.]+ ❤', r'drains \1'),
        (r'then the lightning jumps onward: up to (\d+) targets in a row, jumps up to ([\d.]+) blocks.*', r'chains to \1 targets, \2-block jumps'),
        (r'(a glowing wisp|a magic glowstone block) ([^:]+): .*', r'\1 \2, light 15'),
        (r'; light level 15, permanent', ''),
        (r'puts out fire within ([\d.]+) blocks: .*', r'puts out fire within \1 blocks'),
        (r'highlights everyone you can harm within ([\d.]+) blocks, visible through walls for (.*)', r'reveals enemies within \1 blocks for \2'),
        (r'growth: up to (\d+) nearby blocks — .*', r'grows plants (up to \1 blocks)'),
        (r' \(twice as many if brittle\)', ''),
        (r'heaves the ground: up to (\d+) blocks within ([\d.]+) blocks.*', r'heaves up to \1 blocks of ground within \2'),
        (r'revives the nearest fallen player or creature within ([\d.]+) blocks with (\d+%) health', r'revives the nearest fallen (\2 health)'),
        (r'leaves a ([\d.]+)-block zone for ([\d.]+ s) that repeats these effects every second', r'leaves a \1-block zone for \2'),
        (r'A projectile flies (?:in an arc )?where you look and strikes the first target\.', 'Projectile.'),
        (r'A projectile flies (?:in an arc )?where you look and explodes in a ([\d.]+)-block radius on hit\.', r'Projectile, \1-block blast.'),
        (r'A slow orb flies (in an arc )?where you look and explodes in a ([\d.]+)-block radius.*?(?= Effects:|$)', r'Slow orb, \2-block blast.'),
        (r'A wisp flies (in an arc )?where you look until it touches a block or creature\.', 'A wisp where you look.'),
        (r' (\d+) shards scatter around with the same effects on creatures they touch\.', r' \1 shards.'),
        (r' The explosion comes ([\d.]+ s) after impact\.', r' Blast after \1.'),
        (r' In instant mode it (keeps firing|strikes twice a second) while held\.', ''),
        (r'An instant beam to the aim point hits everyone along it and explodes at the end in a ([\d.]+)-block radius\.', r'Beam with a \1-block blast at the end.'),
        (r'A beam to the aim point hits everyone along it\.', 'Beam through everyone in line.'),
        (r'A widening stream \(up to 3 blocks wide\) hits everyone in front of you; in instant mode it keeps going while held:', 'Stream in front of you:'),
        (r'Lightning shoots from your hand straight to the aim point, striking everyone along the line, then chains on from the last one\.', 'Lightning pierces everyone in line.'),
        (r'Lightning shoots from your hand into the creature you aim at \(or the first one in your line of sight\)\.', 'Lightning at the target.'),
        (r'An explosion at the aim point with a ([\d.]+)-block radius \(does not hit you\):', r'\1-block blast at the aim point:'),
        (r'A zone at the aim point with a ([\d.]+)-block radius for ([\d.]+ s)(?: \([^)]*\))?\. Every second, on everyone inside[^:]*:', r'\1-block zone for \2, every second:'),
        (r'A zone around you with a ([\d.]+)-block radius for ([\d.]+ s)\. Every second, on everyone inside[^:]*:', r'\1-block zone around you for \2, every second:'),
        (r'Affects the creature you aim at(?:, allies included \(use Self for yourself\))?:', 'On the target:'),
        (r'A mine at the aim point for ([\d.]+ s): arms in 1 s and explodes in a ([\d.]+)-block radius when an enemy comes near:', r'Mine for \1, \2-block blast:'),
        (r'An explosion around you with a ([\d.]+)-block radius, hitting you as well:', r'\1-block blast around you (hits you too):'),
        (r'Places a totem \(([\d.]+ ❤)\) at the aim point for ([\d.]+ s)\. Every second within ([\d.]+) blocks, on ([^:]+):', r'Totem for \2, every second within \3 blocks on \4:'),
        (r'The totem appears at your feet\. Every second within ([\d.]+) blocks, ([^:]+):', r'Totem at your feet, every second within \1 blocks \2:'),
        (r'After ([\d.]+ s) a meteor appears above the aim point, falls from 10 blocks up and [\d.]+ s after landing explodes in a ([\d.]+)-block radius:', r'Meteor, \2-block blast:'),
        (r'For half a second you leave a trail of 1-block zones lasting 2 s; every second, [^:]*:', 'Trail of zones:'),
        (r', extending to the right across your view', ''),
        (r'; look up or down to slope it', ''),
        (r', with a random tilt and side shards, stopping at obstacles.*?(?=\. |$)', ''),
        (r' with a random tilt and side shards, stopping at obstacles.*?(?=\. |$)', ''),
        (r'\. Material: ([^,.]+)[^.\n]*', r' (\1)'),
        (r'. Each block that grows affects [^:]+:', ':'),
        (r' Effects:', ''),
    ],
}


def condense(text, lang):
    for pattern, repl in RULES[lang]:
        text = re.sub(pattern, repl, text, flags=re.IGNORECASE if pattern[0].islower() else 0)
    head, items, tail = [], [], []
    for line in text.split('\n'):
        if line.startswith('• '):
            items.append(line[2:])
        elif line:
            (tail if items else head).append(line)
    if not items:
        return ' '.join(head + tail).strip()
    items = [i if i[1:2].isupper() else i[:1].lower() + i[1:] for i in items]
    lead = ' '.join(head).rstrip(':').rstrip()
    out = (lead.rstrip('.') + ': ' if lead else '') + ', '.join(items)
    out = out[:1].upper() + out[1:]
    return (out.rstrip('.') + '.' + (' ' + ' '.join(tail) if tail else '')).strip()


def bullets(text):
    return [l for l in text.split('\n') if l.startswith('• ')]


def main():
    for name, lang in (('ru_ru.json', 'ru'), ('en_us.json', 'en')):
        path = LANG / name
        raw = path.read_text(encoding='utf-8')
        nl = '\r\n' if '\r\n' in raw else '\n'
        data = json.loads(raw)
        same = 'На вас — те же эффекты.' if lang == 'ru' else 'Same effects, on you.'
        out, count = [], 0
        for line in raw.split(nl):
            if re.match(r'  "spell\.magicka\.[^"]+\.(brief|self_brief)": ', line):
                continue
            out.append(line)
            m = re.match(r'  "(spell\.magicka\.[^"]+)\.self": ', line)
            if not m:
                continue
            base, comma = m[1], line.endswith(',')
            desc, self_text = data.get(base + '.desc', ''), data[base + '.self']
            self_brief = same if bullets(self_text) and bullets(self_text) == bullets(desc) else condense(self_text, lang)
            if not comma:
                out[-1] = line + ','
            out.append(f'  "{base}.brief": {json.dumps(condense(desc, lang), ensure_ascii=False)},')
            out.append(f'  "{base}.self_brief": {json.dumps(self_brief, ensure_ascii=False)}' + (',' if comma else ''))
            count += 1
        path.write_text(nl.join(out), encoding='utf-8', newline='')
        json.loads(path.read_text(encoding='utf-8'))
        print(name, count)


if __name__ == '__main__':
    main()
