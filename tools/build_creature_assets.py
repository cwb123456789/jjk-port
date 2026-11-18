#!/usr/bin/env python3
"""Exports the creatures of the Bedrock addon that the mod has ported: geometry, skin, animation, names.

usage: build_creature_assets.py <scratchpad with rp1/ rp4/ rp8/ rpnew/> <resources/assets/jujutsuenhancement>

For every id the client entity file (rp1/entity/**/<id>.json) names the geometry, the skin and the animations. This writes
  geo/<id>.geo.json, textures/entity/<id>.png, animations/<id>.animation.json  (only the animations the entity plays)
and the names (entity.jujutsuenhancement.<id>, item.jujutsuenhancement.<id>_spawn_egg) from the addon's texts/*.lang.
"""
import glob
import json
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(__file__))
from build_armor_assets import load, geometry, animations, PACKS  # noqa: E402

CREATURES = ['flyhead']


def client_entity(src, ident):
    for f in glob.glob(os.path.join(src, 'rp1', 'entity', '**', '*.json'), recursive=True):
        if ' - Copy' in f:
            continue
        try:
            d = load(f)
        except Exception:
            continue
        ce = d.get('minecraft:client_entity')
        if ce and ce['description']['identifier'] == 'ds:' + ident:
            return ce['description']
    return None


def texture(src, path):
    for pack in ('rpnew/2e5ccfa5-__', 'rp8/textures', 'rp4/textures', 'rp1/textures'):
        cand = os.path.join(src, pack, path.replace('textures/', '', 1) + '.png')
        if os.path.exists(cand):
            return cand
    return None


def lang_lines(src, name):
    out = {}
    for pack in ('rpnew', 'rp8', 'rp4', 'rp1'):
        for f in glob.glob(os.path.join(src, pack, '**', 'texts', name), recursive=True) + \
                 glob.glob(os.path.join(src, pack, '**', name), recursive=True):
            for line in open(f, encoding='utf-8-sig').read().split('\n'):
                if '=' in line and not line.startswith('#'):
                    k, v = line.split('=', 1)
                    out.setdefault(k.strip(), re.sub(r'\s*#.*$', '', v.rstrip('\r')) if False else v.rstrip('\r'))
    return out


def main(src, dst):
    for d in ('geo', 'animations', os.path.join('textures', 'entity')):
        os.makedirs(os.path.join(dst, d), exist_ok=True)
    zh = lang_lines(src, 'zh_CN.lang')
    en = lang_lines(src, 'en_US.lang')
    langs = {}
    for lang in ('zh_cn', 'en_us'):
        path = os.path.join(dst, 'lang', lang + '.json')
        langs[lang] = json.load(open(path, encoding='utf-8'))
    for ident in CREATURES:
        desc = client_entity(src, ident)
        if desc is None:
            print('missing client entity', ident)
            continue
        gfile, geo = geometry(src, desc['geometry']['default'])
        with open(os.path.join(dst, 'geo', ident + '.geo.json'), 'w', encoding='utf-8') as fh:
            json.dump({'format_version': '1.12.0', 'minecraft:geometry': [geo]}, fh, indent=1)
        tex = texture(src, desc['textures']['default'])
        shutil.copyfile(tex, os.path.join(dst, 'textures', 'entity', ident + '.png'))
        anims = animations(src, set(desc['animations'].values()))
        with open(os.path.join(dst, 'animations', ident + '.animation.json'), 'w', encoding='utf-8') as fh:
            json.dump({'format_version': '1.8.0', 'animations': anims}, fh, indent=1)
        for lang, texts in (('zh_cn', zh), ('en_us', en)):
            name = texts.get('entity.ds:' + ident + '.name')
            egg = texts.get('item.spawn_egg.entity.ds:' + ident + '.name', name)
            if name:
                langs[lang]['entity.jujutsuenhancement.' + ident] = name
            if egg:
                langs[lang]['item.jujutsuenhancement.' + ident + '_spawn_egg'] = egg
        print(ident, os.path.relpath(gfile, src), list(anims), desc.get('spawn_egg'))
    for lang, data in langs.items():
        with open(os.path.join(dst, 'lang', lang + '.json'), 'w', encoding='utf-8') as fh:
            json.dump(data, fh, ensure_ascii=False, indent=2)
            fh.write('\n')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
