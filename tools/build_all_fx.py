#!/usr/bin/env python3
"""Exports the timeline (particles, sounds, shakes, fades, impact frames, CAMERA shots) of EVERY function of the Bedrock behaviour pack
that runs at the caster, per counter scoreboard (move / Deleter / cutscene / domain / ...):
bedrock/fx/<folder>.json = {"<file>#<var>": {"n": N, "events": [...]}, ...} plus bedrock/fx/index.json (key -> folder).

usage: build_all_fx.py <scratchpad> <resources/assets/jujutsuenhancement>
"""
import glob
import json
import os
import re
import sys

import build_all_anims as baa_
import build_bedrock_assets as bba
import build_gojo_moves as gm

VARS = ('move', 'Deleter', 'cutscene', 'domain', 'sukunacutscene', 'domainstart')


def main(src, dst):
    root = os.path.join(src, 'ba', '81e3bc74', 'JujutsuAwa', 'functions')
    sounds, particles, glyphs = set(), set(), set()
    out = {}
    nfiles = 0
    for f in sorted(glob.glob(os.path.join(root, '**', '*.mcfunction'), recursive=True)):
        rel = os.path.relpath(f, root).replace('\\', '/')[:-len('.mcfunction')]
        text = open(f, encoding='utf-8', errors='ignore').read()
        if not re.search(r'\b(camera|particle|playsound|camerashake|title)\b', text):
            continue
        folder = rel.split('/')[0] if '/' in rel else '_root'
        for var in VARS:
            nums = []
            for m in re.finditer(r'\b' + var + r'=(\d*)(?:\.\.)?(\d*)', text):
                for g in m.groups():
                    if g:
                        nums.append(int(g))
            if not nums:
                continue
            n = max(nums)
            if n < 1 or n > 20000:
                continue
            conds = ['none']
            if 'tag=blackflashed' in text or 'tag=!blackflashed' in text:
                conds = ['none', 'bf', 'nbf']
            for cond in conds:
                s2, p2, g2 = set(), set(), set()
                ev = gm.events_for(f, n, s2, p2, g2, {'var': var, 'hi': n, 'lo': 0, 'cond': cond, 'strict': True})
                if not ev:
                    continue
                sounds |= s2
                particles |= p2
                glyphs |= g2
                key = rel + '#' + var + ('' if cond == 'none' else '#' + cond)
                out.setdefault(folder, {})[key] = {'n': n, 'events': ev}
                nfiles += 1
    print('timelines', nfiles, 'folders', len(out), 'particles', len(particles), 'sounds', len(sounds), 'glyphs', len(glyphs))
    resolved, _ = baa_.resolve_sounds(src, dst, sounds)
    fdir = os.path.join(dst, 'bedrock', 'fx')
    if os.path.isdir(fdir):
        import shutil
        shutil.rmtree(fdir)
    os.makedirs(fdir)
    index = {}
    total = 0
    for folder, d in out.items():
        for k, v in d.items():
            for e in v['events']:
                if e[1] == 's':
                    e[2] = resolved.get(e[2], '')
            v['events'] = [e for e in v['events'] if not (e[1] == 's' and not e[2])]
            index[k] = folder
        path = os.path.join(fdir, re.sub(r'[^a-z0-9_]', '_', folder.lower()) + '.json')
        with open(path, 'w', encoding='utf-8') as fh:
            json.dump(d, fh, separators=(',', ':'))
        total += os.path.getsize(path)
    with open(os.path.join(fdir, 'index.json'), 'w', encoding='utf-8') as fh:
        json.dump({k: re.sub(r'[^a-z0-9_]', '_', v.lower()) for k, v in index.items()}, fh, separators=(',', ':'))
    print('fx written', total // 1024, 'KB')
    bba.WANT = sorted(set(bba.WANT) | particles)
    bba.main(src, dst)
    # impact-frame glyphs: handled by build_glyph_frames.py (its list is the union of all glyph codes)
    json.dump(sorted(glyphs), open(os.path.join(fdir, 'glyphs.json'), 'w'))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
