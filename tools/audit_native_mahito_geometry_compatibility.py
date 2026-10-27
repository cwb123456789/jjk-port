#!/usr/bin/env python3
"""Compare the native JujutsuCraft human skeleton with the Bedrock-derived Mahito skeleton.

Pass means the known incompatibility is detected and reported; it does not mean
that the two skeletons can be swapped without a renderer/model adapter.
"""
from pathlib import Path
import json, zipfile
ROOT = Path(__file__).resolve().parents[1]
def find_base_jar():
    candidates = [
        Path("/mnt/data/jujutsucraft-base.jar"),
        Path("/mnt/data/project_inputs/[墨蓝＆玄道水源汉化]JujutsuCraft-ver50.1-forge-1.20.1.jar"),
        Path("/mnt/data/[墨蓝＆玄道水源汉化]JujutsuCraft-ver50.1-forge-1.20.1.jar"),
    ]
    return next((candidate for candidate in candidates if candidate.is_file()), candidates[0])

JAR = find_base_jar()
BEDROCK_GEO = ROOT / 'src/main/resources/assets/jujutsuenhancement/geo/mahito_stage2.geo.json'

def bone_names(doc):
    result = set()
    def visit(bone):
        name = bone.get('name')
        if name:
            result.add(name)
        for child in bone.get('children', []):
            visit(child)
    for geometry in doc.get('minecraft:geometry', []):
        for bone in geometry.get('bones', []):
            visit(bone)
    return result

checks=[]
def check(name, ok):
    checks.append((name, bool(ok)))

bedrock = json.loads(BEDROCK_GEO.read_text(encoding='utf-8'))
check('Bedrock-derived Mahito stage2 geometry parses', bool(bedrock.get('minecraft:geometry')))
if not JAR.is_file():
    check('base JAR available for native geometry comparison', False)
else:
    with zipfile.ZipFile(JAR) as z:
        native = json.loads(z.read('assets/jujutsucraft/geo/human.geo.json'))
    native_bones = bone_names(native)
    bedrock_bones = bone_names(bedrock)
    shared = native_bones & bedrock_bones
    native_only = native_bones - bedrock_bones
    bedrock_only = bedrock_bones - native_bones
    check('native human geometry parses', bool(native_bones))
    check('skeleton mismatch is explicitly detected', native_bones != bedrock_bones)
    check('only shared base bones are treated as direct name matches', shared == {'body', 'torso', 'head'})
    check('native-only bones requiring mapping are present', {'right_arm', 'left_arm', 'right_leg', 'left_leg'} <= native_only)
    check('Bedrock-only bones requiring mapping are present', {'rightArm', 'leftArm', 'rightLeg', 'leftLeg'} <= bedrock_only)
    print('Shared bone names:', ', '.join(sorted(shared)))
    print('Native-only bones:', ', '.join(sorted(native_only)))
    print('Bedrock-only bones:', ', '.join(sorted(bedrock_only)))
for name, ok in checks:
    print(('PASS' if ok else 'FAIL') + ': ' + name)
print(f'Native Mahito geometry compatibility audit: {sum(ok for _,ok in checks)}/{len(checks)} passed')
raise SystemExit(0 if all(ok for _,ok in checks) else 1)
