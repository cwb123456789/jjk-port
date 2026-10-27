from pathlib import Path

root = Path(__file__).resolve().parents[1]
prototype = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java').read_text(encoding='utf-8')
hands = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoDomainHandsEntity.java').read_text(encoding='utf-8')
barrier = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoDomainBarrierEntity.java').read_text(encoding='utf-8')
checks = {
    'active intro schedules visual repair after loading': 'domainVisualRepairPending = true;' in prototype,
    'repair runs on the first eligible server tick': 'if (!level().isClientSide && domainVisualRepairPending && level() instanceof ServerLevel serverLevel)' in prototype,
    'repair retries after transient insertion failure': 'domainVisualRepairPending = !ensureDomainVisuals(serverLevel);' in prototype and 'private boolean ensureDomainVisuals(ServerLevel serverLevel)' in prototype,
    'repair checks hand UUID against the expected entity type': 'if (!(hands instanceof MahitoDomainHandsEntity)) spawnDomainHands(serverLevel, elapsedTicks);' in prototype,
    'repair checks barrier UUID against the expected entity type': 'if (!(barrier instanceof MahitoDomainBarrierEntity)) spawnDomainBarrier(serverLevel, elapsedTicks);' in prototype,
    'repair does not call the domain pulse entry point': 'private boolean ensureDomainVisuals(ServerLevel serverLevel)' in prototype and 'expandTransfiguredDomain();' not in prototype.split('private boolean ensureDomainVisuals(ServerLevel serverLevel)', 1)[1].split('private void spawnDomainHands', 1)[0],
    'restored age follows remaining intro time': '60 - domainIntroTicks' in prototype,
    'hands expose bounded restored-age setter': 'public void restoreAge(int restoredAge)' in hands and 'Math.min(LIFETIME_TICKS, restoredAge)' in hands,
    'barrier exposes bounded restored-age setter': 'public void restoreAge(int restoredAge)' in barrier and 'Math.min(LIFETIME_TICKS, restoredAge)' in barrier,
    'normal intro expiration disables pending repair': 'domainVisualRepairPending = false;\n            // The opening timer is the authoritative lifetime boundary.' in prototype,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
print(f'Result: {sum(checks.values())}/{len(checks)} checks passed')
raise SystemExit(0 if all(checks.values()) else 1)
