from pathlib import Path
from PIL import Image
from collections import Counter

image = Image.open(Path(__file__).resolve().parents[1] / 'src/main/resources/assets/magicka/textures/gui/elements.png').convert('RGBA')
water = image.crop((32, 0, 64, 32))
print('water contour:', [(min(row), max(row)+1) if row else (0,0) for y in range(32) for row in [[x for x in range(32) if water.getpixel((x,y))[3]]]])
pixels = image.crop((320, 0, 352, 32))
colors = Counter(pixels.getdata())
mapping = {color: str(index) if index < 10 else chr(65+index-10) for index, (color, _) in enumerate(colors.most_common())}
print(mapping)
for y in range(32):
    print(''.join('.' if pixels.getpixel((x,y))[3] == 0 else mapping[pixels.getpixel((x,y))] for x in range(32)))
for y in range(32):
    row = [x for x in range(32) if mapping[pixels.getpixel((x,y))] in ('3', '4', '5', '7', '8')]
    if row:
        print(y, min(row), max(row)+1)
