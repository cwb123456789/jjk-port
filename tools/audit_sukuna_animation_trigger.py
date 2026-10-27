#!/usr/bin/env python3
"""Audit that Heian Sukuna's synchronized attack states reference imported animations."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/jujutsuenhancement/entity/HeianSukunaEntity.java'
BRIDGE = ROOT / 'src/main/java/com/jujutsuenhancement/integration/JujutsuCraftSkillBridge.java'
ANIMATION = ROOT / 'src/main/resources/assets/jujutsuenhancement/animations/heian_sukuna.animation.json'


def main():
    java = JAVA.read_text(encoding='utf-8')
    bridge = BRIDGE.read_text(encoding='utf-8')
    animations = json.loads(ANIMATION.read_text(encoding='utf-8')).get('animations', {})
    refs = re.findall(r'RawAnimation\.begin\(\)\.thenPlay\("([^"]+)"\)', java)
    missing = [name for name in refs if name not in animations]
    required = [f'animation.heian_sukuna.attack{i}' for i in range(1, 6)]
    missing_required = [name for name in required if name not in animations]
    checks = {
        'synced entity data declared': 'SynchedEntityData.defineId(HeianSukunaEntity.class' in java,
        'server-only animation state mutation': 'if (level().isClientSide) return;' in java,
        'state timer resets animation': 'entityData.set(SKILL_ANIMATION, 0);' in java,
        'bridge commits attack only after accepted custom-arrow spawn': 'if (player.serverLevel().addFreshEntity(arrow)) {' in bridge and 'sukuna.commitFugaRelease(now, SINGLE_COOLDOWN_TICKS);' in bridge,
        'entity release commit sets synchronized release state': 'entityData.set(SKILL_ANIMATION, 8);' in java and 'FUGA_RELEASE' in java,
        'all five attack clips exist': not missing_required,
        'all Java attack animation refs exist': not missing,
    }
    for name, ok in checks.items():
        print(f'{"PASS" if ok else "FAIL"}: {name}')
    if missing:
        print('Missing animation refs:', ', '.join(missing))
    return 0 if all(checks.values()) else 1

if __name__ == '__main__':
    raise SystemExit(main())
