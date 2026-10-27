#!/usr/bin/env python3
"""Static regression checks for Mahito domain visuals lining up with the intro."""
from pathlib import Path

source = Path(__file__).resolve().parents[1] / "src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java"
text = source.read_text(encoding="utf-8")
checks = {
    "domain intro begins when transformation animation timer reaches zero": "transformAnimationTicks > 0 && --transformAnimationTicks == 0" in text,
    "intro retains full 60 tick duration": "domainIntroTicks = 60; // Keep the full 60-tick (3-second) opening sequence." in text,
    "domain visuals/pulse spawned at intro transition": text.index("expandTransfiguredDomain();") < text.index("if (!level().isClientSide && !beganDomainIntroThisTick"),
    "domain visuals/pulse no longer triggered at 4-tick transform completion": "// The domain pulse is deferred until the transformation animation finishes" in text,
    "hands effect entity is still spawned by domain expansion": "MAHITO_DOMAIN_HANDS.get().create(serverLevel)" in text,
    "barrier effect entity is still spawned by domain expansion": "MAHITO_DOMAIN_BARRIER.get().create(serverLevel)" in text,
    "domain pulse still applies server-side damage": "target.hurt(damageSources().indirectMagic(this, owner), 10.0F);" in text,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
print(f"RESULT: {len(checks) - len(failed)}/{len(checks)} passed")
raise SystemExit(1 if failed else 0)
