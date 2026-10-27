#!/usr/bin/env python3
"""Static checks for the generated native-name Mahito stage-two resources."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/jujutsuenhancement'
geo = json.loads((ASSETS / 'geo/mahito_stage2_native.geo.json').read_text())
anims = json.loads((ASSETS / 'animations/mahito_stage2_native.animation.json').read_text())
assert geo.get('minecraft:geometry'), 'geometry array missing'
bones = geo['minecraft:geometry'][0]['bones']
names = {bone['name'] for bone in bones}
for name in ('right_arm', 'left_arm', 'right_leg', 'left_leg', 'itemMainHand', 'itemOffHand'):
    assert name in names, f'mapped bone missing: {name}'
assert len(names) == len(bones), 'duplicate bone names after mapping'
assert 'animations' in anims and anims['animations'], 'animation set empty'
for clip in anims['animations'].values():
    for bone_name in clip.get('bones', {}):
        assert bone_name not in {'rightArm', 'leftArm', 'rightLeg', 'leftLeg', 'rightItem', 'leftItem'}, f'unmapped animation bone: {bone_name}'
print('PASS: stage-two geometry and animation JSON parse')
print('PASS: six primary limb/item bones use native-compatible names')
print('PASS: mapped geometry bone names are unique')
print('PASS: animation tracks use the mapped limb/item names')
print('NOTE: this is resource preparation only; renderer/model selection is not wired yet')
