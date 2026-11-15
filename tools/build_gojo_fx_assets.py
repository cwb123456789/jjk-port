#!/usr/bin/env python3
"""Copies what the Bedrock addon plays for Gojo's Blue (苍), Red (赫) and their variants into the mod's resources.

usage: build_gojo_fx_assets.py <dir holding the extracted resource packs> <resources/assets/jujutsuenhancement>

The addon draws these techniques with
  * the particle events of the player animations (animations/limitless.animation.json), fired at the animation's
    "locator_right" (the right hand) and sometimes at the feet,
  * particles/sounds fired by the move functions (functions/limitless/*.mcfunction) and by the Lapse / MaxBlue / Reversal
    projectile functions.
This script writes
  * bedrock/gojo/anim_fx.json: for every animation in ANIMS the list [tick, particle, left, up, forward] of its particle events;
    the offset of locator_right is computed from the rightArm rotation keyframes of the animation, in blocks, in the
    addon's ^left ^up ^forward frame of the entity,
  * the particle definitions and textures (through build_bedrock_assets, which keeps what is already there),
  * the sound files, registered in sounds.json as bedrock.gojo.<name>.
"""
import glob
import json
import math
import os
import shutil
import sys

import build_bedrock_assets as bba

ANIMS = ['lapse_blue1', 'lapse_blue2', 'lapse_pull', 'reversal_red1', 'reversal_red2', 'reversal_red3']

# particles fired from the functions (names without the "ds:" prefix)
FUNCTION_PARTICLES = [
    # lapseblue / lapseblue2 / maxblue (the flying Blue, the pulling Blue, the maximum Blue)
    'lapse', 'lapseblue', 'lapseblue0a', 'lapseblue0b', 'lapseblue0c', 'lapseblue1', 'lapseblue2', 'lapseblue3', 'lapseblue4',
    'lapseblue5', 'lapseblue6', 'lapseblue_ex1', 'lapseblue_ex2', 'lapseblue_ex3', 'lapseblue_ex4', 'blue_gas',
    'rubble_suck2', 'rubble_sucked1', 'rubble_sucked2', 'rubble_sucked_big', 'lapse_energy', 'rubble3',
    'blood_mist', 'blood_explode',
    # limitless2c (Blue pull)
    'impact', 'lapse_pull1', 'lapse_pull2', 'lapse_pull3', 'lapse_pull4', 'lapse_pull5',
    # limitless3 / 3c / reversal (Red)
    'red1', 'red2', 'red3', 'red4', 'red5', 'red6', 'red7', 'red_ex', 'red_ex1', 'red_ex2', 'red_ex3', 'red_ex4', 'red_ex5',
    'red_ex6', 'red_launch', 'red_launch2', 'red_launch3', 'crater3', 'air_effect1', 'flashstep1',
]

SOUNDS = {'red1': 'red1', 'shock1': 'shock1', 'shock2': 'shock2', 'blood': 'blood'}


def val(kf):
    """The rotation vector of a keyframe, which can be a bare list or a dict with pre/post/vector."""
    if isinstance(kf, list):
        return kf
    if isinstance(kf, dict):
        for k in ('post', 'vector', 'pre'):
            if k in kf and isinstance(kf[k], list):
                return kf[k]
    return [0, 0, 0]


def pre_val(kf):
    if isinstance(kf, dict) and isinstance(kf.get('pre'), list):
        return kf['pre']
    return val(kf)


def sample(track, t):
    """Linear sample of a keyframed vector track at time t (seconds)."""
    if track is None:
        return [0.0, 0.0, 0.0]
    if isinstance(track, list):
        return [float(x) if not isinstance(x, str) else 0.0 for x in track]
    if isinstance(track, dict) and ('post' in track or 'vector' in track) and not any(_is_time(k) for k in track):
        return [float(x) if not isinstance(x, str) else 0.0 for x in val(track)]
    keys = sorted((float(k), v) for k, v in track.items() if _is_time(k))
    if not keys:
        return [0.0, 0.0, 0.0]
    if t <= keys[0][0]:
        return _num(val(keys[0][1]))
    for (t0, a), (t1, b) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            va, vb = _num(val(a)), _num(pre_val(b))
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [va[i] + (vb[i] - va[i]) * f for i in range(3)]
    return _num(val(keys[-1][1]))


