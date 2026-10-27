from pathlib import Path

source = Path(__file__).resolve().parents[1] / "src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java"
s = source.read_text(encoding="utf-8")
checks = {
    "tracks spawned hands UUID only after successful add": "if (serverLevel.addFreshEntity(hands)) domainHandsUuid = hands.getUUID();" in s,
    "tracks spawned barrier UUID only after successful add": "if (serverLevel.addFreshEntity(barrier)) domainBarrierUuid = barrier.getUUID();" in s,
    "persists hands UUID": 'tag.putUUID("DomainHandsUuid", domainHandsUuid)' in s and 'tag.hasUUID("DomainHandsUuid")' in s,
    "persists barrier UUID": 'tag.putUUID("DomainBarrierUuid", domainBarrierUuid)' in s and 'tag.hasUUID("DomainBarrierUuid")' in s,
    "does not clean up on chunk unload": "reason != Entity.RemovalReason.UNLOADED_TO_CHUNK" in s,
    "does not clean up on player unload": "reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER" in s,
    "only discards recognized Mahito domain visuals": "visual instanceof MahitoDomainHandsEntity || visual instanceof MahitoDomainBarrierEntity" in s,
    "clears tracked UUIDs after cleanup": "domainHandsUuid = null;\n            domainBarrierUuid = null;" in s,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
print(f"Result: {sum(checks.values())}/{len(checks)} checks passed")
raise SystemExit(0 if all(checks.values()) else 1)
