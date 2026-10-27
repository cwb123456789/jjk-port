#!/usr/bin/env python3
"""Static audit for stale Fuga charge ownership cleanup (r65)."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = (root / "src/main/java/com/jujutsuenhancement/entity/HeianSukunaEntity.java").read_text()
checks = {
    "server player lookup is scoped to server level": "level() instanceof ServerLevel serverLevel" in source and "serverLevel.getServer().getPlayerList().getPlayer(fugaChargerUuid)" in source,
    "disconnect or missing player cancels charge": "charger == null" in source and "cancelFugaCharge();" in source,
    "dead player cancels charge": "!charger.isAlive()" in source,
    "dimension change cancels charge": "charger.level() != level()" in source,
    "both charging and ready windows are guarded": "fugaChargeTicksRemaining > 0 || fugaReadyTicksRemaining > 0" in source,
    "charge cleanup clears charger UUID": "fugaChargerUuid = null;" in source and "public void cancelFugaCharge()" in source,
    "cleanup runs only on server tick": "if (!level().isClientSide)" in source,
    "existing charge duration remains unchanged": "FUGA_CHARGE_TICKS = 55" in source,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'} {label}")
print(f"SUMMARY: {sum(checks.values())}/{len(checks)} passed")
raise SystemExit(0 if all(checks.values()) else 1)
