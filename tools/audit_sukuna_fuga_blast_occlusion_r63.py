#!/usr/bin/env python3
"""Static regression checks for wall-occluded Fuga splash targets."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'src/main/java/com/jujutsuenhancement/entity/SukunaFlameArrowEntity.java'
s = SOURCE.read_text(encoding='utf-8')
checks = {
    'blast still applies radius filtering before visibility test': s.index('if (distanceSqr > radiusSqr) continue;') < s.index('HitResult visibility = level.clip'),
    'visibility ray uses impact as origin': 'new ClipContext(at, targetCenter,' in s,
    'visibility ray collides with solid block shapes': 'ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this' in s,
    'occluded target is skipped': 'visibility.getType() == HitResult.Type.BLOCK' in s and 'continue;' in s[s.index('HitResult visibility = level.clip'):s.index('double distance = Math.sqrt(distanceSqr);')],
    'small epsilon avoids treating a block at target endpoint as a wall in front': 'at.distanceToSqr(visibility.getLocation()) + 1.0E-4D < distanceSqr' in s,
    'line-of-sight check occurs before splash damage': s.index('HitResult visibility = level.clip') < s.index('float damage = (float) (maxSplashDamage', s.index('private void impact(')),
    'direct hit remains excluded from splash': 'if (candidate == directHit) continue;' in s,
    'owner and allied targets remain excluded': 'owner.isAlliedTo(candidate)' in s,
}
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
print(f"Fuga blast occlusion audit: {sum(checks.values())}/{len(checks)} passed")
raise SystemExit(0 if all(checks.values()) else 1)
