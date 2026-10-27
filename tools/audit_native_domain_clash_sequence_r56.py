#!/usr/bin/env python3
"""Static checks for the short native-domain clash visual sequence."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
s = (root / 'src/main/java/com/jujutsuenhancement/integration/NativeDomainClashBridge.java').read_text(encoding='utf-8')
checks = {
    'pair state tracks start and last seen': 'class PairState' in s and 'startedAt' in s and 'lastSeen' in s,
    'sequence bounded to 40 ticks': 'CLASH_SEQUENCE_TICKS = 40L' in s,
    'visual pulses throttled to 5 ticks': 'CLASH_PULSE_INTERVAL_TICKS = 5L' in s,
    'initial cue only on new overlap': 'playClashCue(level, entity, opponent)' in s and 'state == null || now - state.lastSeen > PAIR_RELEASE_GRACE_TICKS' in s,
    'follow-up pulses use electric sparks': 'ParticleTypes.ELECTRIC_SPARK' in s,
    'pair cache cleanup remains bounded': 'PAIR_STALE_TICKS' in s and 'ACTIVE_PAIRS.entrySet().iterator()' in s,
    'server-side guard remains': 'entity.level().isClientSide' in s,
    'does not claim or mutate winner skill state': 'getPersistentData().putDouble("skill"' not in s,
}
for name, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {name}")
print(f"Native domain clash sequence audit: {sum(checks.values())}/{len(checks)}")
raise SystemExit(0 if all(checks.values()) else 1)
