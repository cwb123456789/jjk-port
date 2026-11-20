#!/usr/bin/env python3
"""Exports the effects the Bedrock addon plays while a limitless move runs (particles, sounds, camera shakes, screen fades,
impact-frame titles) as a timeline per move: bedrock/gojo/moves.json.

usage: build_gojo_moves.py <dir holding the extracted packs> <resources/assets/jujutsuenhancement> [glyph page]

The addon runs one function per move once per tick with the scoreboard `move` counting down from N, so a line that fires at
`move=m` happens N-m ticks after the click.  Only the lines that run at the caster itself are exported (the effects at
projectiles / hit targets are played from the mod's own entities).

Event rows: [tick, "p", name, local(1)/world(0), a, b, c]  particle at the caster (^left ^up ^forward  or  ~x ~y ~z)
            [tick, "r", name, step, max]                    particle every `step` blocks along the caster's look ray
            [tick, "s", sound, volume, pitch, radius]       sound (radius 0 = everywhere)
            [tick, "k", amp, ticks, radius]                 camera shake (radius -1 = only the caster)
            [tick, "f", in, hold, out, r, g, b, radius]     screen fade (seconds)
            [tick, "i", codepoint]                          impact frame (0 clears it)
"""
import glob
import json
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_bedrock_assets as bba  # noqa: E402

MOVES = {
    'blitz': ('limitless4', 16), 'blitz_area': ('limitless4b', 15), 'kick': ('limitless4c', 36),
    'purple_fast': ('limitless5', 101), 'purple': ('limitless5b', 481), 'purple200': ('limitless5c', 321),
    'purple_unl': ('limitless5d', 481),
}
# vanilla sounds the addon uses by their Bedrock name
VANILLA = {
    'beacon.deactivate': 'minecraft:block.beacon.deactivate', 'beacon.activate': 'minecraft:block.beacon.activate',
    'random.fizz': 'minecraft:block.fire.extinguish', 'random.glass': 'minecraft:block.glass.break',
    'random.explode': 'minecraft:entity.generic.explode', 'random.levelup': 'minecraft:entity.player.levelup',
    'random.orb': 'minecraft:entity.experience_orb.pickup', 'mob.wither.shoot': 'minecraft:entity.wither.shoot',
    'mob.wither.hurt': 'minecraft:entity.wither.hurt', 'mob.wither.break_block': 'minecraft:entity.wither.break_block',
    'mob.wither.spawn': 'minecraft:entity.wither.spawn', 'mob.enderdragon.growl': 'minecraft:entity.ender_dragon.growl',
    'random.anvil_land': 'minecraft:block.anvil.land', 'note.pling': 'minecraft:block.note_block.pling',
    'mob.stormwraith.attack': 'minecraft:entity.generic.explode', 'ambient.weather.thunder': 'minecraft:entity.lightning_bolt.thunder',
    'mob.zombie.metal': 'minecraft:entity.zombie.attack_iron_door', 'random.break': 'minecraft:entity.item.break',
}
COORD = re.compile(r'[~^]-?\d*\.?\d*')


def num(s, d=0.0):
    try:
        return float(s)
    except (TypeError, ValueError):
        return d


def radius(sel):
    """-1: the caster only, 0: everybody, else the radius of the @a selector."""
    if sel.startswith('@s') or sel.startswith('@p'):
        return -1
    m = re.search(r'\br=([\d.]+)', sel)
    return num(m.group(1)) if m else 0


def move_range(sel, n):
    m = re.search(r'\bmove=(\d*)(\.\.)?(\d*)', sel)
    if not m or (m.group(1) == '' and m.group(3) == ''):
        return None
    lo, dots, hi = m.group(1), m.group(2), m.group(3)
    if not dots:
        return int(lo), int(lo)
    return (int(lo) if lo else 0), (int(hi) if hi else n)


