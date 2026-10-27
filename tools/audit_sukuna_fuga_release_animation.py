#!/usr/bin/env python3
"""Validate the Fuga release clip and its synchronized trigger."""
import json
from pathlib import Path
root = Path(__file__).resolve().parents[1]
java = (root / 'src/main/java/com/jujutsuenhancement/entity/HeianSukunaEntity.java').read_text()
bridge = (root / 'src/main/java/com/jujutsuenhancement/integration/JujutsuCraftSkillBridge.java').read_text()
anims = json.loads((root / 'src/main/resources/assets/jujutsuenhancement/animations/heian_sukuna.animation.json').read_text())['animations']
geo = json.loads((root / 'src/main/resources/assets/jujutsuenhancement/geo/heian_sukuna.geo.json').read_text())['minecraft:geometry'][0]
bones = {b['name'] for b in geo['bones']}
clip = anims.get('animation.fuga_release', {})
checks = {
    'release clip is imported': bool(clip),
    'release clip is non-looping': clip.get('loop') is False,
    'release clip has useful arm and head motion': {'leftArm', 'rightArm', 'head'}.issubset(clip.get('bones', {})),
    'all release channels target existing model bones': set(clip.get('bones', {})).issubset(bones),
    'release state maps to release clip': 'if (attack == 8) return state.setAndContinue(FUGA_RELEASE)' in java,
    'release timer matches 1.5-second clip': 'FUGA_RELEASE_TICKS = 30' in java and clip.get('animation_length') == 1.5,
    'release commit occurs only after spawn succeeds': bridge.index('if (player.serverLevel().addFreshEntity(arrow)) {') < bridge.index('sukuna.commitFugaRelease(now, SINGLE_COOLDOWN_TICKS);'),
}
for name, ok in checks.items(): print(f'{"PASS" if ok else "FAIL"}: {name}')
if not all(checks.values()): raise SystemExit(1)
print(f'{sum(checks.values())}/{len(checks)} checks passed')
