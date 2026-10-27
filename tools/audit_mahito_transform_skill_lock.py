#!/usr/bin/env python3
"""Static regression audit: Mahito's transformed skills stay locked through opening animations."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
src = (ROOT / 'src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java').read_text()
checks = []
def check(ok, label):
    checks.append((ok, label))
check('if (!level().isClientSide && entityData.get(TRANSFORMED)) {' in src,
      'transformed interaction branch remains server-authoritative')
needle = 'if (entityData.get(TRANSFORM_ANIMATION_PLAYING) || entityData.get(DOMAIN_INTRO_PLAYING)) {'
check(needle in src, 'stage-two and domain-intro states both gate skills')
block = src.split(needle, 1)[1].split('\n            }', 1)[0]
check('return InteractionResult.sidedSuccess(level().isClientSide);' in block,
      'interaction is consumed without applying a skill during opening')
check(src.index(needle) < src.index('if (player.isCrouching())', src.index(needle)),
      'animation gate executes before both transformed skill branches')
check('transformAnimationTicks = 110;' in src and 'domainIntroTicks = 60;' in src,
      'existing transformation and domain intro durations are preserved')
for ok, label in checks:
    print(('PASS: ' if ok else 'FAIL: ') + label)
print(f'SUMMARY: {sum(ok for ok, _ in checks)}/{len(checks)} passed')
sys.exit(0 if all(ok for ok, _ in checks) else 1)
