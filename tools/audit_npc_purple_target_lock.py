#!/usr/bin/env python3
"""Static regression checks for Gojo NPC charged-Purple target locking."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
s = (ROOT / 'src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java').read_text(encoding='utf-8')
checks = {
    'target UUID uses a single named key': 'putUUID(NPC_TARGET_UUID, possessedMegumi.getUUID())' in s and 'hasUUID(NPC_TARGET_UUID)' in s,
    'locked target must remain the Sukuna-possessed Megumi entity': 'candidate.getClass().getSimpleName().equals("SukunaFushiguroEntity")' in s,
    'locked target must remain alive and in the same level': 'candidate.isAlive() && candidate.level() == level' in s,
    'charge cancels if target leaves 32-block lock range': 'candidate.distanceToSqr(gojo) <= 32.0D * 32.0D' in s,
    'missing target cancels charge before projectile creation': s.index('if (target == null) {') < s.index('HollowPurpleEntity purple = ModEntities.HOLLOW_PURPLE.get().create(level);', s.index('if (target == null) {')),
    'cancellation clears charge and target state': 'gojo.getPersistentData().remove(NPC_CHARGE_START);\n            gojo.getPersistentData().remove(NPC_TARGET_UUID);' in s,
    'launch direction follows the locked target': 'target.getBoundingBox().getCenter().subtract(gojo.getEyePosition()).normalize()' in s,
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f'PASS: {len(checks)} NPC target-lock regression checks')
