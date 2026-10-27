#!/usr/bin/env python3
"""Static audit for Mahito domain visual test commands and harmless short-lived entities."""
from pathlib import Path
import re, sys
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/jujutsuenhancement"
cmd = (JAVA / "integration/PrototypeCommands.java").read_text()
checks, errors = [], []
def check(ok, message):
    (checks if ok else errors).append(("PASS: " if ok else "FAIL: ") + message)

expected = {
    "mahito_domain_visual_test": "spawnMahitoDomainVisuals",
    "mahito_domain_hands_test": "spawnMahitoDomainPart",
    "mahito_domain_barrier_test": "spawnMahitoDomainPart",
}
for literal, method in expected.items():
    pattern = rf'Commands\.literal\("{re.escape(literal)}"\).*?\.executes\(ctx -> {method}\('
    check(re.search(pattern, cmd, re.S) is not None, f"/{literal} routes to {method}")
check('if (spawned == 2)' in cmd and 'level.addFreshEntity(hands)' in cmd and 'level.addFreshEntity(barrier)' in cmd,
      "combined test reports partial entity-spawn failure")
check('level.addFreshEntity(visual)' in cmd and '视觉实体未能加入世界' in cmd,
      "single-part test reports entity insertion failure")
for filename, age_key in [("MahitoDomainHandsEntity.java", '"HandsAge"'), ("MahitoDomainBarrierEntity.java", '"BarrierAge"')]:
    src = (JAVA / "entity" / filename).read_text()
    check("private static final int LIFETIME_TICKS = 60;" in src, f"{filename} expires after 60 ticks")
    check("if (!level().isClientSide && ++age >= LIFETIME_TICKS) discard();" in src, f"{filename} expires server-side")
    check(age_key in src, f"{filename} persists age under a dedicated NBT key")
    check("hurt(" not in src and "LivingEntity" not in src, f"{filename} has no direct damage implementation")
print("\n".join(checks + ["ERROR: " + e for e in errors]))
print(f"SUMMARY: {len(checks)} passed, {len(errors)} errors")
sys.exit(1 if errors else 0)
