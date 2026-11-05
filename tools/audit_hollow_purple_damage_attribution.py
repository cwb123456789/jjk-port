#!/usr/bin/env python3
"""Static guard for Hollow Purple owner attribution and friendly-fire filtering."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "src/main/java/com/jujutsuenhancement/entity/HollowPurpleEntity.java").read_text(encoding="utf-8")
checks = {
    "resolves owner UUID from server level before damage": "Entity owner = ownerUuid == null ? null : server.getEntity(ownerUuid);" in source,
    "records projectile and owner in damage source": "damageSources().indirectMagic(this, owner)" in source,
    "falls back to magic damage when owner is unavailable": "damageSources().magic()" in source,
    "excludes direct owner from damage": "e != owner" in source,
    "excludes owner allies from damage": "owner == null || !owner.isAlliedTo(e)" in source,
    "preserves Bedrock per-tick damage behavior": "hurtWithin(server, owner," in source,
}
for label, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {label}")
if not all(checks.values()):
    raise SystemExit(1)
print(f"{sum(checks.values())}/{len(checks)} checks passed")
