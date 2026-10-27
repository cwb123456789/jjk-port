#!/usr/bin/env python3
"""Static guardrails for the initial native domain clash cue bridge."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "src/main/java/com/jujutsuenhancement/integration/NativeDomainClashBridge.java").read_text(encoding="utf-8")
checks = {
    "server-side only": "entity.level().isClientSide" in source,
    "native Mahito domain ID 1520": 'className.equals(MAHITO) && rawSkill == 1520.0D' in source,
    "native Sukuna domain ID 120": 'rawSkill == 120.0D' in source and "SUKUNA_PERFECT" in source,
    "native Gojo domain ID 220": 'className.equals(GOJO) && rawSkill == 220.0D' in source,
    "native Yuji variants domain ID 2120": all(token in source for token in ["ITADORI_SHIBUYA", "ITADORI_SHINJUKU", "rawSkill == 2120.0D"]),
    "native Higuruma domain ID 2720": 'className.equals(HIGURUMA) && rawSkill == 2720.0D' in source,
    "nearby pair distance bounded": "CLASH_RADIUS * CLASH_RADIUS" in source,
    "pair processing de-duplicated": "compareUuid(entity.getUUID(), opponent.getUUID()) >= 0" in source,
    "one cue per overlap episode": "PAIR_RELEASE_GRACE_TICKS = 10L" in source and "ACTIVE_PAIRS.put(key, state)" in source,
    "stale pair state cleanup": "PAIR_STALE_TICKS = 200L" in source and "ACTIVE_PAIRS.entrySet().iterator()" in source,
    "does not replay cue during sustained clash": "now - state.lastSeen > PAIR_RELEASE_GRACE_TICKS" in source and "playClashPulse(level, entity, opponent)" in source,
    "clash cue includes particles": "ParticleTypes.PORTAL" in source and "ParticleTypes.END_ROD" in source,
    "native skill state not mutated": 'getPersistentData().putDouble("skill"' not in source and 'setAnimation(' not in source,
    "limitations documented in source": "without replacing or mutating JujutsuCraft's skill state" in source,
}
for label, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {label}")
print(f"Native domain clash bridge audit: {sum(checks.values())}/{len(checks)}")
raise SystemExit(0 if all(checks.values()) else 1)
