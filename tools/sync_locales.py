import hashlib
import json
import shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
LANG=Path('src/main/resources/assets/magicka/lang')
TARGETS=['port-mc1192','port-mc1201','port-mc261','port-mc262','port-mc263']
source=ROOT/LANG
files=sorted(source.glob('*.json'))
expected={'en_us','ru_ru','de_de','es_es','fr_fr','pt_br','zh_cn','ja_jp'}
if {path.stem for path in files}!=expected:
    raise ValueError('Expected all eight locales before synchronizing')
base=json.loads((source/'en_us.json').read_text(encoding='utf-8'))
for path in files:
    values=json.loads(path.read_text(encoding='utf-8'))
    if values.keys()!=base.keys():
        raise ValueError('Incomplete locale: '+path.name)
for target in TARGETS:
    destination=ROOT/target/LANG
    for path in files:
        copied=destination/path.name
        shutil.copy2(path,copied)
        assert hashlib.sha256(path.read_bytes()).digest()==hashlib.sha256(copied.read_bytes()).digest()
    shutil.copy2(ROOT/'docs/LOCALIZATION.md',ROOT/target/'docs/LOCALIZATION.md')
    readme=ROOT/target/'README.md'
    text=readme.read_text(encoding='utf-8')
    anchor='*Русское руководство: [docs/GUIDE_RU.md](docs/GUIDE_RU.md)*'
    if anchor in text and 'Languages: English, Russian, German' not in text:
        notice=(ROOT/'README.md').read_text(encoding='utf-8').split('Languages:',1)[1].split('\n\n',1)[0]
        text=text.replace(anchor,anchor+'\n\nLanguages:'+notice)
        readme.write_text(text,encoding='utf-8',newline='\n')
    print(target,':',len(files),'identical locales')
