#!/usr/bin/env python3
"""Copies the Bedrock particle files (and the textures they use) that the Unlimited Void needs from the
Bedrock resource packs into the mod's resources, so the mod can play them back.

usage: build_bedrock_assets.py <dir with the extracted resource packs> <resources/assets/jujutsuenhancement>
The packs are searched recursively; the extracted pack folders are expected to contain particles/ and textures/.
"""
import glob
import json
import os
import shutil
import sys

WANT = (
    ['infinite_void_whitev1', 'infinite_void_whitev1_small', 'infinite_void_whitev2', 'infinite_void_whitev3',
     'unlimited_enter', 'unlimited_enter2', 'unlimited_enter3',
     'unlimited_enter_backgroundv1', 'unlimited_enter_backgroundv2', 'unlimited_enter_backgroundv3', 'unlimited_enter_backgroundv4',
     'unlimited_eye1', 'unlimited_eye1b', 'unlimited_eye1c', 'unlimited_eye2', 'unlimited_eye3', 'unlimited_eye4', 'unlimited_eye5',
     'domain_smash1', 'domain_smash2', 'domain_smash3']
    + ['unlimited_void%s' % s for s in ['1', '1b', '2', '3', '4', '5', '6', '7', '8', '9', '10', '_background1', '_floor', '_splash',
                                       '_paint1', '_paint2', '_paint3', '_paint4', '_paint5']]
)


def main(src, dst):
    ids = {}
    for f in glob.glob(os.path.join(src, '**', '*.json'), recursive=True):
        if '/particles/' not in f.replace('\\', '/'):
            continue
        try:
            d = json.load(open(f, encoding='utf-8'))
        except Exception:
            continue
        pe = d.get('particle_effect') if isinstance(d, dict) else None
        if not pe:
            continue
        ident = pe['description']['identifier']
        # a file called "... (1).json" is the only one for ds:unlimited_enter2
        if ident not in ids or ('Copy' in ids[ident] or '(1)' in ids[ident]) and not ('Copy' in f or '(1)' in f):
            ids[ident] = f
    out_particles = os.path.join(dst, 'bedrock', 'particles')
    os.makedirs(out_particles, exist_ok=True)
    textures = set()
    for name in WANT:
        f = ids.get('ds:' + name)
        if not f:
            print('missing particle', name)
            continue
        d = json.load(open(f, encoding='utf-8'))
        textures.add(d['particle_effect']['description']['basic_render_parameters']['texture'])
        with open(os.path.join(out_particles, name + '.json'), 'w', encoding='utf-8') as fh:
            json.dump(d, fh, separators=(',', ':'))
    for t in sorted(textures):
        target = os.path.join(dst, 'textures', 'bedrock', t[len('textures/'):] + '.png')
        os.makedirs(os.path.dirname(target), exist_ok=True)
        hits = glob.glob(os.path.join(src, '**', t + '.png'), recursive=True)
        if hits:
            shutil.copyfile(hits[0], target)
        elif t.endswith('wool_colored_white'):
            from PIL import Image
            Image.new('RGBA', (16, 16), (255, 255, 255, 255)).save(target)
            print('generated', t)
        else:
            print('missing texture', t)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
