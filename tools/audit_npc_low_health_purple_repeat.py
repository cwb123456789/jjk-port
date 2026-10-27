#!/usr/bin/env python3
"""Static checks for repeatable low-health Gojo NPC Purple, separate from 200% Purple."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
s = (ROOT / 'src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java').read_text(encoding='utf-8')
checks = {
    'uses independent cooldown timestamp': 'NPC_LOW_HEALTH_PURPLE_READY_AT' in s and 'NPC_LOW_HEALTH_PURPLE_COOLDOWN_TICKS = 40' in s,
    'low-health trigger is below or equal to 20 percent': 'gojo.getHealth() <= gojo.getMaxHealth() * 0.20F' in s,
    'low-health shot waits for cooldown': 'now >= emergencyReadyAt' in s,
    'repeat cooldown committed only after projectile spawn succeeds': s.index('if (level.addFreshEntity(emergencyPurple))') < s.index('putLong(NPC_LOW_HEALTH_PURPLE_READY_AT'),
    'low-health shot does not mark one-shot 200 percent as used': 'putBoolean(NPC_PURPLE_USED, true)' not in s[s.index('long emergencyReadyAt'):s.index('if (gojo.getPersistentData().getBoolean(NPC_PURPLE_USED)) return;')],
    'low-health shot is regular Purple, not charged 200 percent': 'emergencyPurple.setCharged200Percent()' not in s,
    'emergency shot does not clear independent 200 percent charge or target lock': 'Keep the independent 200% Purple charge and locked target intact.' in s and 'gojo.getPersistentData().remove(NPC_CHARGE_START);' not in s[s.index('long emergencyReadyAt'):s.index('if (gojo.getPersistentData().getBoolean(NPC_PURPLE_USED)) return;')],
    'fresh Meguna encounter takes priority over low-health fallback': 'boolean prioritizeChargedPurple' in s and '&& findNearbyPossessedMegumi(gojo, level) != null;' in s,
    'charge start and priority use the same target finder': s.count('findNearbyPossessedMegumi(gojo, level)') >= 2,
    '200 percent one-shot remains a separate later gate': 'if (gojo.getPersistentData().getBoolean(NPC_PURPLE_USED)) return;' in s,
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f'PASS: {len(checks)} repeatable low-health Purple checks')
