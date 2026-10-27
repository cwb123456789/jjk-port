#!/usr/bin/env python3
"""Static guard for the flame arrow's interaction-target self-hit regression."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
projectile = (root / "src/main/java/com/jujutsuenhancement/entity/SukunaFlameArrowEntity.java").read_text()
bridge = (root / "src/main/java/com/jujutsuenhancement/integration/JujutsuCraftSkillBridge.java").read_text()
checks = {
    "projectile records ignored entity UUID": "private UUID ignoredEntityUuid;" in projectile,
    "launch accepts ignored entity": "launch(Entity owner, Entity ignoredEntity, Vec3 direction)" in projectile,
    "collision filter excludes ignored UUID": "!ignoredEntityUuid.equals(entity.getUUID())" in projectile,
    "ignored UUID is persisted": 'tag.putUUID("IgnoredEntity", ignoredEntityUuid)' in projectile,
    "ignored UUID is restored": 'tag.getUUID("IgnoredEntity")' in projectile,
    "bridge excludes clicked Sukuna": "arrow.launch(player, sukuna, direction);" in bridge,
}
for label, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {label}")
if not all(checks.values()):
    raise SystemExit(1)
print(f"{sum(checks.values())}/{len(checks)} checks passed")
