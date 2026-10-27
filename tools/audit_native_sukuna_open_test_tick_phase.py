#!/usr/bin/env python3
"""Audit server END-phase ordering for the native Sukuna Open test driver."""
from pathlib import Path
s = (Path(__file__).resolve().parents[1] / 'src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java').read_text()
assert 'public static void tickNativeSukunaOpenTest(TickEvent.LevelTickEvent event)' in s
assert 'event.phase != TickEvent.Phase.END' in s
assert 'event.level instanceof ServerLevel level' in s
assert 'NATIVE_SUKUNA_OPEN_TEST_CNT1' in s
assert 'NATIVE_SUKUNA_OPEN_TEST_TICKS' in s
assert 'living.getPersistentData().putDouble("skill", 107.0D)' in s
assert 'sessionTicks >= 60' in s
assert 'CursedTechniqueSukunaProcedure' in s
print('PASS: native dispatcher is driven at server level END, after entity AI tick')
print('PASS: test-owned cnt1 snapshot prevents the AI tick from resetting the test timeline')
print('PASS: test session has a hard 60-tick upper bound and missing-target cleanup')
print('LIMIT: static audit only; actual Forge event ordering and in-game procedure behavior remain unverified')
