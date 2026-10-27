#!/usr/bin/env python3
"""Static regression checks for server-side Fuga splash damage."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = (root / 'src/main/java/com/jujutsuenhancement/entity/SukunaFlameArrowEntity.java').read_text()
checks = {
    'impact receives owner and direct-hit context': 'impact(serverLevel, hitEntity.position(), owner, hitEntity)' in source and 'impact(serverLevel, blockHit.getType()' in source,
    'splash damage is server-side in ServerLevel impact handler': 'private void impact(ServerLevel level, Vec3 at, Entity owner, Entity directHit)' in source,
    'blast has finite radius': 'final double radius = 3.0D;' in source and 'distanceSqr > radiusSqr' in source,
    'direct hit is not damaged twice': 'if (candidate == directHit) continue;' in source,
    'owner and charging player are excluded': 'ownerUuid.equals(candidate.getUUID())' in source and 'ignoredEntityUuid.equals(candidate.getUUID())' in source,
    'allied entities are excluded': 'owner.isAlliedTo(candidate)' in source,
    'splash attribution remains owner-aware': 'damageSources().indirectMagic(this, damageOwner)' in source,
    'splash fire duration is bounded': 'livingTarget.setSecondsOnFire(3)' in source,
}
for name, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {name}")
print(f"Static checks: {sum(checks.values())}/{len(checks)} passed")
raise SystemExit(0 if all(checks.values()) else 1)
