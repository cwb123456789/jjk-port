#!/usr/bin/env python3
"""Exports EVERY animation of the Bedrock resource pack (skeleton keyframes, particle and sound events) for the player:
bedrock/anims/<group>.json (one per source file) and bedrock/anims/index.json (animation id -> group).

The particle / sound events of an animation name a short effect that the client entity of the entity that plays it maps to a particle
identifier (client_entity.description.particle_effects / sound_effects). The animations are played on the player, so the map of the player
entity (entity/player_entity.json) is applied here: events carry the final particle id and the final sound key.

usage: build_all_anims.py <scratchpad> <resources/assets/jujutsuenhancement>
"""
import glob
import json
import os
import re
import shutil
import sys

import build_armor_assets as baa
import build_bedrock_assets as bba
import build_gojo_moves as gm

KEEP = ('loop', 'animation_length', 'bones', 'particle_effects', 'sound_effects', 'override_previous_animation', 'anim_time_update',
        'start_delay', 'loop_delay')


def load(p):
    return json.loads(re.sub(r'(?m)^\s*//.*$', '', open(p, encoding='utf-8-sig', errors='ignore').read()))


def player_maps(src):
    pm, sm = {}, {}
    for pack in baa.PACKS:
        for f in glob.glob(os.path.join(src, pack, '**', 'player_entity.json'), recursive=True):
            try:
                d = load(f)['minecraft:client_entity']['description']
            except Exception:
                continue
            for k, v in d.get('particle_effects', {}).items():
                pm.setdefault(k, v)
            for k, v in d.get('sound_effects', {}).items():
                sm.setdefault(k, v)
    # other entities declare the shortnames of the animations they play (priority to the player map above)
    for pack in baa.PACKS:
        for f in glob.glob(os.path.join(src, pack, '**', '*.json'), recursive=True):
            if '/entity/' not in f.replace('\\', '/') or f.endswith('player_entity.json'):
                continue
            try:
                d = load(f)['minecraft:client_entity']['description']
            except Exception:
                continue
            for k, v in d.get('particle_effects', {}).items():
                pm.setdefault(k, v)
            for k, v in d.get('sound_effects', {}).items():
                sm.setdefault(k, v)
    return pm, sm


def events(node):
    for t, x in (node or {}).items():
        for e in (x if isinstance(x, list) else [x]):
            if isinstance(e, dict) and 'effect' in e:
                yield t, e


def main(src, dst):
    pm, sm = player_maps(src)
    print('player particle map', len(pm), 'sound map', len(sm))
    groups, index, seen = {}, {}, set()
    particles, sounds = set(), set()
    for pack in baa.PACKS:
        for f in sorted(glob.glob(os.path.join(src, pack, '**', '*.json'), recursive=True)):
            if '/animations/' not in f.replace('\\', '/'):
                continue
            try:
                d = load(f)
            except Exception:
                continue
            anims = d.get('animations') or {}
            group = re.sub(r'[^a-z0-9_]', '_', os.path.basename(f).lower().replace('.animation.json', '').replace('.json', ''))
            for name, a in anims.items():
                if name in seen or not isinstance(a, dict):
                    continue
                seen.add(name)
                out = {k: a[k] for k in KEEP if k in a}
                for key in ('particle_effects',):
                    if key in out:
                        ne = {}
                        for t, e in events(out[key]):
                            eff = pm.get(e['effect'], 'ds:' + e['effect'])
                            eff = eff.split(':')[-1]
                            particles.add(eff)
                            ne.setdefault(t, []).append({'effect': eff, **({'locator': e['locator']} if e.get('locator') else {})})
                        out[key] = ne
                if 'sound_effects' in out:
                    ne = {}
                    for t, e in events(out['sound_effects']):
                        snd = sm.get(e['effect'], e['effect'])
                        sounds.add(snd)
                        ne.setdefault(t, []).append({'effect': snd})
                    out['sound_effects'] = ne
                groups.setdefault(group, {})[name] = out
                index[name] = group
    print('animations', len(index), 'groups', len(groups), 'particles', len(particles), 'sounds', len(sounds))
    # particles
    bba.WANT = sorted(particles | set(bba.WANT))
    bba.main(src, dst)
    # sounds
    defs = gm.load_sound_defs(src)
    sdir = os.path.join(dst, 'sounds', 'bedrock')
    os.makedirs(sdir, exist_ok=True)
    sj_path = os.path.join(dst, 'sounds.json')
    sj = json.load(open(sj_path, encoding='utf-8')) if os.path.exists(sj_path) else {}
    resolved = {}
    for name in sorted(sounds):
        base = re.sub(r'\.(mp3|ogg|wav)$', '', name)
        dfn = None
        for cand in (name, 'mob.' + name, 'music.' + name, base, 'mob.' + base, 'music.' + base):
            if cand in defs:
                dfn = defs[cand]
                break
        if dfn is None and name == 'mob.slime.jump':
            resolved[name] = 'minecraft:entity.slime.jump_small'
            continue
        if dfn is None and name == 'armor.equip_chain':
            resolved[name] = 'minecraft:item.armor.equip_chain'
            continue
        if dfn is None:
            if name in gm.VANILLA:
                resolved[name] = gm.VANILLA[name]
            continue
        files = []
        for s in dfn.get('sounds', []):
            rel = s if isinstance(s, str) else s.get('name')
            p = gm.find_ogg(src, rel)
            if p:
                safe = 'gojo_mv_' + re.sub(r'[^a-z0-9_]', '_', rel.lower().split('/')[-1])
                if not os.path.exists(os.path.join(sdir, safe + '.ogg')):
                    shutil.copyfile(p, os.path.join(sdir, safe + '.ogg'))
                files.append({'name': 'jujutsuenhancement:bedrock/' + safe, 'volume': 1.0})
        if files:
            key = 'bedrock.gojo.mv_' + re.sub(r'[^a-z0-9_]', '_', name.lower())
            sj[key] = {'category': 'hostile', 'sounds': files}
            resolved[name] = 'jujutsuenhancement:' + key
    unresolved = sorted(n for n in sounds if n not in resolved)
    print('sounds resolved', len(resolved), 'unresolved', len(unresolved), unresolved)
    with open(sj_path, 'w', encoding='utf-8') as fh:
        json.dump(sj, fh, indent=2)
    adir = os.path.join(dst, 'bedrock', 'anims')
    if os.path.isdir(adir):
        shutil.rmtree(adir)
    os.makedirs(adir)
    total = 0
    for g, anims in groups.items():
        for a in anims.values():
            if 'sound_effects' in a:
                for t, lst in a['sound_effects'].items():
                    for e in lst:
                        e['effect'] = resolved.get(e['effect'], '')
                    a['sound_effects'][t] = [e for e in lst if e['effect']]
        with open(os.path.join(adir, g + '.json'), 'w', encoding='utf-8') as fh:
            json.dump(anims, fh, separators=(',', ':'))
        total += os.path.getsize(os.path.join(adir, g + '.json'))
    with open(os.path.join(adir, 'index.json'), 'w', encoding='utf-8') as fh:
        json.dump(index, fh, separators=(',', ':'))
    print('anims written', total // 1024, 'KB in', len(groups), 'files')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
