#!/usr/bin/env python3
"""Normalize Bedrock geometry JSON for GeckoLib and rescale per-face UVs to actual texture pixels.

Keeps bone hierarchy, cube origins/sizes/rotations/pivots and per-face UV orientation.
This is a data conversion step, not proof of runtime compatibility; render testing is still required.
"""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/jujutsuenhancement'
GEO_DIR = ASSETS / 'geo'
TEXTURE_DIR = ASSETS / 'textures/entity'


def convert(path: Path) -> tuple[int, int, int, int]:
    data = json.loads(path.read_text(encoding='utf-8'))
    models = data.get('minecraft:geometry')
    if not isinstance(models, list) or not models:
        raise ValueError(f'{path.name}: missing minecraft:geometry array')
    stem = path.name.removesuffix('.geo.json')
    texture_path = TEXTURE_DIR / f'{stem}.png'
    if not texture_path.exists():
        # The shrine resource historically uses malevolent_shrine.png; other assets may be unpaired.
        print(f'SKIP {path.name}: no paired texture found; geometry left unchanged')
        return None
    with Image.open(texture_path) as image:
        actual_w, actual_h = image.size
    cube_count = face_count = 0
    for model in models:
        desc = model.setdefault('description', {})
        declared_w = float(desc.get('texture_width', actual_w))
        declared_h = float(desc.get('texture_height', actual_h))
        if declared_w <= 0 or declared_h <= 0:
            raise ValueError(f'{path.name}: invalid declared texture dimensions')
        sx, sy = actual_w / declared_w, actual_h / declared_h
        desc['texture_width'] = actual_w
        desc['texture_height'] = actual_h
        for bone in model.get('bones', []):
            for cube in bone.get('cubes', []):
                cube_count += 1
                uv = cube.get('uv')
                if isinstance(uv, dict):
                    for face in uv.values():
                        if not isinstance(face, dict) or not isinstance(face.get('uv'), list) or not isinstance(face.get('uv_size'), list):
                            raise ValueError(f'{path.name}: malformed per-face UV in bone {bone.get("name")}')
                        face['uv'] = [round(float(face['uv'][0]) * sx, 6), round(float(face['uv'][1]) * sy, 6)]
                        face['uv_size'] = [round(float(face['uv_size'][0]) * sx, 6), round(float(face['uv_size'][1]) * sy, 6)]
                        face_count += 1
                elif isinstance(uv, list) and len(uv) == 2:
                    cube['uv'] = [round(float(uv[0]) * sx, 6), round(float(uv[1]) * sy, 6)]
        # GeckoLib's Bedrock-compatible geometry loader uses this standard geometry wrapper.
    data['format_version'] = '1.12.0'
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return cube_count, face_count, actual_w, actual_h


def main() -> int:
    targets = [GEO_DIR / 'heian_sukuna.geo.json', GEO_DIR / 'malevolent_shrine.geo.json', GEO_DIR / 'innate_sukuna.geo.json', GEO_DIR / 'yuji_domain.geo.json', GEO_DIR / 'heian_shrine.geo.json']
    errors = 0
    for path in targets:
        if not path.exists():
            continue
        try:
            result = convert(path)
            if result is not None:
                cubes, faces, w, h = result
                print(f'{path.name}: converted; cubes={cubes}; per-face UV entries={faces}; texture={w}x{h}')
        except Exception as exc:
            errors += 1
            print(f'ERROR {path.name}: {exc}')
    return 1 if errors else 0

if __name__ == '__main__':
    raise SystemExit(main())
