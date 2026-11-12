#!/usr/bin/env python3
"""Copies what the Bedrock domain clash plays on the client into the mod's resources.

usage: build_clash_assets.py <dir holding the extracted resource packs> <resources/assets/jujutsuenhancement>

The addon plays each clash visual as an entity animation (animations/domain_stuff/clashing_animations.animation.json):
a looping animation whose only job is to fire a list of particle effects ({@code particle_effects}). This script
  * lists those effects per clash entity (entity/domain_entities/*_clashing.json maps the short names to ds:...),
  * copies the particle definitions and textures through build_bedrock_assets.copy_particles,
  * writes bedrock/clash/effects.json = {kind: [particle names in the order the animation fires them]}.
It also copies the few sound effects the clash functions play (blasted3/4, whoosh3, fuga_shoot, fuga_appear, slashes).
"""
import glob
import json
import os
import shutil
import sys

import build_bedrock_assets as bba

# kind id (used by ClashKind) -> clash entity id
KINDS = {
    'unlimited_void': 'infinite_void_clashing',
    'malevolent_shrine': 'malevolent_shrine_clashing',
    'perfection': 'perfection_clashing',
    'deadly_sentencing': 'deadly_sentencing_clashing',
}

# particles the starting / main / resolution functions spawn directly
EXTRA = ['domainclash', 'domainclash1', 'domainclash2', 'domainclash3',
         'domain_clash_shockwave1', 'domain_clash_shockwave2', 'domain_clash_shockwave3',
         'domain_clash_engulf1', 'clash_smash']

SOUNDS = {  # file in sounds/main -> name in sounds.json
    'blasted3': 'bedrock.clash.blasted3', 'blasted4': 'bedrock.clash.blasted4', 'whoosh3': 'bedrock.clash.whoosh3',
    'fuga_shoot': 'bedrock.clash.fuga_shoot', 'fuga_appear': 'bedrock.clash.fuga_appear',
}


def main(src, dst):
    anim = json.load(open(glob.glob(os.path.join(src, '**', 'clashing_animations.animation.json'), recursive=True)[0],
                          encoding='utf-8'))['animations']
    effects = {}
    want = list(EXTRA)
    for kind, ent in KINDS.items():
        edef = json.load(open(glob.glob(os.path.join(src, '**', ent + '.json'), recursive=True)[0],
                              encoding='utf-8'))['minecraft:client_entity']['description']
        short = edef['particle_effects']
        a = anim['animation.' + ent]
        # the keyframes repeat the same list; take the first one that exists
        first = a['particle_effects'][sorted(a['particle_effects'], key=float)[0]]
        names = []
        for e in (first if isinstance(first, list) else [first]):
            ident = short.get(e['effect'], 'ds:' + e['effect'])
            names.append(ident[3:] if ident.startswith('ds:') else ident)
        effects[kind] = names
        want += names
    bba.WANT = sorted(set(want))
    bba.main(src, dst)
    # drop names whose particle file is not in the pack (the addon itself plays nothing for them)
    have = set(os.path.splitext(f)[0] for f in os.listdir(os.path.join(dst, 'bedrock', 'particles')))
    for k in effects:
        effects[k] = [n for n in effects[k] if n in have]
    out = os.path.join(dst, 'bedrock', 'clash')
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, 'effects.json'), 'w', encoding='utf-8') as fh:
        json.dump(effects, fh, indent=1)
    sdir = os.path.join(dst, 'sounds', 'bedrock')
    os.makedirs(sdir, exist_ok=True)
    sj_path = os.path.join(dst, 'sounds.json')
    sj = json.load(open(sj_path, encoding='utf-8')) if os.path.exists(sj_path) else {}
    for f, name in SOUNDS.items():
        hits = glob.glob(os.path.join(src, '**', 'sounds', 'main', f + '.ogg'), recursive=True)
        if not hits:
            print('missing sound', f)
            continue
        shutil.copyfile(hits[0], os.path.join(sdir, 'clash_' + f + '.ogg'))
        sj[name] = {'category': 'hostile', 'sounds': [{'name': 'jujutsuenhancement:bedrock/clash_' + f, 'volume': 1.0}]}
    with open(sj_path, 'w', encoding='utf-8') as fh:
        json.dump(sj, fh, indent=2)
    print({k: len(v) for k, v in effects.items()})


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
