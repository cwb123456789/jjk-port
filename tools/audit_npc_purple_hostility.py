#!/usr/bin/env python3
"""Static regression checks for Gojo NPC's one-shot 200% Purple target hostility."""
from pathlib import Path

source = Path(__file__).resolve().parents[1] / "src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java"
text = source.read_text(encoding="utf-8")
checks = {
    "acquisition selects Sukuna-possessed Megumi": '.filter(e -> e.getClass().getSimpleName().equals("SukunaFushiguroEntity"))' in text,
    "acquisition excludes allies": '.filter(e -> !gojo.isAlliedTo(e))' in text,
    "target is locked by UUID": 'putUUID(NPC_TARGET_UUID, possessedMegumi.getUUID())' in text,
    "locked target must remain alive": 'candidate != null && candidate.isAlive()' in text,
    "locked target must remain in same level": 'candidate.level() == level' in text,
    "locked target must remain in range": 'candidate.distanceToSqr(gojo) <= 32.0D * 32.0D' in text,
    "locked target must remain Sukuna-possessed Megumi": 'candidate.getClass().getSimpleName().equals("SukunaFushiguroEntity")' in text,
    "locked target is rechecked for hostility": '&& !gojo.isAlliedTo(candidate)' in text,
    "invalid target cancels charge": 'if (target == null) {\n            gojo.getPersistentData().remove(NPC_CHARGE_START);\n            gojo.getPersistentData().remove(NPC_TARGET_UUID);' in text,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
print(f"{len(checks)-len(failed)}/{len(checks)} checks passed")
raise SystemExit(bool(failed))
