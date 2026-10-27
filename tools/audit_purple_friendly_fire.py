#!/usr/bin/env python3
"""Static regression checks for Hollow Purple owner/alliance damage filtering."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "src/main/java/com/jujutsuenhancement/entity/HollowPurpleEntity.java").read_text(encoding="utf-8")
checks = {
    "projectile resolves owner from UUID": "Entity owner = ownerUuid == null ? null : this.level().getEntity(ownerUuid);" in source,
    "owner UUID remains explicitly excluded": "!ownerUuid.equals(e.getUUID())" in source,
    "allied targets are skipped before damage": "if (owner != null && owner.isAlliedTo(target)) continue;" in source,
    "damage occurs after alliance filter": source.index("if (owner != null && owner.isAlliedTo(target)) continue;") < source.index("target.hurt(this.damageSources().magic(), damage);"),
    "owner UUID is saved for projectile persistence": 'tag.putUUID("Owner", ownerUuid)' in source,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f"PASS: {len(checks)} friendly-fire regression checks")
