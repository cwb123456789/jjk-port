#!/usr/bin/env python3
"""Exports the locators of the addon's player model (models/entity/locator_player.geo.json, geometry.locator_players) to
bedrock/locators.json: {locator: {"bone": <skeleton bone it follows>, "pos": [x, y, z] (Bedrock model pixels)}}.
The skeleton bones are root, body, head, rightArm, leftArm, rightLeg, leftLeg and rightItem (animated by the player animations);
a locator whose bone chain ends at none of them (forward_locator2 ...) is "static" and only follows the entity's turn.

usage: build_locators.py <scratchpad with rp4/models/entity/locator_player.geo.json> <resources/assets/jujutsuenhancement>
"""
import json
import os
import re
import sys

SKELETON = {'root', 'body', 'head', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg', 'rightItem'}


def geometries(path):
    try:
        d = json.loads(re.sub(r'(?m)^\s*//.*$', '', open(path, encoding='utf-8-sig', errors='ignore').read()))
    except Exception:
        return
    if 'minecraft:geometry' in d:
        for g in d['minecraft:geometry']:
            yield g['description']['identifier'], g['bones']
    for k, g in d.items():
        if k.startswith('geometry.') and isinstance(g, dict) and 'bones' in g:
            yield k.split(':')[0], g['bones']


def main(src, dst):
    import glob
    out = {}
    # the addon's player geometry first, then every other model of a humanoid skeleton fills the locators it lacks
    files = [os.path.join(src, 'rp4', 'models', 'entity', 'locator_player.geo.json')]
    files += sorted(glob.glob(os.path.join(src, 'rp*', 'models', '**', '*.json'), recursive=True))
    files += sorted(glob.glob(os.path.join(src, 'rpnew', '*', 'models', '**', '*.json'), recursive=True))
    for f in files:
        for gid, blist in geometries(f):
            bones = {b['name']: b for b in blist}
            if not ({'body', 'head'} <= set(bones) and ('rightArm' in bones or 'locator_rightgroup' in bones)):
                continue
            for b in blist:
                for name, pos in (b.get('locators') or {}).items():
                    if name in out or not isinstance(pos, list) or len(pos) != 3:
                        continue
                    cur, base = b, 'static'
                    seen = 0
                    while cur is not None and seen < 20:
                        if cur['name'] in SKELETON:
                            base = cur['name']
                            break
                        cur = bones.get(cur.get('parent'))
                        seen += 1
                    out[name] = {'bone': base, 'pos': pos}
    os.makedirs(os.path.join(dst, 'bedrock'), exist_ok=True)
    with open(os.path.join(dst, 'bedrock', 'locators.json'), 'w', encoding='utf-8') as fh:
        json.dump(out, fh, separators=(',', ':'))
    print('locators', len(out))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
