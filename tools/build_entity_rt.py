#!/usr/bin/env python3
"""bedrock/entity_rt.json: per behaviour-pack entity what the Bedrock entity runtime interprets: component groups, components, events, properties.
Creature AI (behavior.*, navigation, jumps ...) is left out; knockback_roar is kept.   usage: build_entity_rt.py <BP dir>"""
import glob, json, os, re, sys
bp = sys.argv[1]
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/jujutsuenhancement/bedrock/entity_rt.json')
DROP = ('minecraft:behavior.', 'minecraft:navigation', 'minecraft:movement.', 'minecraft:jump', 'minecraft:can_', 'minecraft:annotation',
        'minecraft:loot', 'minecraft:rideable', 'minecraft:breathable', 'minecraft:nameable', 'minecraft:tick_world', 'minecraft:persistent',
        'minecraft:pushable', 'minecraft:knockback_resistance', 'minecraft:leashable', 'minecraft:equipment', 'minecraft:is_', 'minecraft:fire_immune',
        'minecraft:follow_range', 'minecraft:hurt_on_condition', 'minecraft:attack', 'minecraft:variable_max_auto_step', 'minecraft:body_rotation',
        'minecraft:input_ground', 'minecraft:flying_speed', 'minecraft:underwater_movement', 'minecraft:mob_effect', 'minecraft:collision_box_ignore',
        'minecraft:interact', 'minecraft:addrider', 'minecraft:behavior', 'minecraft:ageable', 'minecraft:healable', 'minecraft:tameable', 'minecraft:trade',
        'minecraft:experience_reward', 'minecraft:shooter', 'minecraft:target_nearby', 'minecraft:home', 'minecraft:preferred_path', 'minecraft:dimension_bound')
KEEP = ('minecraft:behavior.knockback_roar',)


def slim(comps):
    r = {}
    for k, v in comps.items():
        if k in KEEP or not any(k.startswith(d) for d in DROP):
            r[k] = v
    return r


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
    except Exception:
        bad.append(f); continue
    ident = e['description']['identifier']
    d = {}
    c = slim(e.get('components', {}))
    if c: d['c'] = c
    g = {k: slim(v) for k, v in e.get('component_groups', {}).items()}
    g = {k: v for k, v in g.items() if v}
    if g: d['g'] = g
    if e.get('events'): d['e'] = e['events']
    p = e['description'].get('properties')
    if p: d['p'] = p
    defs[ident] = d
os.makedirs(os.path.dirname(out), exist_ok=True)
json.dump(defs, open(out, 'w', encoding='utf-8'), separators=(',', ':'), ensure_ascii=False)
print(len(defs), 'entities', len(bad), 'unreadable', bad, os.path.getsize(out), 'bytes')
