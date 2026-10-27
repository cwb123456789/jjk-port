#!/usr/bin/env python3
"""Static checks for Fuga impact sound and radial knockback feedback."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = (root / 'src/main/java/com/jujutsuenhancement/entity/SukunaFlameArrowEntity.java').read_text()
checks = {
    'impact feedback is server-side': 'private void impact(ServerLevel level, Vec3 at, Entity owner, Entity directHit)' in source,
    'explosion sound is emitted at impact': 'level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE' in source,
    'fire-charge sound layers with explosion': 'SoundEvents.FIRECHARGE_USE' in source,
    'knockback follows accepted damage only': source.index('if (candidate.hurt(damageSources().indirectMagic(this, damageOwner), damage))') < source.index('candidate.push(horizontal.x'),
    'knockback has bounded upward impulse': 'Math.max(0.12D, Math.min(0.38D' in source,
    'horizontal impulse is normalized and bounded': 'horizontal.normalize().scale(0.72D)' in source,
    'velocity sync flag is marked': 'candidate.hurtMarked = true;' in source,
    'direct hit still excluded from splash': 'if (candidate == directHit) continue;' in source,
    'owner and ignored charger still excluded': 'ownerUuid.equals(candidate.getUUID())' in source and 'ignoredEntityUuid.equals(candidate.getUUID())' in source,
    'blast remains finite': 'final double radius = 3.0D;' in source and 'distanceSqr > radiusSqr' in source,
}
for name, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {name}")
print(f"Static checks: {sum(checks.values())}/{len(checks)} passed")
raise SystemExit(0 if all(checks.values()) else 1)
