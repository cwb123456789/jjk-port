#!/usr/bin/env python3
"""Static regression checks for player charged-Purple state handling."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java").read_text(encoding="utf-8")
checks = {
    "charge start has a positive sentinel": "putLong(CHARGE_START_KEY, Math.max(1L, now))" in source,
    "inactive charge is ignored": "if (start <= 0L) return;" in source,
    "death cancels pending charge": "if (!player.isAlive())" in source and 'remove(CHARGE_START_KEY);\n            player.sendSystemMessage(Component.literal("你已倒下，200% 虚式茈蓄力中断。"))' in source,
    "charge still waits full configured duration": "player.level().getGameTime() - start < CHARGE_TICKS" in source,
    "charge state removed before firing": "remove(CHARGE_START_KEY);\n        long now = player.level().getGameTime();" in source,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f"PASS: {len(checks)} charged-Purple state regression checks")
