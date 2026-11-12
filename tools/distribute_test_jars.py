from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGETS = [
    ('1.21.1', '', 'spellweaver-neoforge-1.21.1-1.5.0.jar', 'magicka_test', 'magicka-1.5.0.jar'),
    ('1.19.2', 'port-mc1192', 'spellweaver-forge-1.19.2-1.5.0-secret.jar', 'magicka_test_19_2', None),
    ('1.20.1', 'port-mc1201', 'spellweaver-forge-1.20.1-1.5.0-secret.jar', 'magicka_test_20_', None),
    ('26.1.2', 'port-mc261', 'spellweaver-neoforge-26.1.2-1.5.0-secret.jar', None, None),
    ('26.2', 'port-mc262', 'spellweaver-neoforge-26.2-1.5.0-secret.jar', None, None),
    ('26.3', 'port-mc263', 'spellweaver-neoforge-26.3-1.5.0-secret.jar', 'magicka_test_26_3', None),
]

parser = argparse.ArgumentParser()
parser.add_argument('--copy', action='store_true')
args = parser.parse_args()
versions = Path(os.environ['APPDATA']) / '.minecraft/versions'
print('Test instances:', ', '.join(p.name for p in versions.glob('magicka_test*') if p.is_dir()))
out = ROOT / 'test-jars/current'
report = []
for version, target, filename, instance, installed_name in TARGETS:
    source = ROOT / target / 'build/libs' / filename
    with zipfile.ZipFile(source) as archive:
        assert archive.testzip() is None, source
        icons = archive.read('com/restor/magicka/client/ElementIcons.class')
        assert b'STEAM_ICON' in icons and b'ICE_ICON' in icons, source
        for name in ['MagicUi', 'GrimoireScreen$RuneElement']:
            assert b'com/restor/magicka/client/ElementIcons' in archive.read('com/restor/magicka/client/'+name+'.class'), source
        assert 'com/restor/magicka/client/GrimoireScreen$SpellPrefixTree.class' in archive.namelist(), source
        assert 'com/restor/magicka/client/HudLayoutScreen.class' in archive.namelist(), source
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    item = {'version': version, 'jar': str(source.relative_to(ROOT)), 'sha256': digest, 'instance': instance}
    if args.copy:
        out.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, out / filename)
        if instance:
            mods = versions / instance / 'mods'
            assert mods.is_dir(), mods
            destination = mods / (installed_name or filename)
            previous = []
            for candidate in mods.glob('*.jar'):
                if candidate == destination:
                    continue
                with zipfile.ZipFile(candidate) as archive:
                    if 'com/restor/magicka/Magicka.class' in archive.namelist():
                        previous.append(candidate)
            shutil.copy2(source, destination)
            assert hashlib.sha256(destination.read_bytes()).hexdigest() == digest
            for candidate in previous:
                candidate.unlink()
            item['installed'] = str(destination)
    report.append(item)
    print(version, 'OK', instance or 'no matching instance', digest[:16])
if args.copy:
    (out / 'manifest.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
