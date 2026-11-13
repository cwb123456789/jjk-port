#!/usr/bin/env python3
"""Copies what the Bedrock addon plays for Sukuna's Dismantle (解) skills into the mod's resources.

usage: build_dismantle_assets.py <dir holding the extracted resource packs> <resources/assets/jujutsuenhancement>

The addon draws every slash with particles (entity animations dismantle.horizontal1/2, .dissect, .wide, .down only fire
particle lists, see animations/sukuna/sukuna_moves.animation.json) and plays a few sounds from the move functions
(sukuna/attacks/dismantles/*.mcfunction, dismantle_land, dissect_land, dismantledown). This script copies
  * the particle definitions and their textures (through build_bedrock_assets, which keeps what is already there),
  * the sound files, registered in sounds.json as bedrock.sukuna.<name>.
"""
import glob
import json
import os
import shutil
import sys

import build_bedrock_assets as bba

PARTICLES = [
    # animation.dismantle.horizontal1/2, forwards2
    'dismantle_attack1', 'dismantle_attack1b', 'dismantle_attack1c', 'dismantle_attack1d', 'dismantle_attack1e',
    # animation.dismantle.dissect / wide / down
    'dismantle_attack_big1', 'dismantle_attack_wide', 'dismantle_down1', 'dismantle_down2', 'dismantle_down3',
    # the move functions
    'dismantle_hit', 'dismantle_slashed', 'dismantle_stars', 'dismantle_star_large', 'cleave_aureole',
    'ground_slam2', 'ground_slam3', 'heavy_impact2', 'flashstep1', 'leap', 'rubble3', 'bounce', 'invert',
    'dirt2', 'dirt3', 'dirt4', 'large_slam1', 'large_slam2', 'large_slam3', 'slam_dust1', 'slam_dust2', 'slam_dust3',
]

SOUNDS = ['slash', 'dismantle', 'slice1', 'kai', 'youvedoneitnow', 'shock1']


def main(src, dst):
    bba.WANT = sorted(set(PARTICLES))
    bba.main(src, dst)
    have = set(os.path.splitext(f)[0] for f in os.listdir(os.path.join(dst, 'bedrock', 'particles')))
    print('missing particles:', [p for p in PARTICLES if p not in have])
    sdir = os.path.join(dst, 'sounds', 'bedrock')
    os.makedirs(sdir, exist_ok=True)
    sj_path = os.path.join(dst, 'sounds.json')
    sj = json.load(open(sj_path, encoding='utf-8')) if os.path.exists(sj_path) else {}
    for n in SOUNDS:
        hits = glob.glob(os.path.join(src, '**', 'sounds', 'main', n + '.ogg'), recursive=True)
        if not hits:
            print('missing sound', n)
            continue
        shutil.copyfile(hits[0], os.path.join(sdir, 'sukuna_' + n + '.ogg'))
        sj['bedrock.sukuna.' + n] = {'category': 'hostile',
                                     'sounds': [{'name': 'jujutsuenhancement:bedrock/sukuna_' + n, 'volume': 1.0}]}
    with open(sj_path, 'w', encoding='utf-8') as fh:
        json.dump(sj, fh, indent=2)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
