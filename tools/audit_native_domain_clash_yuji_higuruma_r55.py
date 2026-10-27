#!/usr/bin/env python3
"""Regression checks for native Yuji/Higuruma domain recognition."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
s = (root / 'src/main/java/com/jujutsuenhancement/integration/NativeDomainClashBridge.java').read_text(encoding='utf-8')
checks = {
    'Yuji base entity recognized': 'net.mcreator.jujutsucraft.entity.ItadoriYujiEntity' in s,
    'Yuji Shibuya entity recognized': 'net.mcreator.jujutsucraft.entity.ItadoriYujiShibuyaEntity' in s,
    'Yuji Shinjuku entity recognized': 'net.mcreator.jujutsucraft.entity.ItadoriYujiShinjukuEntity' in s,
    'Yuji domain skill 2120 recognized': 'rawSkill == 2120.0D' in s,
    'Higuruma entity recognized': 'net.mcreator.jujutsucraft.entity.HigurumaHiromiEntity' in s,
    'Higuruma domain skill 2720 recognized': 'rawSkill == 2720.0D' in s,
    'server-side only remains enforced': 'entity.level().isClientSide' in s,
    'no native skill mutation introduced': 'getPersistentData().putDouble("skill"' not in s,
}
for name, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {name}")
print(f"Yuji/Higuruma domain support audit: {sum(checks.values())}/{len(checks)}")
raise SystemExit(0 if all(checks.values()) else 1)