def _is_time(k):
    try:
        float(k)
        return True
    except (TypeError, ValueError):
        return False


def _num(v):
    return [float(x) if not isinstance(x, str) else 0.0 for x in v]


def rot(v, vec):
    """Bedrock Euler rotation (degrees, applied X then Y then Z) of vec."""
    ax, ay, az = (math.radians(a) for a in v)
    x, y, z = vec
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    return [x, y, z]


# humanoid rig of the addon: rightArm pivot and the locator_right on it (model pixels)
ARM_PIVOT = [-5.0, 22.0, 0.0]
LOCATOR_RIGHT = [-5.8, 12.0, 0.0]
BODY_PIVOT = [0.0, 24.0, 0.0]


def hand_offset(anim, t):
    bones = anim.get('bones', {})
    arm = sample(bones.get('rightArm', {}).get('rotation'), t)
    body = sample(bones.get('body', {}).get('rotation'), t)
    rel = [LOCATOR_RIGHT[i] - ARM_PIVOT[i] for i in range(3)]
    p = rot(arm, rel)
    p = [ARM_PIVOT[i] + p[i] for i in range(3)]
    q = rot(body, [p[i] - BODY_PIVOT[i] for i in range(3)])
    p = [BODY_PIVOT[i] + q[i] for i in range(3)]
    # model x grows to the entity's left, y up, z forward; 16 px per block
    return [round(p[0] / 16.0, 3), round(p[1] / 16.0, 3), round(p[2] / 16.0, 3)]


def anim_events(src_anims):
    out = {}
    used = set()
    for name in ANIMS:
        a = src_anims['animation.' + name]
        pe = a.get('particle_effects') or {}
        events = []
        for t, v in pe.items():
            for e in (v if isinstance(v, list) else [v]):
                eff = e.get('effect')
                if not eff:
                    continue
                used.add(eff)
                if e.get('locator') == 'locator_right':
                    off = hand_offset(a, float(t))
                else:
                    off = [0.0, 0.0, 0.0]
                events.append([int(float(t) * 20 + 0.5), eff] + off)
        events.sort(key=lambda r: r[0])
        out[name] = {'length': int(round((a.get('animation_length') or 0) * 20)), 'events': events}
    return out, used


def main(src, dst):
    anim_files = glob.glob(os.path.join(src, '**', 'animations', 'limitless.animation.json'), recursive=True)
    if not anim_files:
        raise SystemExit('limitless.animation.json not found under ' + src)
    src_anims = json.load(open(anim_files[0], encoding='utf-8'))['animations']
    fx, used = anim_events(src_anims)
    gdir = os.path.join(dst, 'bedrock', 'gojo')
    os.makedirs(gdir, exist_ok=True)
    with open(os.path.join(gdir, 'anim_fx.json'), 'w', encoding='utf-8') as fh:
        json.dump(fx, fh, separators=(',', ':'))
    print('animation events:', {k: len(v['events']) for k, v in fx.items()})

    want = sorted(set(FUNCTION_PARTICLES) | used)
    bba.WANT = want
    bba.main(src, dst)
    have = set(os.path.splitext(f)[0] for f in os.listdir(os.path.join(dst, 'bedrock', 'particles')))
    print('missing particles:', [p for p in want if p not in have])

    sdir = os.path.join(dst, 'sounds', 'bedrock')
    os.makedirs(sdir, exist_ok=True)
    sj_path = os.path.join(dst, 'sounds.json')
    sj = json.load(open(sj_path, encoding='utf-8')) if os.path.exists(sj_path) else {}
    for n, f in SOUNDS.items():
        hits = glob.glob(os.path.join(src, '**', 'sounds', 'main', f + '.ogg'), recursive=True)
        if not hits:
            print('missing sound', n)
            continue
        shutil.copyfile(hits[0], os.path.join(sdir, 'gojo_' + n + '.ogg'))
        sj['bedrock.gojo.' + n] = {'category': 'hostile',
                                   'sounds': [{'name': 'jujutsuenhancement:bedrock/gojo_' + n, 'volume': 1.0}]}
    with open(sj_path, 'w', encoding='utf-8') as fh:
        json.dump(sj, fh, indent=2)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
