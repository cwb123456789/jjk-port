#!/usr/bin/env python3
"""Static guard for projectile damage attribution and anonymous fallback."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "src/main/java/com/jujutsuenhancement/entity/SukunaFlameArrowEntity.java").read_text()
checks = {
    "resolves projectile owner before collision damage": "Entity owner = ownerUuid == null ? null : serverLevel.getEntity(ownerUuid);" in source,
    "damage source identifies the projectile": "damageSources().indirectMagic(this, damageOwner)" in source,
    "resolved owner is used as attacker": "Entity damageOwner = owner != null ? owner : this;" in source,
    "existing damage amount is retained": "hitEntity.hurt(damageSources().indirectMagic(this, damageOwner), DAMAGE);" in source,
    "fire effect remains on living targets": "livingTarget.setSecondsOnFire(4)" in source,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
if not all(checks.values()):
    raise SystemExit(1)
print(f"{sum(checks.values())}/{len(checks)} checks passed")
