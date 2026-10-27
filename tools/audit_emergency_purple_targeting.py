#!/usr/bin/env python3
"""Static checks for Gojo NPC low-health Purple targeting and cooldown behavior."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
s = (ROOT / 'src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java').read_text(encoding='utf-8')
checks = {
    'low-health trigger delegates to target selector': 'Entity target = findEmergencyPurpleTarget(gojo, level);' in s,
    'selector searches within 32 blocks': 'gojo.getBoundingBox().inflate(32.0D)' in s and 'e.distanceToSqr(gojo) <= 32.0D * 32.0D' in s,
    'selector only chooses living entities': 'e instanceof LivingEntity' in s,
    'selector excludes Gojo and allies': 'e != gojo && !gojo.isAlliedTo(e)' in s,
    'Sukuna-possessed Megumi is preferred': 'filter(e -> e.getClass().getSimpleName().equals("SukunaFushiguroEntity"))' in s,
    'nearest Sukuna is selected before fallback': s.index('if (sukuna != null) return sukuna;') < s.index('return candidates.stream()', s.index('if (sukuna != null) return sukuna;')),
    'low-health attack has a dedicated repeat cooldown': 'NPC_LOW_HEALTH_PURPLE_READY_AT' in s and 'NPC_LOW_HEALTH_PURPLE_COOLDOWN_TICKS = 40' in s,
    'cooldown advances only after successful spawn': s.index('if (level.addFreshEntity(emergencyPurple))') < s.index('putLong(NPC_LOW_HEALTH_PURPLE_READY_AT'),
    'low-health attack does not consume 200 percent one-shot': 'putBoolean(NPC_PURPLE_USED, true)' not in s[s.index('long emergencyReadyAt'):s.index('if (gojo.getPersistentData().getBoolean(NPC_PURPLE_USED)) return;')],
    'emergency shot preserves independent charged-Purple timer': 'Keep the independent 200% Purple charge and locked target intact.' in s and 'gojo.getPersistentData().remove(NPC_CHARGE_START);' not in s[s.index('long emergencyReadyAt'):s.index('if (gojo.getPersistentData().getBoolean(NPC_PURPLE_USED)) return;')],
    'emergency shot preserves independent charged-Purple target lock': 'Keep the independent 200% Purple charge and locked target intact.' in s and 'gojo.getPersistentData().remove(NPC_TARGET_UUID);' not in s[s.index('long emergencyReadyAt'):s.index('if (gojo.getPersistentData().getBoolean(NPC_PURPLE_USED)) return;')],
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f'PASS: {len(checks)} emergency-Purple target checks')
