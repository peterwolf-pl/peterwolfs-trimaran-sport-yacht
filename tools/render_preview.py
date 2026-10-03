#!/usr/bin/env python3
"""Offline geometry contact sheet. Not an in-game screenshot or a visual GameTest."""
from pathlib import Path
import sys
import math
from PIL import Image, ImageDraw
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_assets as assets
import json

FACES = [(0, 3, 2, 1), (5, 6, 7, 4), (4, 7, 3, 0), (1, 2, 6, 5), (4, 0, 1, 5), (3, 7, 6, 2)]


def render(tree, width, height, yaw, pitch, close=False):
    image = Image.new('RGB', (width, height), (226, 233, 233))
    d = ImageDraw.Draw(image)
    c, s = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    def view(v):
        x, y, z = v
        a, depth = c*x + s*z, -s*x + c*z
        return a, cp*y - sp*depth, sp*y + cp*depth
    polygons = []
    points = []
    for name, vertices, box in assets.transformed_boxes(tree):
        if close and any(n in name for n in ['/boom', '/stay_frame', '/shroud', '/vang']):
            continue
        verts = [view(v) for v in vertices]
        for i, face in enumerate(FACES):
            raw = [vertices[j] for j in face]
            if close and min(v[1] for v in raw) > 3:
                continue
            vs = [verts[j] for j in face]
            # Back face culling in camera coordinates.
            ax, ay = vs[1][0]-vs[0][0], vs[1][1]-vs[0][1]
            bx, by = vs[2][0]-vs[0][0], vs[2][1]-vs[0][1]
            if ax*by - ay*bx < 0:
                continue
            points.extend(vs)
            polygons.append((sum(v[2] for v in vs)/4, vs, box, i, name))
    minx,maxx = min(v[0] for v in points),max(v[0] for v in points)
    miny,maxy = min(v[1] for v in points),max(v[1] for v in points)
    scale = min((width-80)/(maxx-minx), (height-70)/(maxy-miny))
    def project(v):
        return ((v[0]-(maxx+minx)/2)*scale+width/2, height/2-(v[1]-(maxy+miny)/2)*scale)
    for depth, vs, box, face, name in sorted(polygons, key=lambda p:p[0]):
        ps = [project(v) for v in vs]
        color = assets.COLORS[int(box[8])]
        shade = [.75,.85,.80,.94,1.15,.62][face]
        color = tuple(min(255,int(c*shade)) for c in color)
        if box[8] == 6:
            # Show the actual open net, not a misleading solid panel.
            for i in range(13):
                t = i/12
                a = tuple(ps[0][j]*(1-t)+ps[1][j]*t for j in range(2))
                b = tuple(ps[3][j]*(1-t)+ps[2][j]*t for j in range(2))
                d.line((a,b),fill=(93,112,119),width=1)
            for i in range(9):
                t = i/8
                a = tuple(ps[0][j]*(1-t)+ps[3][j]*t for j in range(2))
                b = tuple(ps[1][j]*(1-t)+ps[2][j]*t for j in range(2))
                d.line((a,b),fill=(93,112,119),width=1)
        else:
            d.polygon(ps, fill=color)
    return image


if __name__ == '__main__':
    tree=json.loads((assets.OUT/'geometry.json').read_text())
    poster=Image.new('RGB',(1800,1200),(245,246,240))
    poster.paste(render(tree,760,1010,.95,.12),(30,130))
    poster.paste(render(tree,940,480,.80,.65,True),(830,130))
    poster.paste(render(tree,940,480,-.70,.45,True),(830,640))
    d=ImageDraw.Draw(poster)
    d.text((34,22),'PETER WOLF / TRIMARAN 03',font=assets.font(36),fill=(12,34,54))
    d.text((36,76),'16 m VAKA  /  11.25 m BEAM  /  28 m MAST     —     FABRIC 26.3',font=assets.font(19),fill=(52,80,92))
    d.text((48,148),'01 / RIG & SILHOUETTE',font=assets.font(16),fill=(12,34,54))
    d.text((848,148),'02 / THREE HULLS & CARBON AKAS',font=assets.font(16),fill=(12,34,54))
    d.text((848,658),'03 / WORKING COCKPIT & FOILS',font=assets.font(16),fill=(12,34,54))
    d.text((36,1162),'OFFLINE GEOMETRY PREVIEW — flat materials; see OBJ + atlas for the complete livery. Not an in-game capture.',font=assets.font(16),fill=(75,91,95))
    poster.save(assets.OUT/'geometry-preview.png')
    print(assets.OUT/'geometry-preview.png')
