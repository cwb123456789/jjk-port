#!/usr/bin/env python3
"""Everything the command engine plays: resolves EVERY sound and particle named by the behaviour pack's functions (the fx timelines
only cover the ones that run at the caster): bedrock/sounds_map.json = {bedrock sound name: sound event id}, plus the particle files.
usage: build_sound_map.py <scratchpad> <resources/assets/jujutsuenhancement>"""
import glob
import json
import os
import re
import sys

import build_all_anims as baa_
import build_bedrock_assets as bba


def main(src, dst):
    sounds, particles = set(), set()
    for f in glob.glob(os.path.join(dst, 'bedrock', 'functions', '*.txt')):
        for ln in open(f, encoding='utf-8'):
            m = re.search(r'(?:^|run )playsound (\S+)', ln)
            if m:
                sounds.add(m.group(1))
            m = re.search(r'(?:^|run )stopsound \S+ (\S+)', ln)
            if m:
                sounds.add(m.group(1))
            m = re.search(r'(?:^|run )particle ds:([A-Za-z0-9_]+)', ln)
            if m:
                particles.add(m.group(1))
    resolved, un = baa_.resolve_sounds(src, dst, sounds)
    json.dump(resolved, open(os.path.join(dst, 'bedrock', 'sounds_map.json'), 'w', encoding='utf-8'), separators=(',', ':'), sort_keys=True)
    bba.WANT = sorted(set(bba.WANT) | particles)
    bba.main(src, dst)
    have = set(os.path.splitext(f)[0] for f in os.listdir(os.path.join(dst, 'bedrock', 'particles')))
    print('particles named', len(particles), 'missing', sorted(p for p in particles if p not in have))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
