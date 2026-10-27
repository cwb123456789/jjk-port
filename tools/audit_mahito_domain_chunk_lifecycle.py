from pathlib import Path

root = Path(__file__).resolve().parents[1]
prototype = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java').read_text(encoding='utf-8')
hands = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoDomainHandsEntity.java').read_text(encoding='utf-8')
barrier = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoDomainBarrierEntity.java').read_text(encoding='utf-8')

remove_body = prototype.split('@Override public void remove(Entity.RemovalReason reason)', 1)[1].split('private static void discardDomainVisual', 1)[0]
checks = {
    'prototype preserves visuals during chunk unload': 'reason != Entity.RemovalReason.UNLOADED_TO_CHUNK' in remove_body,
    'prototype preserves visuals during player unload': 'reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER' in remove_body,
    'actual removal still invokes visual cleanup': 'discardDomainVisual(serverLevel, domainHandsUuid);' in remove_body and 'discardDomainVisual(serverLevel, domainBarrierUuid);' in remove_body,
    'visual cleanup remains restricted to known effect types': 'visual instanceof MahitoDomainHandsEntity || visual instanceof MahitoDomainBarrierEntity' in prototype,
    'prototype saves hand UUID for reload tracking': 'tag.putUUID("DomainHandsUuid", domainHandsUuid)' in prototype,
    'prototype saves barrier UUID for reload tracking': 'tag.putUUID("DomainBarrierUuid", domainBarrierUuid)' in prototype,
    'hands persist age across chunk unload/reload': 'tag.putInt("HandsAge", age)' in hands and 'tag.getInt("HandsAge")' in hands,
    'barrier persists age across chunk unload/reload': 'tag.putInt("BarrierAge", age)' in barrier and 'tag.getInt("BarrierAge")' in barrier,
    'hands age is clamped to legal bounds on load': 'Math.max(0, Math.min(LIFETIME_TICKS, tag.getInt("HandsAge")))' in hands,
    'barrier age is clamped to legal bounds on load': 'Math.max(0, Math.min(LIFETIME_TICKS, tag.getInt("BarrierAge")))' in barrier,
    'hands expire server-side at the 60-tick boundary': 'if (!level().isClientSide && ++age >= LIFETIME_TICKS) discard();' in hands,
    'barrier expires server-side at the 60-tick boundary': 'if (!level().isClientSide && ++age >= LIFETIME_TICKS) discard();' in barrier,
    'domain end resets tracked UUIDs after cleanup': 'domainHandsUuid = null;\n                domainBarrierUuid = null;' in prototype,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
print(f'Result: {sum(checks.values())}/{len(checks)} checks passed')
raise SystemExit(0 if all(checks.values()) else 1)
