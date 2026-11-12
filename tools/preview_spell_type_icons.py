from pathlib import Path
import re
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
source = (root / 'src/main/java/com/restor/magicka/client/SpellTypeIcons.java').read_text(encoding='utf-8')
glyphs = [re.findall(r'"([.#a-z]+)"', row) for row in source.splitlines() if row.strip().startswith('{"')]
palette = dict(h='#EDE5CD', r='#D39172', b='#A56854', c='#929DA8', g='#87B66B', e='#283528', t='#579A9A', p='#7775AA', m='#B89561', y='#F3DB91', o='#E89A47', s='#C4D4D9', w='#B38A59')
canvas = Image.new('RGB', (9*23, 20), '#10101A')
draw = ImageDraw.Draw(canvas)
for panel, index in enumerate(range(9)):
    glyph = glyphs[index]
    points = [(x, y, pixel) for y, row in enumerate(glyph) for x, pixel in enumerate(row) if pixel != '.']
    min_x, max_x = min(x for x, y, c in points), max(x for x, y, c in points)
    min_y, max_y = min(y for x, y, c in points), max(y for x, y, c in points)
    button_x, button_y = panel*23+2, 2
    draw.rectangle((button_x, button_y, button_x+18, button_y+15), fill='#161824', outline='#625673')
    offset_x = button_x+(19-(max_x-min_x+1))//2-min_x
    offset_y = button_y+(16-(max_y-min_y+1))//2-min_y
    for x, y, pixel in points:
        draw.rectangle((offset_x+x-1, offset_y+y-1, offset_x+x+1, offset_y+y+1), fill='#15111D')
    for x, y, pixel in points:
        draw.point((offset_x+x, offset_y+y), fill=palette.get(pixel, '#FFFFFF'))
canvas.resize((1656, 160), Image.Resampling.NEAREST).save(root / 'test-jars/spell-type-preview.png')
