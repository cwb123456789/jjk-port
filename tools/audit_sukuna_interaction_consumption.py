#!/usr/bin/env python3
"""Static regression guard: custom Heian Sukuna interactions are consumed consistently."""
from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'src/main/java/com/jujutsuenhancement/integration/JujutsuCraftSkillBridge.java'
s = p.read_text(encoding='utf-8')
method = s[s.index('public static void onEntityInteract'):]
guard = method.index('if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()) return;')
cancel = method.index('event.setCanceled(true);', guard)
first_branch = method.index('if (player.isCrouching())')
checks = {
    'server/client gate precedes event consumption': guard < cancel,
    'interaction is canceled before domain factory or cooldown branches': cancel < first_branch and cancel < method.index('ModEntities.MALEVOLENT_SHRINE.get().create(player.level())'),
    'failed domain factory cannot fall through to vanilla': cancel < method.index('if (shrine == null) return;'),
    'failed projectile spawn cannot fall through to vanilla': cancel < method.index('if (arrow == null)'),
    'projectile commit still depends on successful spawn': method.index('if (player.serverLevel().addFreshEntity(arrow)) {') < method.index('sukuna.commitFugaRelease(now, SINGLE_COOLDOWN_TICKS);'),
}
for name, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {name}")
if not all(checks.values()): raise SystemExit(1)
print('PASS: static interaction-consumption audit; no runtime claim.')
