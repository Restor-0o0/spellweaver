from pathlib import Path
import difflib
import json
import re

ROOT = Path(__file__).resolve().parents[1]
TARGETS = ['port-mc1192', 'port-mc1201', 'port-mc261', 'port-mc262', 'port-mc263']
FILES = ['CastingServer.java', 'SpellRuntime.java', 'GrimoireState.java', 'SpellTags.java', 'client/GrimoireScreen.java', 'client/MagicClient.java', 'client/MagicHud.java', 'client/MagicHudLayout.java', 'client/HudLayoutScreen.java', 'client/HudView.java', 'client/ElementIcons.java', 'client/SpellTypeIcons.java', 'client/SpellVisuals.java']

def normalize(source):
    source = re.sub(r'^import .*;\n', '', source, flags=re.M)
    source = source.replace('net.minecraft.util.Mth.clamp', 'Math.clamp').replace('Mth.clamp', 'Math.clamp')
    source = source.replace('GuiGraphicsExtractor', 'GuiGraphics').replace('ResourceLocation', 'Identifier')
    source = source.replace('pushMatrix()', 'pushPose()').replace('popMatrix()', 'popPose()')
    source = source.replace('.gui.screen()', '.screen').replace('.gui.setScreen(', '.setScreen(').replace('.gui.hud.', '.gui.')
    source = source.replace('.serverLevel()', '.level()').replace('.getLast()', '.getLast()')
    source = source.replace('net.minecraftforge.', 'net.neoforged.neoforge.').replace('net.neoforged.neoforge.eventbus.api.', 'net.neoforged.bus.api.')
    return source

for target in TARGETS:
    lines = []
    for name in FILES:
        relative = Path('src/main/java/com/restor/magicka') / name
        original = normalize((ROOT / relative).read_text(encoding='utf-8'))
        port = normalize((ROOT / target / relative).read_text(encoding='utf-8'))
        lines.extend(difflib.unified_diff(original.splitlines(True), port.splitlines(True), fromfile=name, tofile=target+'/'+name, n=2))
    (ROOT / 'test-jars' / (target+'-parity.diff')).write_text(''.join(lines), encoding='utf-8')
    for locale in ['en_us', 'ru_ru']:
        relative = Path('src/main/resources/assets/magicka/lang') / (locale+'.json')
        original = json.loads((ROOT / relative).read_text(encoding='utf-8'))
        port = json.loads((ROOT / target / relative).read_text(encoding='utf-8'))
        different = [key for key in original.keys() | port.keys() if original.get(key) != port.get(key)]
        print(target, locale, 'different translations:', different)
    relative = Path('src/main/resources')
    def resources(directory):
        result = {}
        for path in directory.rglob('*'):
            if not path.is_file():
                continue
            key = path.relative_to(directory).as_posix()
            key = key.replace('/recipes/', '/recipe/').replace('/advancements/', '/advancement/').replace('/tags/blocks/', '/tags/block/').replace('/tags/items/', '/tags/item/').replace('/tags/entity_types/', '/tags/entity_type/').replace('/forge/', '/neoforge/').replace('data/forge/', 'data/neoforge/')
            value = path.read_bytes()
            if path.suffix == '.json':
                value = json.loads(value)
            result[Path(key)] = value
        return result
    original = resources(ROOT / relative)
    port = resources(ROOT / target / relative)
    different = [str(key) for key in original.keys() | port.keys() if original.get(key) != port.get(key) and key.suffix not in ['.toml']]
    print(target, 'different resources:', different)
