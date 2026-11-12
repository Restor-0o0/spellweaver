from pathlib import Path
import re
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = (root / 'src/main/java/com/restor/magicka/client/ElementIcons.java').read_text(encoding='utf-8')
atlas = Image.open(root / 'src/main/resources/assets/magicka/textures/gui/elements.png').convert('RGBA')
for index, name, colors in [(8, 'STEAM', ['#EEF3F2', '#BACDD2', '#7899A7']), (9, 'ICE', ['#D4F7FF', '#7CD7F2', '#3789C4'])]:
    tile = Image.new('RGBA', (32, 32))
    edges = [14,10,8,7,5,4,4,3,2,2,1,1,1,1,0,0,0,0,1,1,1,1,2,2,3,4,4,5,7,8,10,14]
    for y, edge in enumerate(edges):
        for x in range(edge,32-edge):
            tile.putpixel((x,y), (157,175,186,112) if index==8 else (99,185,235,112))
    rows = re.findall(r'"([.hsd]+)"', source.split(name+'_ICON={')[1].split('};')[0])
    assert len(rows)==16 and all(len(row)==16 for row in rows)
    for y,row in enumerate(rows):
        for x,char in enumerate(row):
            if char=='.': continue
            color=colors['hsd'.index(char)]
            for dy in range(2):
                for dx in range(2):
                    tile.putpixel((x*2+dx+(index==9),y*2+dy),tuple(bytes.fromhex(color[1:]))+(255,))
    atlas.paste(tile,(index*32,0))
canvas=Image.new('RGBA', atlas.size, '#171421')
canvas.alpha_composite(atlas)
canvas.resize((1536,128),Image.Resampling.NEAREST).convert('RGB').save(root/'test-jars/element-icons-preview.png')
