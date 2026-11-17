#!/usr/bin/env python3
"""Exports the equipment (attachables) of the Bedrock addon that the mod has ported: geometry, texture, animation.

usage: build_armor_assets.py <scratchpad with rp1/ rp4/ rp8/ rpnew/> <resources/assets/jujutsuenhancement>

For every id the attachable (attachables/**/<id>.json of the resource pack) names the geometry, the texture and the
animations. This writes
  geo/<id>.geo.json                 the geometry in the 1.12.0 layout GeckoLib reads (old 1.8/1.10 files are converted);
  textures/armor/<id>.png           the worn texture, as is (the geometry keeps its declared texture size);
  animations/<id>.animation.json    the animations of the attachable (empty when it has none).

The bones named head / body / leftArm / rightArm / leftLeg / rightLeg follow the matching part of the wearer. GeckoLib
sets those from the wearer's model without parenting, so a tracked bone that sits below another tracked bone (the
addon parents arms and the head to the body) is re-parented to the first untracked ancestor; otherwise the body's turn
would be applied twice. Pivots are absolute in Bedrock, so the pose at rest does not change.
"""
import glob
import json
import os
import shutil
import sys

TRACKED = {'head', 'body', 'leftArm', 'rightArm', 'leftLeg', 'rightLeg', 'hat'}
IDS = ['pigs_crown', 'gojo_cloak', 'kusakabe_fit']
PACKS = ['rpnew', 'rp8', 'rp4', 'rp1']


def load(path):
    """JSON with the '//' comment lines that bridge writes at the top of its files."""
    text = open(path, encoding='utf-8-sig').read()
    text = '\n'.join(line for line in text.split('\n') if not line.lstrip().startswith('//'))
    return json.loads(text)


def find_json(src, pattern_dirs, predicate):
    for pack in PACKS:
        for f in glob.glob(os.path.join(src, pack, '**', '*.json'), recursive=True):
            norm = f.replace('\\', '/')
            if not any(d in norm for d in pattern_dirs):
                continue
            try:
                d = load(f)
            except Exception:
                continue
            hit = predicate(d)
            if hit is not None:
                return f, hit
    return None, None


def attachable(src, ident):
    def pred(d):
        a = d.get('minecraft:attachable') if isinstance(d, dict) else None
        if a and a['description']['identifier'] == 'ds:' + ident:
            return a['description']
        return None
    return find_json(src, ['attachables/', 'attachable/'], pred)[1]


def geometry(src, ident):
    """(file, geometry dict in the 1.12.0 layout) of geometry.<x>; the updated pack is searched first."""
    for pack in PACKS:
        for f in glob.glob(os.path.join(src, pack, '**', '*.json'), recursive=True):
            norm = f.replace('\\', '/')
            if '/animations/' in norm or '/attachables/' in norm or '/render_controllers/' in norm or '/particles/' in norm:
                continue
            try:
                d = load(f)
            except Exception:
                continue
            if not isinstance(d, dict):
                continue
            if 'minecraft:geometry' in d:
                for g in d['minecraft:geometry']:
                    if g['description']['identifier'] == ident:
                        return f, g
            elif ident in d:
                old = d[ident]
                desc = {'identifier': ident,
                        'texture_width': old.get('texturewidth', old.get('texture_width', 64)),
                        'texture_height': old.get('textureheight', old.get('texture_height', 64))}
                for k in ('visible_bounds_width', 'visible_bounds_height', 'visible_bounds_offset'):
                    if k in old:
                        desc[k] = old[k]
                return f, {'description': desc, 'bones': old['bones']}
    return None, None


def untrack(geo):
    bones = {b['name']: b for b in geo['bones']}
    for b in geo['bones']:
        if b['name'] not in TRACKED:
            continue
        parent = b.get('parent')
        while parent in TRACKED:
            parent = bones[parent].get('parent')
        if parent is None:
            b.pop('parent', None)
        else:
            b['parent'] = parent


def texture(src, path):
    for pack_dir in ('rpnew/2e5ccfa5-__', 'rp8/textures', 'rp4/textures', 'rp1/textures'):
        rel = path.replace('textures/', '', 1)
        cand = os.path.join(src, pack_dir, rel + '.png')
        if os.path.exists(cand):
            return cand
    return None


def animations(src, names):
    """{key: animation} for the animation keys of the attachable."""
    out = {}
    for pack in PACKS:
        for f in glob.glob(os.path.join(src, pack, '**', '*.json'), recursive=True):
            if '/animations/' not in f.replace('\\', '/'):
                continue
            try:
                d = load(f)
            except Exception:
                continue
            for k, v in (d.get('animations') or {}).items():
                if k in names and k not in out:
                    out[k] = v
    return out


def main(src, dst):
    for d in ('geo', 'animations', os.path.join('textures', 'armor')):
        os.makedirs(os.path.join(dst, d), exist_ok=True)
    for ident in IDS:
        desc = attachable(src, ident)
        if desc is None:
            print('missing attachable', ident)
            continue
        gid = desc['geometry']['default']
        gfile, geo = geometry(src, gid)
        if geo is None:
            print('missing geometry', ident, gid)
            continue
        untrack(geo)
        with open(os.path.join(dst, 'geo', ident + '.geo.json'), 'w', encoding='utf-8') as fh:
            json.dump({'format_version': '1.12.0', 'minecraft:geometry': [geo]}, fh, indent=1)
        tex = texture(src, desc['textures']['default'])
        if tex is None:
            print('missing texture', ident, desc['textures']['default'])
        else:
            shutil.copyfile(tex, os.path.join(dst, 'textures', 'armor', ident + '.png'))
        anims = animations(src, set((desc.get('animations') or {}).values()))
        with open(os.path.join(dst, 'animations', ident + '.animation.json'), 'w', encoding='utf-8') as fh:
            json.dump({'format_version': '1.8.0', 'animations': anims}, fh, indent=1)
        print(ident, 'geo', os.path.relpath(gfile, src), 'bones', [b['name'] for b in geo['bones']][:6], '... anims', list(anims))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
