#!/usr/bin/env python3
"""Compare the preserved Bedrock Heian Sukuna geometry with the runtime GeckoLib model."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'docs/bedrock-source-reference/JujutsuAwa__models__entity__sukuna__heian_sukuna.geo.json'
RUNTIME = ROOT / 'src/main/resources/assets/jujutsuenhancement/geo/heian_sukuna.geo.json'
ANIMATION = ROOT / 'src/main/resources/assets/jujutsuenhancement/animations/heian_sukuna.animation.json'


def read_geometry(path):
    data = json.loads(path.read_text(encoding='utf-8'))
    return data['minecraft:geometry'][0]


def main():
    source = read_geometry(SOURCE)
    runtime = read_geometry(RUNTIME)
    source_bones = {bone['name']: bone for bone in source['bones']}
    runtime_bones = {bone['name']: bone for bone in runtime['bones']}
    animations = json.loads(ANIMATION.read_text(encoding='utf-8')).get('animations', {})
    animated_bones = {
        bone for animation in animations.values()
        for bone in animation.get('bones', {})
    }
    missing_source_bones = sorted(set(source_bones) - set(runtime_bones))
    extra_runtime_bones = sorted(set(runtime_bones) - set(source_bones))
    missing_animated_bones = sorted(animated_bones - set(runtime_bones))
    # Converted geometry intentionally expands the 64x64 Bedrock UV atlas to 512x512.
    source_desc = source.get('description', {})
    runtime_desc = runtime.get('description', {})
    checks = {
        'source and runtime skeleton bone names match': not missing_source_bones and not extra_runtime_bones,
        'runtime geometry declares expanded 512x512 atlas': runtime_desc.get('texture_width') == 512 and runtime_desc.get('texture_height') == 512,
        'all animation channels target runtime bones': not missing_animated_bones,
    }
    for name, ok in checks.items():
        print(f'{"PASS" if ok else "FAIL"}: {name}')
    print(f'Bedrock bones: {len(source_bones)}; runtime bones: {len(runtime_bones)}')
    if missing_source_bones: print('Runtime missing source bones:', ', '.join(missing_source_bones))
    if extra_runtime_bones: print('Runtime-only bones:', ', '.join(extra_runtime_bones))
    if missing_animated_bones: print('Animation bones absent from runtime geometry:', ', '.join(missing_animated_bones))
    if source_desc.get('texture_width') != 64 or source_desc.get('texture_height') != 64:
        print('NOTE: source atlas dimensions differ from expected 64x64; review UV scaling.')
    return 0 if all(checks.values()) else 1

if __name__ == '__main__':
    raise SystemExit(main())