def coords(rest):
    toks = COORD.findall(rest.replace(' ', ''))
    if len(toks) < 3:
        return None
    kind = 1 if toks[0][0] == '^' else 0
    if any((t[0] == '^') != (kind == 1) for t in toks[:3]):
        return None
    return kind, [num(t[1:]) for t in toks[:3]]


def parse_line(line):
    """-> (selector of the executor, command) for the lines that run at the caster."""
    m = re.match(r'execute as @s\[(.*?)\] at @s run (.*)$', line)
    if m:
        return m.group(1), m.group(2)
    m = re.match(r'execute as @s\[(.*?)\] run (.*)$', line)
    if m:
        return m.group(1), m.group(2)
    m = re.match(r'(camera|title|playsound|particle|camerashake) .*?@s\[(.*?)\]', line)
    if m and line.startswith(('camera ', 'title ', 'playsound ')):
        return m.group(2), line
    return None


def events_for(path, n, sounds, particles, glyphs):
    ev = []
    for raw in open(path, encoding='utf-8', errors='ignore').read().replace('\r', '').split('\n'):
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        pl = parse_line(line)
        if not pl:
            continue
        sel, cmd = pl
        rng = move_range(sel, n)
        if not rng:
            continue
        ticks = sorted(set(max(1, n - m) for m in range(rng[0], rng[1] + 1)))
        tok = cmd.split()
        if not tok:
            continue
        row = None
        if tok[0] == 'particle' and len(tok) >= 2 and tok[1].startswith('ds:'):
            parts = re.split(r'(?=[~^])', tok[1][3:], maxsplit=1)
            name = parts[0]
            rest = (parts[1] if len(parts) > 1 else '') + ''.join(tok[2:])
            c = coords(rest) if rest else (1, [0.0, 0.0, 0.0])
            if c is None:
                continue
            particles.add(name)
            row = ['p', name, c[0]] + c[1]
        elif tok[0] == 'playsound' and len(tok) >= 4:
            name, selr = tok[1], tok[2]
            i = 3
            if len(tok[3]) < 3:  # spaced coordinates
                i = 6
            else:
                i = 4
            vol = num(tok[i], 1.0) if len(tok) > i else 1.0
            pit = num(tok[i + 1], 1.0) if len(tok) > i + 1 else 1.0
            sounds.add(name)
            row = ['s', name, vol, pit, radius(selr)]
        elif tok[0] == 'camerashake' and len(tok) >= 5:
            row = ['k', num(tok[3]), int(round(num(tok[4]) * 20)), radius(tok[2])]
        elif tok[0] == 'camera' and 'fade' in tok and 'color' in tok:
            i = tok.index('time')
            ci = tok.index('color')
            row = ['f', num(tok[i + 1]), num(tok[i + 2]), num(tok[i + 3]), int(num(tok[ci + 1])), int(num(tok[ci + 2])),
                   int(num(tok[ci + 3])), radius(tok[1])]
        elif tok[0] == 'title' and len(tok) >= 3 and tok[2] == 'title':
            text = cmd.split(' title ', 1)[1] if ' title ' in cmd else ''
            cps = [ord(ch) for ch in text if 0xE300 <= ord(ch) <= 0xE3FF]
            if cps:
                glyphs.add(cps[0] & 0xFF)
                row = ['i', cps[0]]
            elif '§l' in text or not text.strip():
                row = ['i', 0]
        if row is None:
            continue
        for t in ticks:
            ev.append([t] + row)
    ev.sort(key=lambda r: r[0])
    return ev


def load_sound_defs(src):
    defs = {}
    files = glob.glob(os.path.join(src, 'rp*', 'sounds', 'sound_definitions.json')) + \
        glob.glob(os.path.join(src, 'rpnew', '*', 'sound_definitions.json')) + \
        glob.glob(os.path.join(src, 'rpnew', '*', 'sounds', 'sound_definitions.json'))
    for f in sorted(files):
        try:
            j = json.loads(re.sub(r'(?m)^\s*//.*$', '', open(f, encoding='utf-8').read()))
        except Exception:
            continue
        defs.update(j.get('sound_definitions', j))
    return defs


