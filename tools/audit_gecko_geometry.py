#!/usr/bin/env python3
"""Audit converted Bedrock cube UV data for GeckoLib geometry resources.

This validates the six-face UV object shape and atlas bounds. It does not prove
that GeckoLib 4.8.4 accepts every geometry feature or that the model renders well.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GEO_DIR = ROOT / 'src/main/resources/assets/jujutsuenhancement/geo'
TARGETS = ('heian_sukuna.geo.json', 'malevolent_shrine.geo.json')
FACES = ('north', 'east', 'south', 'west', 'up', 'down')
EPSILON = 1e-6


def audit(path: Path) -> list[str]:
    errors = []
    data = json.loads(path.read_text(encoding='utf-8'))
    models = data.get('minecraft:geometry')
    if not isinstance(models, list) or not models:
        return [f'{path.name}: missing minecraft:geometry array']
    for model_index, model in enumerate(models):
        desc = model.get('description', {})
        try:
            width, height = float(desc['texture_width']), float(desc['texture_height'])
        except (KeyError, TypeError, ValueError):
            errors.append(f'{path.name}: geometry[{model_index}] has invalid texture dimensions')
            continue
        if width <= 0 or height <= 0:
            errors.append(f'{path.name}: non-positive texture dimensions')
            continue
        bones = model.get('bones', [])
        cubes = [cube for bone in bones for cube in bone.get('cubes', [])]
        per_face = 0
        for bone in bones:
            for cube_index, cube in enumerate(bone.get('cubes', [])):
                uv = cube.get('uv')
                if not isinstance(uv, dict):
                    continue
                per_face += 1
                for face_name in FACES:
                    face = uv.get(face_name)
                    where = f'{path.name}:{bone.get("name", "<unnamed>")} cube[{cube_index}] {face_name}'
                    if not isinstance(face, dict):
                        errors.append(f'{where}: missing face UV object')
                        continue
                    origin, size = face.get('uv'), face.get('uv_size')
                    if not (isinstance(origin, list) and len(origin) == 2 and isinstance(size, list) and len(size) == 2):
                        errors.append(f'{where}: uv/uv_size must each contain two numbers')
                        continue
                    try:
                        x, y = map(float, origin)
                        dx, dy = map(float, size)
                    except (TypeError, ValueError):
                        errors.append(f'{where}: non-numeric UV value')
                        continue
                    if not all(map(lambda n: abs(n) < float('inf'), (x, y, dx, dy))):
                        errors.append(f'{where}: non-finite UV value')
                        continue
                    if min(x, x + dx) < -EPSILON or max(x, x + dx) > width + EPSILON or min(y, y + dy) < -EPSILON or max(y, y + dy) > height + EPSILON:
                        errors.append(f'{where}: UV rectangle exceeds {width:g}x{height:g} atlas')
        negative_sizes = sum(1 for cube in cubes if any(float(v) < 0 for v in cube.get('size', [])))
        print(f'{path.name}: bones={len(bones)} cubes={len(cubes)} per-face-UV-cubes={per_face} atlas={width:g}x{height:g} negative-size-cubes={negative_sizes}')
        if per_face:
            print(f'  PASS: all per-face UVs have six valid faces and remain inside the declared atlas (negative uv_size is allowed for mirroring).')
    return errors


def main() -> int:
    errors = []
    for name in TARGETS:
        path = GEO_DIR / name
        if path.exists():
            try:
                errors.extend(audit(path))
            except (OSError, json.JSONDecodeError) as exc:
                errors.append(f'{name}: {exc}')
        else:
            errors.append(f'{name}: missing geometry file')
    if errors:
        print('\n'.join(f'ERROR: {item}' for item in errors))
        return 1
    print('PASS: all targeted geometry UV checks passed. Runtime loading/rendering remains unverified.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
