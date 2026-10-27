from pathlib import Path
root = Path(__file__).resolve().parents[1]
prototype = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java').read_text(encoding='utf-8')
hands = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoDomainHandsEntity.java').read_text(encoding='utf-8')
barrier = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoDomainBarrierEntity.java').read_text(encoding='utf-8')
checks = {
    'domain intro timer remains 60 ticks': 'domainIntroTicks = 60;' in prototype,
    'intro completion clears the synced animation flag': 'domainIntroTicks > 0 && --domainIntroTicks == 0) {\n            entityData.set(DOMAIN_INTRO_PLAYING, false);' in prototype,
    'intro completion removes any still-loaded hand effect': 'discardDomainVisual(serverLevel, domainHandsUuid);' in prototype,
    'intro completion removes any still-loaded barrier effect': 'discardDomainVisual(serverLevel, domainBarrierUuid);' in prototype,
    'UUID tracking is reset after lifetime cleanup': 'domainHandsUuid = null;\n                domainBarrierUuid = null;' in prototype,
    'hands retain their own 60-tick failsafe': 'private static final int LIFETIME_TICKS = 60;' in hands and '++age >= LIFETIME_TICKS' in hands,
    'barrier retains its own 60-tick failsafe': 'private static final int LIFETIME_TICKS = 60;' in barrier and '++age >= LIFETIME_TICKS' in barrier,
    'cleanup remains type-restricted': 'visual instanceof MahitoDomainHandsEntity || visual instanceof MahitoDomainBarrierEntity' in prototype,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
print(f'Result: {sum(checks.values())}/{len(checks)} checks passed')
raise SystemExit(0 if all(checks.values()) else 1)
