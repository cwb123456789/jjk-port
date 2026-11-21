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
    'infinity': ('limitless1', 15), 'pressure': ('limitless1b', 81), 'levitate': ('limitless1c', 15),
}
# Yuji (functions/yuji): key -> (function, N, options). cond: which lines (none = unconditional, nbf = tag=!blackflashed, bf = tag=blackflashed);
# hi: the move value at which the timeline is started by the server (ticks = hi - move).
YUJI = {
    'sukuna_chant': ('../sukuna/attacks/worldslash/chant', 21, {'cond': 'none'}),
    'sukuna_wcs': ('../sukuna/attacks/worldslash/wcs1', 31, {'cond': 'none'}),
    'sukuna_ws1': ('../sukuna/attacks/worldslash/world_slash1', 136, {'cond': 'none'}),
    'sukuna_ws2': ('../sukuna/attacks/worldslash/world_slash2', 136, {'cond': 'none'}),
    'sukuna_cleave': ('../sukuna/attacks/cleaves/cleave', 11, {'cond': 'none'}),
    'sukuna_counter': ('../sukuna/attacks/cleaves/cleave_counter', 16, {'cond': 'none'}),
    'sukuna_layered': ('../sukuna/attacks/cleaves/layered_cleave', 26, {'cond': 'none'}),
    'sukuna_spider': ('../sukuna/attacks/cleaves/spiderweb', 41, {'cond': 'none'}),
    'sukuna_carve': ('../sukuna/attacks/cleaves/carving_combo', 191, {'cond': 'none'}),
    'sukuna_ab1': ('../sukuna/attacks/dismantles/ab1', 11, {'cond': 'none'}),
    'sukuna_ab1b': ('../sukuna/attacks/dismantles/ab1b', 66, {'cond': 'none'}),
    'sukuna_ab1d': ('../sukuna/attacks/dismantles/ab1d', 36, {'cond': 'none'}),
    'sukuna_ab1e': ('../sukuna/attacks/dismantles/ab1e', 41, {'cond': 'none'}),
    'sukuna_ab2': ('../sukuna/attacks/dismantles/ab2', 31, {'cond': 'none'}),
    'sukuna_ab2b': ('../sukuna/attacks/dismantles/ab2b', 13, {'cond': 'none'}),
    'sukuna_ab2d': ('../sukuna/attacks/dismantles/ab2d', 31, {'cond': 'none'}),
    'sukuna_ab2e': ('../sukuna/attacks/dismantles/ab2e', 66, {'cond': 'none'}),
    # 竈 (sukuna4): the charge, the four forms, the arrow's explosion (fuga_arrow1, by its Deleter timer) and the scythe's grab
    'sukuna_fuga': ('../sukuna/attacks/fuga/divine_flames', 56, {'cond': 'none', 'hi': 56, 'lo': 11}),
    'sukuna_fuga_arrow': ('../sukuna/attacks/fuga/divine_flames', 1001, {'cond': 'none', 'hi': 1001, 'lo': 820}),
    'sukuna_fuga_spear': ('../sukuna/attacks/fuga/divine_flames', 4001, {'cond': 'none', 'hi': 4001, 'lo': 3850}),
    'sukuna_fuga_mugetsu': ('../sukuna/attacks/fuga/divine_flames', 5001, {'cond': 'none', 'hi': 5001, 'lo': 4860}),
    'sukuna_fuga_scythe': ('../sukuna/attacks/fuga/divine_flames', 7001, {'cond': 'none', 'hi': 7001, 'lo': 6955}),
    'sukuna_fuga_grab': ('../sukuna/attacks/fuga/divine_flames', 6801, {'cond': 'none', 'hi': 6801, 'lo': 6660}),
    'fuga_boom': ('../sukuna/attacks/fuga/fuga_arrow1', 1001, {'cond': 'none', 'hi': 1001, 'lo': 700, 'var': 'Deleter'}),
    # 伏魔御厨子 (sukuna3): the cast (domainstart 121 -> 1) and the cutscene that starts with it (domain 221 / 281 -> 1)
    'sukuna_dom1_start': ('../sukuna/attacks/domains/shrine/start', 121, {'cond': 'none', 'hi': 121, 'lo': 1, 'var': 'domainstart'}),
    'sukuna_dom1_cut': ('../sukuna/attacks/domains/shrine/cutscene', 221, {'cond': 'none', 'hi': 221, 'lo': 1, 'var': 'domain'}),
    'sukuna_dom2_start': ('../sukuna/attacks/domains/kitchen/start', 121, {'cond': 'none', 'hi': 121, 'lo': 1, 'var': 'domainstart'}),
    'sukuna_dom2_cut': ('../sukuna/attacks/domains/kitchen/cutscene', 221, {'cond': 'none', 'hi': 221, 'lo': 1, 'var': 'domain'}),
    'sukuna_dom3_start': ('../sukuna/attacks/domains/incomplete/start', 121, {'cond': 'none', 'hi': 121, 'lo': 1, 'var': 'domainstart'}),
    'sukuna_dom3_cut': ('../sukuna/attacks/domains/incomplete/cutscene', 281, {'cond': 'none', 'hi': 281, 'lo': 1, 'var': 'domain'}),
    'yuji_fierce': ('../yuji/fierce_strikes', 36, {'cond': 'none'}),
    'yuji_fierce_n': ('../yuji/fierce_strikes', 36, {'cond': 'nbf', 'hi': 15}),
    'yuji_fierce_b': ('../yuji/fierce_strikes', 36, {'cond': 'bf', 'hi': 15}),
    'yuji_twin': ('../yuji/twinkicks', 43, {'cond': 'none'}),
    'yuji_quake_air': ('../yuji/brutal_quake', 87, {'cond': 'none', 'lo': 83}),
    'yuji_quake_land': ('../yuji/brutal_quake', 87, {'cond': 'none', 'hi': 82}),
    'yuji_pursuit': ('../yuji/deadly_pursuit', 16, {'cond': 'none', 'lo': 2}),
    'yuji_pursuit2': ('../yuji/deadly_pursuit', 16, {'cond': 'none', 'hi': 1001, 'lo': 900}),
    'yuji_vengeance': ('../yuji/vengeance_fists', 16, {'cond': 'none', 'lo': 2}),
    'yuji_vengeance2': ('../yuji/vengeance_fists', 16, {'cond': 'none', 'hi': 1002, 'lo': 900}),
    'yuji_flash': ('../yuji/final_flash', 16, {'cond': 'none', 'lo': 2}),
    'yuji_flash2': ('../yuji/final_flash', 16, {'cond': 'none', 'hi': 1002, 'lo': 700}),
    'yuji_soul1': ('../yuji/soul_ab1', 26, {'cond': 'none', 'lo': 2}),
    'yuji_soul1b': ('../yuji/soul_ab1', 26, {'cond': 'none', 'hi': 101, 'lo': 60}),
    'yuji_soul2': ('../yuji/soul_ab2', 16, {'cond': 'none', 'lo': 2}),
    'yuji_soul2b': ('../yuji/soul_ab2', 16, {'cond': 'none', 'hi': 1001, 'lo': 900}),
    'yuji_soul3': ('../yuji/soul_ab3', 21, {'cond': 'none', 'lo': 2}),
    'yuji_soul3b': ('../yuji/soul_ab3', 21, {'cond': 'none', 'hi': 1001, 'lo': 900}),
    'yuji_soul4': ('../yuji/soul_ab4', 36, {'cond': 'none'}),
    'yuji_soul5': ('../yuji/soul_ab5', 31, {'cond': 'none', 'lo': 2}),
    'yuji_soul5b': ('../yuji/soul_ab5', 31, {'cond': 'none', 'hi': 1001, 'lo': 800}),
}
# timelines that are not lines of an @s function: key -> (n, rows). Row = [tick, 'p', name, 0, x, y, z] / [tick, 's', name, vol, pitch, radius] / [tick, 'k', amp, ticks, radius]
def _loop(names, a, b):
    return [[t, 'p', nm, 0, 0.0, 0.0, 0.0] for t in range(a, b + 1) for nm in names]


