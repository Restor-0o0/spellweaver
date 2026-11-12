import collections
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
TARGETS = ['', 'port-mc1192', 'port-mc1201', 'port-mc261', 'port-mc262', 'port-mc263']
FORMAT = re.compile(r'%(?:\d+\$)?[sdif]')
errors = False
expected_locales={path.stem for path in (ROOT/'src/main/resources/assets/magicka/lang').glob('*.json')}
common_path=ROOT/'tools/localization/common.json'
if common_path.exists():
    for original,translations in json.loads(common_path.read_text(encoding='utf-8')).items():
        if len(translations)!=6 or any(not text or FORMAT.findall(original)!=FORMAT.findall(text) or original.count('%%')!=text.count('%%') for text in translations):
            raise ValueError('Invalid manual translation: '+original)

for target in TARGETS:
    root = ROOT / target
    languages = {}
    duplicates = {}
    for path in sorted((root / 'src/main/resources/assets/magicka/lang').glob('*.json')):
        locale = path.stem
        pairs = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=lambda items: items)
        counts = collections.Counter(key for key, value in pairs)
        duplicates[locale] = [key for key, count in counts.items() if count > 1]
        languages[locale] = dict(pairs)
    en = languages['en_us']
    missing = {locale: sorted(set(en) - set(values)) for locale, values in languages.items()}
    extra = {locale: sorted(set(values) - set(en)) for locale,values in languages.items()}
    formats = {locale: [key for key in en.keys() & values.keys() if FORMAT.findall(en[key]) != FORMAT.findall(values[key]) or en[key].count('%%') != values[key].count('%%')] for locale,values in languages.items()}
    invalid = {locale: [key for key,value in values.items() if '\ufffd' in value or re.search(r'⟦\s*\d+\s*⟧|\[\[\s*\d+\s*\]\]',value) or en.get(key) and not value.strip()] for locale,values in languages.items()}
    structure = {locale: [key for key in ['book.magicka.guide','book.magicka.magic'] if sum(line.startswith('# ') for line in en[key].splitlines()) != sum(line.startswith('# ') for line in values.get(key,'').splitlines())] for locale,values in languages.items()}
    untranslated = {locale:[key for key,value in values.items() if locale not in ['en_us','ru_ru'] and len(value)>45 and ' ' in value and value==en.get(key)] for locale,values in languages.items()}
    referenced = set()
    for path in (root / 'src/main/java').rglob('*.java'):
        referenced.update(re.findall(r'Component\.translatable\("([a-z0-9_.]+)"\)', path.read_text(encoding='utf-8')))
    absent = sorted(key for key in referenced if key.startswith(('book.magicka.', 'screen.magicka.', 'hud.magicka.')) and key not in en)
    missing_locales=sorted(expected_locales-set(languages))
    failed = any(any(group.values()) for group in [duplicates,missing,extra,formats,invalid,structure,untranslated]) or bool(absent) or bool(missing_locales)
    errors |= failed
    result={'target':target or '1.21.1','keys':{key:len(value) for key,value in languages.items()},'ok':not failed}
    if failed:
        result.update(duplicates=duplicates,missing=missing,extra=extra,format_mismatch=formats,invalid=invalid,guide_structure=structure,untranslated=untranslated,missing_literal_ui_keys=absent,missing_locales=missing_locales)
    print(json.dumps(result,ensure_ascii=True))

raise SystemExit(1 if errors else 0)
