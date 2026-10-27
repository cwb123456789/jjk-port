#!/usr/bin/env python3
"""Ensure cooldowns and combo state are owned by the Sukuna entity, not players."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
entity = (root / 'src/main/java/com/jujutsuenhancement/entity/HeianSukunaEntity.java').read_text(encoding='utf-8')
bridge = (root / 'src/main/java/com/jujutsuenhancement/integration/JujutsuCraftSkillBridge.java').read_text(encoding='utf-8')
checks = {
    'entity owns basic attack cooldown': 'private long nextBasicAttackGameTime;' in entity,
    'entity owns domain cooldown': 'private long nextDomainGameTime;' in entity,
    'entity owns attack sequence': 'private int attackSequence;' in entity,
    'basic attack cooldown checked before projectile spawn': bridge.index('if (!sukuna.canUseBasicAttack(now))') < bridge.index('ModEntities.SUKUNA_FLAME_ARROW.get().create(player.level())'),
    'domain cooldown checked before domain creation': bridge.index('if (!sukuna.canUseDomain(now))') < bridge.index('ModEntities.MALEVOLENT_SHRINE.get().create(player.level())'),
    'basic attack state committed only after spawn success': bridge.index('if (player.serverLevel().addFreshEntity(arrow)) {') < bridge.index('sukuna.commitFugaRelease(now, SINGLE_COOLDOWN_TICKS);'),
    'domain cooldown committed only after entity added': bridge.index('if (player.serverLevel().addFreshEntity(shrine)) {') < bridge.index('sukuna.commitDomain(now, DOMAIN_COOLDOWN_TICKS);'),
    'player persistent data no longer stores Sukuna cooldown/sequence': 'getPersistentData().putLong' not in bridge and 'getPersistentData().putInt' not in bridge,
}
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if not all(checks.values()):
    raise SystemExit(1)
print('PASS: entity-owned cooldown/sequence static audit; not a substitute for compilation or in-game testing.')
