#!/usr/bin/env python3
"""Deterministic assets from the Java geometry. Requires JDK 25 and Pillow.
Run: python3 generate_assets.py. Exports the SAME geometry to build/model/*.obj.
"""
from pathlib import Path
import json
import math
import os
import shutil
import subprocess
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / 'src/main/resources/assets/peterwolfs_trimaran_sport_yacht'
OUT = ROOT / 'build/model'
SIZE = 2048
COLORS = [(12, 34, 54), (21, 28, 35), (248, 197, 29), (27, 74, 95),
          (178, 218, 234), (113, 133, 143), (66, 83, 93), (165, 162, 139)]


def geometry():
    classes = ROOT / 'build/model-tools'
    classes.mkdir(parents=True, exist_ok=True)
    home = os.environ.get('JAVA_HOME', '/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home')
    javac = str(Path(home) / 'bin/javac') if Path(home).exists() else 'javac'
    java = str(Path(home) / 'bin/java') if Path(home).exists() else 'java'
    subprocess.run([javac, '-d', str(classes),
                    str(ROOT / 'src/main/java/com/piotrek/trimaranyacht/geometry/TrimaranGeometry.java'),
                    str(ROOT / 'tools/ExportGeometry.java')], check=True)
    tree = json.loads(subprocess.check_output([java, '-cp', str(classes), 'ExportGeometry']))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'geometry.json').write_text(json.dumps(tree, separators=(',', ':')))
    return tree


def walk(part):
    yield part
    for child in part['children']:
        yield from walk(child)


