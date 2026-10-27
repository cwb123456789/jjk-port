#!/usr/bin/env python3
"""Static guard for Hollow Purple damage attribution and unloaded-owner fallback."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "src/main/java/com/jujutsuenhancement/entity/HollowPurpleEntity.java").read_text()
checks = {
    "resolves owner UUID before damage": "Entity owner = ownerUuid == null ? null : this.level().getEntity(ownerUuid);" in source,
    "records projectile and owner in damage source": "this.damageSources().indirectMagic(this, owner)" in source,
    "fallback for unloaded owner": "target.hurt(this.damageSources().magic(), damage);" in source,
    "preserves hit de-duplication": "hitEntities.add(target.getUUID());" in source,
    "preserves ally exclusion": "if (owner != null && owner.isAlliedTo(target)) continue;" in source,
}
for label, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {label}")
if not all(checks.values()):
    raise SystemExit(1)
print(f"{sum(checks.values())}/{len(checks)} checks passed")
