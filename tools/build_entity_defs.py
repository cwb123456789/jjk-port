#!/usr/bin/env python3
"""bedrock/entities.json: per behaviour-pack entity the families and the collision box (the command engine's @e[family=..], r= and dx= need them).
usage: build_entity_defs.py <BP dir>"""
import glob
import json
import os
import re
import sys

bp = sys.argv[1]
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/jujutsuenhancement/bedrock/entities.json')


def strip_comments(t):
    res = []
    i = 0
    n = len(t)
    instr = False
    while i < n:
        c = t[i]
        if instr:
            res.append(c)
            if c == '\\':
                res.append(t[i + 1]); i += 1
            elif c == '"':
                instr = False
        elif c == '"':
            instr = True; res.append(c)
        elif t.startswith('//', i):
            while i < n and t[i] != '\n':
                i += 1
            continue
        elif t.startswith('/*', i):
            j = t.find('*/', i + 2)
            i = n if j < 0 else j + 2
            continue
        else:
            res.append(c)
        i += 1
    return re.sub(r',(\s*[}\]])', r'\1', ''.join(res))


def load(f):
    t = open(f, encoding='utf-8-sig').read()
    try:
        return json.loads(t)
    except Exception:
        return json.loads(strip_comments(t))


defs = {}
bad = []
for f in glob.glob(os.path.join(bp, 'entities', '**', '*.json'), recursive=True):
    try:
        e = load(f)['minecraft:entity']
    except Exception as ex:
        bad.append(f); continue
    ident = e['description']['identifier']
    comps = dict(e.get('components', {}))
    fam = []
    box = None
    comps_all = [comps] + list(e.get('component_groups', {}).values())
    for c in comps_all:
        tf = c.get('minecraft:type_family')
        if tf:
            for x in tf.get('family', []):
                if x not in fam:
                    fam.append(x)
        cb = c.get('minecraft:collision_box')
        if cb and box is None and isinstance(cb, dict):
            box = [cb.get('width', 0.6), cb.get('height', 1.8)]
    if box is None:
        box = [0.6, 1.8]
    defs[ident] = {'f': fam, 'b': box}
os.makedirs(os.path.dirname(out), exist_ok=True)
json.dump(defs, open(out, 'w', encoding='utf-8'), separators=(',', ':'), ensure_ascii=False)
print(len(defs), 'entities', len(bad), 'unreadable', bad)
