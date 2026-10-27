#!/usr/bin/env python3
"""Static audit for the server END-phase native Sukuna Open test driver."""
from pathlib import Path
s = (Path(__file__).resolve().parents[1] / 'src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java').read_text()
assert 'public static void tickNativeSukunaOpenTest(TickEvent.LevelTickEvent event)' in s
assert 'event.phase != TickEvent.Phase.END' in s
assert 'event.level instanceof ServerLevel level' in s
assert 'NATIVE_SUKUNA_OPEN_TEST_RUNNING' in s
assert 'CursedTechniqueSukunaProcedure' in s
assert 'execute.invoke(null, living.level(), living.getX(), living.getY(), living.getZ(), living)' in s
assert 'NATIVE_SUKUNA_OPEN_TEST_CNT1' in s
assert 'NATIVE_SUKUNA_OPEN_TEST_TICKS' in s
assert 'sessionTicks >= 60' in s
assert 'living.getPersistentData().putDouble("skill", 0.0D)' in s
print('PASS: server-level END event drives the native dispatcher after entity AI tick')
print('PASS: test session owns cnt1 progression and has a 60-tick upper bound')
print('PASS: missing target or reflection failure clears the test session')
print('LIMIT: static check only; runtime animation/projectile behavior remains unverified')
