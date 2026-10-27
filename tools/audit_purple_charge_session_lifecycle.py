#!/usr/bin/env python3
"""Static guard for charged Hollow Purple session lifecycle cleanup."""
from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java'
s = p.read_text()
checks = {
    'login event clears stale charge start': 'onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)' in s and 'event.getEntity().getPersistentData().remove(CHARGE_START_KEY);' in s,
    'dimension change cancels charge': 'onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)' in s,
    'dimension change does not clear cooldown': 'onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)' in s and 'remove(CHARGE_READY_KEY)' not in s,
    'normal tick still cancels charge on death': 'if (!player.isAlive())' in s and 'remove(CHARGE_START_KEY)' in s,
    'five-second charge remains configured': 'private static final int CHARGE_TICKS = 100;' in s,
    'charged release still occurs after elapsed charge': 'player.level().getGameTime() - start < CHARGE_TICKS' in s and 'firePurple(player, true);' in s,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'} {label}")
if not all(checks.values()): raise SystemExit(1)
