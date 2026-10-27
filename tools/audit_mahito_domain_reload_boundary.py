from pathlib import Path
root = Path(__file__).resolve().parents[1]
prototype = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java').read_text(encoding='utf-8')
barrier = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoDomainBarrierEntity.java').read_text(encoding='utf-8')
hands = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoDomainHandsEntity.java').read_text(encoding='utf-8')
checks = {
    'reload boundary restores a one-tick transition rather than skipping effects': 'transformAnimationTicks = 1;' in prototype and 'Skipping directly to the intro flag after' in prototype,
    'reload boundary clears stale intro flag before normal transition': 'entityData.set(DOMAIN_INTRO_PLAYING, false);\n            domainIntroTicks = 0;' in prototype,
    'barrier age is clamped to legal lifetime bounds': 'age = Math.max(0, Math.min(LIFETIME_TICKS, tag.getInt("BarrierAge")));' in barrier,
    'hands age is clamped to legal lifetime bounds': 'age = Math.max(0, Math.min(LIFETIME_TICKS, tag.getInt("HandsAge")));' in hands,
    'barrier expires server-side at lifetime boundary': 'if (!level().isClientSide && ++age >= LIFETIME_TICKS) discard();' in barrier,
    'hands expires server-side at lifetime boundary': 'if (!level().isClientSide && ++age >= LIFETIME_TICKS) discard();' in hands,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
print(f'Result: {sum(checks.values())}/{len(checks)} checks passed')
raise SystemExit(0 if all(checks.values()) else 1)
