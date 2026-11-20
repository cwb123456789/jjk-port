#!/usr/bin/env python3
"""Exports what the katana (ds:katana) needs besides its geometry: the player animations, particles and sounds.

usage: build_katana_assets.py <scratchpad> <resources/assets/jujutsuenhancement>
"""
import glob
import json
import os
import shutil
import sys

import build_armor_assets as baa
import build_bedrock_assets as bba

ANIMS = ['animation.katana.idle', 'animation.katana_attack1', 'animation.katana_attack2', 'animation.katana_attack3',
         'animation.katana_attack4', 'animation.katana.ab1', 'animation.katana.ab2', 'animation.katana.ab3',
         # Gojo (limitless) techniques, played on the player
         'animation.lapse_blue1', 'animation.lapse_blue2', 'animation.lapse_pull', 'animation.reversal_red1',
         'animation.reversal_red2', 'animation.reversal_red2b', 'animation.reversal_red3',
         'animation.spatial_blitz', 'animation.spacial_blitz', 'animation.spacial_kick', 'animation.imaginary_purple',
         'animation.hollowpurple', 'animation.200_purple', 'animation.limitless_purple', 'animation.infinite_void',
         'animation.infinite_void_2sec', 'animation.infinite_pressure', 'animation.levitation']
PARTICLES = ['katana_slash1', 'katana_slash2', 'katana_slash3', 'katana_slash4', 'katana_ab1', 'katana_ab2',
             'ground_slam2', 'ground_slam3', 'rapid_cuts1', 'rapid_cuts2', 'rapid_cuts3', 'rapid_cuts4', 'rapid_cuts5',
             'dirt0', 'large_impact1', 'large_impact2', 'lapseblue', 'lapseblue0a', 'lapseblue0b']
SOUNDS = ['slash1', 'sword', 'slice', 'sword_swing1']


def main(src, dst):
    bba.WANT = sorted(set(PARTICLES))
    bba.main(src, dst)
    have = set(os.path.splitext(f)[0] for f in os.listdir(os.path.join(dst, 'bedrock', 'particles')))
    print('missing particles:', [p for p in PARTICLES if p not in have])
    anims = {}
    for pack in baa.PACKS:
        for f in glob.glob(os.path.join(src, pack, '**', '*.json'), recursive=True):
            if '/animations/' not in f.replace('\\', '/'):
                continue
            try:
                d = baa.load(f)
            except Exception:
                continue
            for k, v in (d.get('animations') or {}).items():
                if k in ANIMS and k not in anims:
                    anims[k] = v
    print('missing animations:', [a for a in ANIMS if a not in anims])
    with open(os.path.join(dst, 'bedrock', 'player_anims.json'), 'w', encoding='utf-8') as fh:
        json.dump(anims, fh, separators=(',', ':'))
    sdir = os.path.join(dst, 'sounds', 'bedrock')
    sj_path = os.path.join(dst, 'sounds.json')
    sj = json.load(open(sj_path, encoding='utf-8'))
    for n in SOUNDS:
        hits = glob.glob(os.path.join(src, 'rp1', 'sounds', 'main', n + '.ogg'))
        if not hits:
            print('missing sound', n)
            continue
        shutil.copyfile(hits[0], os.path.join(sdir, 'katana_' + n + '.ogg'))
        sj['bedrock.katana.' + n] = {'category': 'hostile',
                                     'sounds': [{'name': 'jujutsuenhancement:bedrock/katana_' + n, 'volume': 1.0}]}
    with open(sj_path, 'w', encoding='utf-8') as fh:
        json.dump(sj, fh, indent=2)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
