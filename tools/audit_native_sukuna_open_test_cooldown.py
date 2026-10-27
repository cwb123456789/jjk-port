#!/usr/bin/env python3
"""Static audit for per-native-entity cooldown on the manual Sukuna Open test."""
from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java'
s = p.read_text()
assert 'NATIVE_SUKUNA_OPEN_TEST_READY_AT' in s
assert 'NATIVE_SUKUNA_OPEN_TEST_COOLDOWN_TICKS = 100L' in s
assert 'target.getPersistentData().getLong(NATIVE_SUKUNA_OPEN_TEST_READY_AT)' in s
assert 'if (now < readyAt)' in s
assert 'target.getPersistentData().putLong(NATIVE_SUKUNA_OPEN_TEST_READY_AT, now + NATIVE_SUKUNA_OPEN_TEST_COOLDOWN_TICKS)' in s
assert s.index('if (now < readyAt)') < s.index('target.getPersistentData().putDouble("skill", 107.0D)')
assert '冷却归属于该 NPC，不影响其他宿傩' in s
print('PASS: per-entity 100-tick cooldown checked before skill mutation')
print('PASS: cooldown is committed when the tick-driven test session is scheduled')
print('LIMIT: manual test guard only; does not add automatic NPC skill selection')
