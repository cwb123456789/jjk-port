from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java').read_text(encoding='utf-8')
checks = {
    'cleanup is deferred via a dedicated transient flag': 'private boolean domainVisualCleanupPending;' in source,
    'pending cleanup runs only on server tick': 'if (!level().isClientSide && domainVisualCleanupPending && level() instanceof ServerLevel serverLevel)' in source,
    'both saved visual UUIDs are cleaned': 'discardDomainVisual(serverLevel, domainHandsUuid);\n            discardDomainVisual(serverLevel, domainBarrierUuid);' in source,
    'both UUID references are reset after cleanup': 'domainHandsUuid = null;\n            domainBarrierUuid = null;\n            domainVisualCleanupPending = false;' in source,
    'stale visual references schedule cleanup outside active intro': 'else if (domainHandsUuid != null || domainBarrierUuid != null) {\n            domainVisualCleanupPending = true;' in source,
    'load path avoids immediate ServerLevel lookup': 'during NBT loading' in source,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
print(f'Result: {sum(checks.values())}/{len(checks)} checks passed')
raise SystemExit(0 if all(checks.values()) else 1)
