#!/usr/bin/env python3
"""Audit the Bedrock Sukuna move animations imported into the GeckoLib resource."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/jujutsuenhancement'
SOURCE = ROOT / 'docs/bedrock-source-reference/animations/sukuna/sukuna_moves.animation.json'
RUNTIME = ASSETS / 'animations/heian_sukuna.animation.json'
GEOMETRY = ASSETS / 'geo/heian_sukuna.geo.json'

def load(path):
    return json.loads(path.read_text(encoding='utf-8'))

def main():
    source = load(SOURCE)['animations']
    runtime = load(RUNTIME)['animations']
    geometry = load(GEOMETRY)
    geo_bones = {bone['name'] for model in geometry['minecraft:geometry'] for bone in model['bones']}
    imported = sorted(set(source) & set(runtime))
    absent = sorted(set(source) - set(runtime))
    animated_bones = {bone for name in source for bone in source[name].get('bones', {})}
    missing_bones = sorted(animated_bones - geo_bones)
    malformed = []
    catmull_frames = 0
    for name in imported:
        for bone, channels in runtime[name].get('bones', {}).items():
            if bone not in geo_bones:
                continue
            for channel, frames in channels.items():
                if channel not in {'rotation', 'position', 'scale'}:
                    malformed.append(f'{name}.{bone}: unknown channel {channel}')
                    continue
                for time, frame in frames.items():
                    vector = frame.get('vector') if isinstance(frame, dict) else None
                    if not isinstance(vector, list) or len(vector) != 3 or not all(isinstance(v, (int, float)) for v in vector):
                        malformed.append(f'{name}.{bone}.{channel}@{time}: invalid vector')
                    if isinstance(frame, dict) and frame.get('easing') == 'catmullrom':
                        catmull_frames += 1
    print(f'Bedrock source animations: {len(source)}')
    print(f'Imported source animations: {len(imported)}')
    print(f'Missing source animation IDs: {len(absent)}')
    print(f'Runtime animation IDs total (including existing idle/domain animations): {len(runtime)}')
    print(f'Animated bone names: {len(animated_bones)}; geometry bone names: {len(geo_bones)}')
    print('Animation bone names absent from geometry:', ', '.join(missing_bones) if missing_bones else 'none')
    print(f'Malformed runtime keyframes: {len(malformed)}')
    print(f'Explicit Catmull-Rom frames still present: {catmull_frames}')
    if absent or malformed or catmull_frames:
        for item in malformed[:20]: print('ERROR:', item)
        return 1
    # Missing geometry bones are a known compatibility warning rather than silently accepted as a full port.
    if missing_bones:
        print('WARNING: some source animation channels target bones absent from the current Java geometry.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
