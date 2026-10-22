"""Builds labelled contact sheets from autotest screenshots.

usage: python sheets.py <screenshots dir> <out dir> <prefix> [<prefix> ...]
"""
import glob
import os
import sys

from PIL import Image, ImageDraw


def sheet(files, out, cols=4, w=400, h=225):
    if not files:
        return
    rows = (len(files) + cols - 1) // cols
    s = Image.new('RGB', (cols * w, rows * (h + 14)), 'black')
    d = ImageDraw.Draw(s)
    for i, f in enumerate(files):
        x, y = (i % cols) * w, (i // cols) * (h + 14)
        s.paste(Image.open(f).convert('RGB').resize((w, h)), (x, y + 14))
        d.text((x + 4, y + 1), os.path.basename(f)[3:-4], fill='yellow')
    s.save(out)
    print(out, len(files))


if __name__ == '__main__':
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    for prefix in sys.argv[3:]:
        files = sorted(glob.glob(os.path.join(src, 'sp_' + prefix + '*.png')))
        sheet(files, os.path.join(out, prefix.rstrip('_') + '.png'))