def find_ogg(src, rel):
    base = os.path.basename(rel)
    hits = glob.glob(os.path.join(src, '**', rel + '.ogg'), recursive=True) or \
        glob.glob(os.path.join(src, '**', base + '.ogg'), recursive=True)
    return hits[0] if hits else None


def main(src, dst, glyph_page=None):
    nfun = os.path.join(src, 'ba', '81e3bc74', 'JujutsuAwa', 'functions', 'limitless')
    sounds, particles, glyphs = set(), set(), set()
    out = {}
    for key, (fn, n) in MOVES.items():
        ev = events_for(os.path.join(nfun, fn + '.mcfunction'), n, sounds, particles, glyphs)
        out[key] = {'n': n, 'events': ev}
        print(key, n, len(ev), 'events')
    # blitz.mcfunction: the line of particles along the look ray (once, at move 5 of limitless4)
    blitz_ray = [[11, 'r', 'spatial_blitz2', 0.33, 21.0], [11, 'r', 'spatial_blitz3', 0.33, 21.0], [11, 'r', 'spatial_blitz4', 0.33, 21.0]]
    out['blitz']['events'] += blitz_ray
    particles.update(['spatial_blitz2', 'spatial_blitz3', 'spatial_blitz4', 'heavy_impact1', 'impact'])
    out['blitz']['events'].sort(key=lambda r: r[0])

    # sounds
    defs = load_sound_defs(src)
    sdir = os.path.join(dst, 'sounds', 'bedrock')
    os.makedirs(sdir, exist_ok=True)
    sj_path = os.path.join(dst, 'sounds.json')
    sj = json.load(open(sj_path, encoding='utf-8')) if os.path.exists(sj_path) else {}
    resolved = {}
    for name in sorted(sounds):
        d = defs.get(name)
        if d is None:
            if name in VANILLA:
                resolved[name] = VANILLA[name]
            else:
                print('UNRESOLVED sound', name)
            continue
        files = []
        for s in d.get('sounds', []):
            rel = s if isinstance(s, str) else s.get('name')
            p = find_ogg(src, rel)
            if p:
                safe = 'gojo_mv_' + re.sub(r'[^a-z0-9_]', '_', rel.lower().split('/')[-1])
                shutil.copyfile(p, os.path.join(sdir, safe + '.ogg'))
                files.append({'name': 'jujutsuenhancement:bedrock/' + safe, 'volume': 1.0})
            else:
                print('missing file', name, rel)
        if files:
            key = 'bedrock.gojo.mv_' + re.sub(r'[^a-z0-9_]', '_', name.lower())
            sj[key] = {'category': 'hostile', 'sounds': files}
            resolved[name] = 'jujutsuenhancement:' + key
    with open(sj_path, 'w', encoding='utf-8') as fh:
        json.dump(sj, fh, indent=2)
    for k, v in out.items():
        for e in v['events']:
            if e[1] == 's':
                e[2] = resolved.get(e[2], '')
        v['events'] = [e for e in v['events'] if not (e[1] == 's' and not e[2])]

    gdir = os.path.join(dst, 'bedrock', 'gojo')
    os.makedirs(gdir, exist_ok=True)
    with open(os.path.join(gdir, 'moves.json'), 'w', encoding='utf-8') as fh:
        json.dump(out, fh, separators=(',', ':'))
    with open(os.path.join(gdir, 'moves_particles.txt'), 'w') as fh:
        fh.write('\n'.join(sorted(particles)))
    bba.WANT = sorted(particles)
    bba.main(src, dst)
    have = set(os.path.splitext(f)[0] for f in os.listdir(os.path.join(dst, 'bedrock', 'particles')))
    print('missing particles:', [x for x in sorted(particles) if x not in have])
    print('particles', len(particles), 'sounds resolved', len(resolved), 'glyphs', sorted(hex(g) for g in glyphs))
    return sorted(particles), sorted(glyphs)


if __name__ == '__main__':
    p, g = main(sys.argv[1], sys.argv[2])