EXTRA = {
    # fuga_spear.mcfunction: one spear_explosion (Deleter 9 -> 3: the fall at the first ticks, the blast at Deleter 3 = tick 7)
    'fuga_spear_blast': (9, [[2, 's', 'mob.red2', 999999.0, 2.0, 400]] + _loop(['fuga_stars1', 'fuga_stars2'], 2, 3)
                         + _loop(['fuga_spear_fall1', 'fuga_spear_fall2'], 1, 2)
                         + [[7, 'k', 2.0, 2, 100], [7, 's', 'random.explode', 99999.0, 1.0, 200]]
                         + _loop(['energy_explode1', 'fuga_area', 'fuga_spear_land1', 'fuga_spear_land2', 'flame_repulse_ex2', 'stoic_explode1', 'stoic_explode3'], 7, 7)),
    # the entities' own animations, once per tick of the flight
    'fuga_arrow_fly': (60, _loop(['fuga_head1', 'fuga_head2', 'fuga_head3'], 1, 60)),
    'fuga_spear_fly': (200, _loop(['fuga_spear', 'fuga_spear_shine1', 'fuga_spear_shine2'], 1, 200)),
    'fuga_mugetsu_fly': (60, _loop(['fuga_mugetsu1', 'fuga_mugetsu2', 'fuga_mugetsu3', 'fuga_mugetsu4', 'fuga_mugetsu5', 'fuga_mugetsu6', 'fuga_mugetsu7', 'fuga_mugetsu8'], 1, 60)),
    'fuga_scythe_fly': (22, _loop(['fuga_scythe_slash1', 'fuga_scythe_slash2', 'fuga_scythe_slash3', 'fuga_scythe_slash4', 'fuga_scythe_slash5'], 1, 22)),
}
# vanilla sounds the addon uses by their Bedrock name
VANILLA = {
    'beacon.deactivate': 'minecraft:block.beacon.deactivate', 'beacon.activate': 'minecraft:block.beacon.activate',
    'random.fizz': 'minecraft:block.fire.extinguish', 'random.glass': 'minecraft:block.glass.break',
    'random.explode': 'minecraft:entity.generic.explode', 'random.levelup': 'minecraft:entity.player.levelup',
    'random.orb': 'minecraft:entity.experience_orb.pickup', 'mob.wither.shoot': 'minecraft:entity.wither.shoot',
    'mob.wither.hurt': 'minecraft:entity.wither.hurt', 'mob.wither.break_block': 'minecraft:entity.wither.break_block',
    'mob.wither.spawn': 'minecraft:entity.wither.spawn', 'mob.enderdragon.growl': 'minecraft:entity.ender_dragon.growl',
    'mob.elderguardian.curse': 'minecraft:entity.elder_guardian.curse', 'mob.shulker.hurt': 'minecraft:entity.shulker.hurt',
    'random.anvil_land': 'minecraft:block.anvil.land', 'note.pling': 'minecraft:block.note_block.pling',
    'mob.stormwraith.attack': 'minecraft:entity.generic.explode', 'ambient.weather.thunder': 'minecraft:entity.lightning_bolt.thunder',
    'mob.bat.takeoff': 'minecraft:entity.bat.takeoff', 'mob.blaze.shoot': 'minecraft:entity.blaze.shoot', 'mob.enderdragon.flap': 'minecraft:entity.ender_dragon.flap', 'mob.witch.throw': 'minecraft:entity.witch.throw',
    'mob.blaze.breathe': 'minecraft:entity.blaze.ambient', 'mob.block.campfire.crackle': 'minecraft:block.campfire.crackle',
    'block.end_portal.spawn': 'minecraft:block.end_portal.spawn', 'break.amethyst_block': 'minecraft:block.amethyst_block.break',
    'portal.travel': 'minecraft:block.portal.travel', 'portal.trigger': 'minecraft:block.portal.trigger',
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


def move_range(sel, n, var='move'):
    m = re.search(r'\b' + var + r'=(\d*)(\.\.)?(\d*)', sel)
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


def events_for(path, n, sounds, particles, glyphs, opt=None):
    opt = opt or {}
    ev = []
    for raw in open(path, encoding='utf-8', errors='ignore').read().replace('\r', '').split('\n'):
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        pl = parse_line(line)
        if not pl:
            continue
        sel, cmd = pl
        if re.search(r'Stun=1\.\.|punch=|domainactive|itemUse|domainclashing|domain_clasher', sel):
            continue  # conditional on a state the timeline does not know (the caster being stunned, the selection click, a domain)
        rng = move_range(sel, 100000 if opt else n, opt.get('var', 'move') if opt else 'move')
        if not rng:
            continue
        cnd = 'none'
        if re.search(r'tag=blackflashed(?![\w])', sel):
            cnd = 'bf'
        elif 'tag=!blackflashed' in sel:
            cnd = 'nbf'
        if opt and cnd != opt.get('cond', 'none'):
            continue
        hi = opt.get('hi', n) if opt else n
        lo = opt.get('lo', 0) if opt else 0
        a, b = max(rng[0], lo), min(rng[1], hi)
        if a > b:
            continue
        if b - a > 2000:
            continue
        ticks = sorted(set(max(1, hi - m) for m in range(a, b + 1)))
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


PROJECTILES = {
    # key: (function, entity name, animation file key of the per-tick animation)
    'hollow_purple': ('hollow_purple', 'HollowP', 'animation.hollowp'),
    'hollow_purple200': ('200hollow_purple', '200HollowP', 'animation.200hollowp'),
    'imaginary_purple': ('imaginary_purple', 'ImaginaryP', 'animation.imaginaryp'),
}


def projectile_events(path, name, anim, anims, particles):
    """Per-tick effects of a projectile entity: [kind, ...] rows with a condition (0 always, 1 inside a block, 2 on the ground)
    and a chance (1 = every tick, 5 = one in five)."""
    out = {'p': [], 'k': []}
    pat = re.compile(r'execute as @s\[name=' + re.escape(name) + r',scores=\{([^}]*)\}\] at @s (.*?)run (particle|camerashake) (.*)$')
    for raw in open(path, encoding='utf-8', errors='ignore').read().replace('\r', '').split('\n'):
        m = pat.match(raw.strip())
        if not m:
            continue
        scores, conds, cmd, rest = m.groups()
        cond = 0
        if conds.strip():
            if re.fullmatch(r'unless block ~~~ air unless block ~~~ barrier ', conds):
                cond = 1
            elif re.fullmatch(r'unless block ~~-0\.3~ air unless block ~~-0\.3~ barrier ', conds):
                cond = 2
            else:
                continue
        chance = 5 if 'chance=1' in scores else 1
        tok = rest.split()
        if cmd == 'particle' and tok and tok[0].startswith('ds:'):
            parts = re.split(r'(?=[~^])', tok[0][3:], maxsplit=1)
            r2 = (parts[1] if len(parts) > 1 else '') + ''.join(tok[1:])
            c = coords(r2) if r2 else (0, [0.0, 0.0, 0.0])
            if c is None:
                continue
            particles.add(parts[0])
            out['p'].append([parts[0], c[0]] + c[1] + [cond, chance])
        elif cmd == 'camerashake' and len(tok) >= 4 and tok[0] == 'add':
            out['k'].append([num(tok[2]), int(round(num(tok[3]) * 20)), radius(tok[1]), cond])
    a = anims.get(anim)
    if a:
        seen = []
        for k, v in (a.get('particle_effects') or {}).items():
            for e in (v if isinstance(v, list) else [v]):
                if e['effect'] not in seen or True:
                    seen.append(e['effect'])
        for eff in seen:
            particles.add(eff)
            out['p'].append([eff, 0, 0.0, 0.0, 0.0, 0, 1])
    return out


def trg_events(path, anims, particles, t0=21, d0=251):
    """limitless5d: the anchor projectile LimitlessPTRG (summoned at move 460 = tick 21, Deleter 251 counting down) follows the
    caster (tp ~~1~), spins (tp ~~~ ~speed), keeps the red and blue orbs at ^^^+-D of itself and plays the particles listed in
    limitless5ptrg.mcfunction by Deleter range.  Everything is exported as events relative to the caster."""
    import math
    lines = open(path, encoding='utf-8', errors='ignore').read().replace('\r', '').split('\n')
    pat = re.compile(r'execute as @s\[name=LimitlessPTRG,scores=\{Deleter=(\d*)(\.\.)?(\d*)\}\] at @s run (particle|camerashake) (.*)$')
    tp = re.compile(r'tp @s\[name=LimitlessPTRG,scores=\{Deleter=(\d+)\.\.(\d*)\}\] ~~~~(\d+)')
    spins, rules = [], []
    for raw in lines:
        raw = raw.strip()
        m = tp.match(raw)
        if m:
            spins.append((int(m.group(1)), int(m.group(2)) if m.group(2) else 9999, int(m.group(3))))
            continue
        m = pat.match(raw)
        if m:
            lo = int(m.group(1)) if m.group(1) else 0
            hi = int(m.group(3)) if m.group(3) else (lo if not m.group(2) else 9999)
            rules.append((lo, hi, m.group(4), m.group(5).split()))
    orb = [(200, 9999, 13.0, 0), (140, 200, 10.0, 0), (100, 139, 8.0, 0), (5, 99, 7.0, 0), (1, 24, 0.0, 7.0)]
    ev = []
    theta = 0.0
    for n in range(0, d0):
        d = d0 - n
        t = t0 + n
        sp = [v for (lo, hi, v) in spins if lo <= d <= hi]
        if sp:
            theta += sp[0]
        for lo, hi, cmd, tok in rules:
            if not (lo <= d <= hi):
                continue
            if cmd == 'particle' and tok[0].startswith('ds:'):
                parts = re.split(r'(?=[~^])', tok[0][3:], maxsplit=1)
                c = coords((parts[1] if len(parts) > 1 else '') + ''.join(tok[1:]))
                if c is None:
                    continue
                particles.add(parts[0])
                ev.append([t, 'p', parts[0], 0, c[1][0], c[1][1] + 1.0, c[1][2]])
            elif cmd == 'camerashake' and tok[0] == 'add':
                ev.append([t, 'k', num(tok[2]), int(round(num(tok[3]) * 20)), radius(tok[1])])
        # the orbs (hollow_red / hollow_blue play their per-tick particles at the orb)
        o = [(dd, up) for (lo, hi, dd, up) in orb if lo <= d <= hi]
        if o and d >= 20 - 1 + 0 and d >= 1:
            dist, up = o[-1] if d <= 24 else o[0]
            if d >= 20:
                for sign, anim in ((1, 'animation.hollow_red'), (-1, 'animation.hollow_blue')):
                    a = anims.get(anim) or {}
                    effs = [e['effect'] for v in (a.get('particle_effects') or {}).values() for e in (v if isinstance(v, list) else [v])]
                    rad = math.radians(theta)
                    for eff in effs:
                        particles.add(eff)
                        ev.append([t, 'p', eff, 0, -math.sin(rad) * dist * sign, 1.0 + up, math.cos(rad) * dist * sign])
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
    for key, (fn, n, *o) in [(k, v) for k, v in list(MOVES.items())] + [(k, v) for k, v in YUJI.items()]:
        ev = events_for(os.path.join(nfun, fn + '.mcfunction'), n, sounds, particles, glyphs, o[0] if o else None)
        out[key] = {'n': n, 'events': ev}
        print(key, n, len(ev), 'events')
    for key, (n, rows) in EXTRA.items():
        for r in rows:
            if r[1] == 'p':
                particles.add(r[2])
            if r[1] == 's':
                sounds.add(r[2])
        out[key] = {'n': n, 'events': sorted(rows, key=lambda r: r[0])}
    # fuga_arrow1: the music is played by "execute as @a[r=200] ... playsound music.fuga_music" at Deleter 999..1000
    for t in (1, 2):
        out['fuga_boom']['events'].append([t, 's', 'music.fuga_music', 999999.0, 1.0, 200])
        sounds.add('music.fuga_music')
    out['fuga_boom']['events'].sort(key=lambda r: r[0])
    # blitz.mcfunction: the line of particles along the look ray (once, at move 5 of limitless4)
    blitz_ray = [[11, 'r', 'spatial_blitz2', 0.33, 21.0], [11, 'r', 'spatial_blitz3', 0.33, 21.0], [11, 'r', 'spatial_blitz4', 0.33, 21.0]]
    out['blitz']['events'] += blitz_ray
    particles.update(['infinity', 'infinity_shatter', 'infinity_shatter2', 'spatial_blitz2', 'spatial_blitz3', 'spatial_blitz4', 'heavy_impact1', 'impact'])
    out['blitz']['events'].sort(key=lambda r: r[0])
    for e in out['purple_unl']['events']:
        pass

    anims = {}
    for f in glob.glob(os.path.join(src, 'rp4', 'animations', 'limitless.animation.json')):
        anims.update(json.loads(re.sub(r'(?m)^\s*//.*$', '', open(f, encoding='utf-8').read())).get('animations', {}))
    proj = {}
    for key, (fn, ename, anim) in PROJECTILES.items():
        proj[key] = projectile_events(os.path.join(nfun, fn + '.mcfunction'), ename, anim, anims, particles)
        print('projectile', key, len(proj[key]['p']), 'particles', len(proj[key]['k']), 'shakes')
    out['purple_unl']['events'] += trg_events(os.path.join(nfun, 'limitless5ptrg.mcfunction'), anims, particles)
    out['purple_unl']['events'].sort(key=lambda r: r[0])
    print('purple_unl events', len(out['purple_unl']['events']))
    pdir = os.path.join(dst, 'bedrock', 'gojo')
    os.makedirs(pdir, exist_ok=True)
    with open(os.path.join(pdir, 'projectiles.json'), 'w', encoding='utf-8') as fh:
        json.dump(proj, fh, separators=(',', ':'))

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