def font(size):
    for path in ['/System/Library/Fonts/Supplemental/Arial Bold.ttf',
                 '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size=size)


def atlas(tree):
    im = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    for material, base in enumerate(COLORS):
        u, v = (material % 4) * 256, (material // 4) * 256
        for y in range(256):
            for x in range(256):
                weave = (x // 2 + y // 2) % 2
                shade = (weave * 5 - 2) if material == 1 else ((x * 17 + y * 11) % 5 - 2)
                alpha = 255
                if material == 6:
                    # Real cutout holes, not a painted opaque gray slab.
                    alpha = 255 if x % 5 == 0 or y % 5 == 0 else 0
                px[u + x, v + y] = (*[max(0, min(255, c + shade)) for c in base], alpha)
    # One continuous original contour-wave graphic across all mainsail strips.
    art = Image.new('RGB', (124, 384), COLORS[0])
    d = ImageDraw.Draw(art)
    for y in range(384):
        d.line((0, y, 123, y), fill=(11 + y // 75, 32 + y // 65, 52 + y // 60))
    for cy in [92, 183, 282]:
        for radius in range(11, 65, 4):
            points = []
            for j in range(181):
                a = j * math.tau / 180
                r = radius * (1 + .15 * math.sin(a * 3 + cy))
                points.append((42 + math.cos(a) * r * .62, cy + math.sin(a) * r))
            d.line(points, fill=(65, 112, 140), width=1)
    d.line((2, 0, 2, 384), fill=(186, 216, 228), width=1)
    d.text((5, 7), '03', font=font(22), fill=(222, 235, 237))
    d.text((5, 33), 'PW', font=font(11), fill=(249, 199, 34))
    d.text((13, 346), 'PETER WOLF', font=font(9), fill=(200, 226, 233))
    d.text((13, 359), 'OCEAN RACING', font=font(7), fill=(126, 174, 193))
    for part in walk(tree):
        for x, y, z, w, h, depth, u, v, mat in part['boxes']:
            assert u + 2 * (w + depth) <= SIZE and v + depth + h <= SIZE
            if u < 1024:
                continue
            w, h, depth, u, v = map(int, (w, h, depth, u, v))
            band_top = int(y + 384)
            strip = art.crop((0, band_top, depth, band_top + h)).convert('RGBA')
            ImageDraw.Draw(im).rectangle((u, v, u + 2 * (w + depth), v + depth + h), fill=(*COLORS[0], 255))
            im.paste(strip.transpose(Image.Transpose.FLIP_LEFT_RIGHT), (u, v + depth))
            im.paste(strip, (u + depth + w, v + depth))
    path = ASSETS / 'textures/entity/trimaran_sport_yacht.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    shutil.copy2(path, OUT / 'trimaran_sport_yacht.png')
    return im


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


IDENTITY = [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def matrix(pose):
    x, y, z, rx, ry, rz = pose
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    my = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    mz = [[cz, -sz, 0, x], [sz, cz, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]
    return matmul(matmul(mz, my), mx)


def transformed_boxes(part, parent=IDENTITY, path=''):
    if part['name'] in ('sail_furled', 'jib_furled') or part['name'].startswith('cleat_knot_'):
        return
    transform = matmul(parent, matrix(part['pose']))
    path += '/' + part['name']
    for box in part['boxes']:
        x, y, z, w, h, d, u, v, mat = box
        corners = [(x, y, z), (x + w, y, z), (x + w, y + h, z), (x, y + h, z),
                   (x, y, z + d), (x + w, y, z + d), (x + w, y + h, z + d), (x, y + h, z + d)]
        vertices = []
        for point in corners:
            vec = [sum(transform[i][j] * (*point, 1)[j] for j in range(4)) for i in range(3)]
            vertices.append((vec[0] / 16, (24 - vec[1]) / 16, -vec[2] / 16))
        yield path, vertices, box
    for child in part['children']:
        yield from transformed_boxes(child, transform, path)


def export_obj(tree):
    # Cube face order follows vanilla box UV net; coordinates exported in metres, Y up, bow +Z.
    faces = [(0, 3, 2, 1), (5, 6, 7, 4), (4, 7, 3, 0), (1, 2, 6, 5), (4, 0, 1, 5), (3, 7, 6, 2)]
    lines = ['# Peterwolf Trimaran — generated from the in-game Java geometry', 'mtllib trimaran.mtl', 'usemtl atlas']
    vi = ti = 1
    count = 0
    for name, vertices, box in transformed_boxes(tree):
        x, y, z, w, h, d, u, v, mat = box
        regions = [(u + d, v + d, w, h), (u + 2 * d + w, v + d, w, h),
                   (u, v + d, d, h), (u + d + w, v + d, d, h), (u + d, v, w, d), (u + d + w, v, w, d)]
        lines.append('g ' + name.replace('/', '_'))
        for p in vertices:
            lines.append('v %.6f %.6f %.6f' % p)
        for face, region in zip(faces, regions):
            a, b, rw, rh = region
            for tu, tv in [(a, b), (a, b + rh), (a + rw, b + rh), (a + rw, b)]:
                lines.append('vt %.8f %.8f' % (tu / SIZE, 1 - tv / SIZE))
            lines.append('f ' + ' '.join(f'{vi + vertex}/{ti + j}' for j, vertex in enumerate(face)))
            ti += 4
        vi += 8
        count += 1
    (OUT / 'trimaran.obj').write_text('\n'.join(lines) + '\n')
    (OUT / 'trimaran.mtl').write_text('newmtl atlas\nKd 1 1 1\nKa 0.2 0.2 0.2\nd 1\nmap_Kd trimaran_sport_yacht.png\nmap_d trimaran_sport_yacht.png\n')
    return count


def icon():
    im = Image.new('RGBA', (128, 128))
    d = ImageDraw.Draw(im)
    d.polygon([(27, 94), (15, 106), (48, 108), (61, 101)], fill=COLORS[0])
    d.polygon([(75, 81), (57, 94), (110, 90), (119, 80)], fill=COLORS[0])
    d.polygon([(18, 91), (11, 97), (96, 103), (113, 93)], fill=COLORS[1])
    d.line((31, 101, 87, 82), fill=COLORS[4], width=3)
    d.line((47, 104, 101, 86), fill=COLORS[4], width=3)
    d.line((53, 94, 53, 12), fill=COLORS[5], width=2)
    d.polygon([(55, 14), (67, 14), (100, 85), (55, 91)], fill=COLORS[0])
    d.polygon([(51, 18), (49, 82), (23, 88)], fill=(24, 57, 77))
    for i in range(4):
        d.arc((58, 36 + i * 10, 86, 65 + i * 10), 70, 270, fill=COLORS[4], width=1)
    d.polygon([(46, 90), (56, 86), (67, 91), (61, 96)], fill=COLORS[2])
    path = ASSETS / 'textures/item/trimaran_sport_yacht.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    im.save(ASSETS / 'icon.png')


if __name__ == '__main__':
    tree = geometry()
    atlas(tree)
    count = export_obj(tree)
    icon()
    print(f'Generated 2048x2048 atlas, icon, and OBJ with {count} boxes in {OUT}')
