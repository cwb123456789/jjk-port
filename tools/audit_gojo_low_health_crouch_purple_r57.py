#!/usr/bin/env python3
"""Static regression checks for Gojo's low-health crouch-triggered Purple."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
s = (root / 'src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java').read_text(encoding='utf-8')
checks = {
    'low-health branch requires crouching': 'if (!prioritizeChargedPurple && gojo.isCrouching() && gojo.getHealth() > 0.0F' in s,
    'health threshold remains 20 percent': 'gojo.getHealth() <= gojo.getMaxHealth() * 0.20F' in s,
    'repeat cooldown remains independent': 'NPC_LOW_HEALTH_PURPLE_COOLDOWN_TICKS = 40' in s and 'now + NPC_LOW_HEALTH_PURPLE_COOLDOWN_TICKS' in s,
    '200-percent encounter priority remains': 'prioritizeChargedPurple' in s and 'findNearbyPossessedMegumi(gojo, level)' in s,
    'charged-purple one-shot guard remains': 'if (gojo.getPersistentData().getBoolean(NPC_PURPLE_USED)) return;' in s,
    'projectile spawn still commits cooldown only on success': 'if (level.addFreshEntity(emergencyPurple))' in s and 'putLong(NPC_LOW_HEALTH_PURPLE_READY_AT' in s,
}
for name, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {name}")
print(f"Gojo low-health crouch Purple audit: {sum(checks.values())}/{len(checks)}")
raise SystemExit(0 if all(checks.values()) else 1)
