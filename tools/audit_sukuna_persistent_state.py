#!/usr/bin/env python3
"""Static regression checks for Heian Sukuna cooldown/combo persistence."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "src/main/java/com/jujutsuenhancement/entity/HeianSukunaEntity.java").read_text(encoding="utf-8")
checks = {
    "basic cooldown saved": 'tag.putLong("NextBasicAttackGameTime", nextBasicAttackGameTime)' in source,
    "domain cooldown saved": 'tag.putLong("NextDomainGameTime", nextDomainGameTime)' in source,
    "combo sequence saved": 'tag.putInt("AttackSequence", Math.floorMod(attackSequence, ATTACKS.length))' in source,
    "basic cooldown restored": 'nextBasicAttackGameTime = Math.max(0L, tag.getLong("NextBasicAttackGameTime"))' in source,
    "domain cooldown restored": 'nextDomainGameTime = Math.max(0L, tag.getLong("NextDomainGameTime"))' in source,
    "combo sequence restored and normalized": 'attackSequence = Math.floorMod(tag.getInt("AttackSequence"), ATTACKS.length)' in source,
    "visual animation reset on load": 'entityData.set(SKILL_ANIMATION, 0);' in source and 'skillAnimationTicks = 0;' in source,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
if not all(checks.values()):
    raise SystemExit(1)
print(f"PASS: {len(checks)} Sukuna persistence checks")
