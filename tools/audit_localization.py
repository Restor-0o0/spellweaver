import collections
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
TARGETS = ['', 'port-mc1192', 'port-mc1201', 'port-mc261', 'port-mc262', 'port-mc263']
FORMAT = re.compile(r'%(?:\d+\$)?[sdif]')

for target in TARGETS:
    root = ROOT / target
    languages = {}
    duplicates = {}
    for locale in ('en_us', 'ru_ru'):
        path = root / 'src/main/resources/assets/magicka/lang' / (locale + '.json')
        pairs = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=lambda items: items)
        counts = collections.Counter(key for key, value in pairs)
        duplicates[locale] = [key for key, count in counts.items() if count > 1]
        languages[locale] = dict(pairs)
    en, ru = languages['en_us'], languages['ru_ru']
    missing = {locale: sorted(set(en if locale == 'ru_ru' else ru) - set(values)) for locale, values in languages.items()}
    formats = [key for key in en.keys() & ru.keys() if FORMAT.findall(en[key]) != FORMAT.findall(ru[key])]
    referenced = set()
    for path in (root / 'src/main/java').rglob('*.java'):
        referenced.update(re.findall(r'Component\.translatable\("([a-z0-9_.]+)"\)', path.read_text(encoding='utf-8')))
    absent = sorted(key for key in referenced if key.startswith(('book.magicka.', 'screen.magicka.', 'hud.magicka.')) and key not in en)
    print(json.dumps({'target': target or '1.21.1', 'keys': {key: len(value) for key, value in languages.items()}, 'duplicates': duplicates, 'missing': missing, 'format_mismatch': formats, 'missing_literal_ui_keys': absent}, ensure_ascii=True))
