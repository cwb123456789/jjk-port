#!/usr/bin/env python3
"""Static audit for r59 native Mahito texture reconciliation after reload."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
src = (ROOT / "src/main/java/com/jujutsuenhancement/integration/NativeMahitoFormBridge.java").read_text(encoding="utf-8")
checks = {
    "native Mahito domain-only guard retained": 'double skill = data.getDouble(SKILL_KEY)' in src and 'DOMAIN_SKILL_ID = 1520' in src,
    "four-tick phase transition retained": 'FORM_DELAY_TICKS = 4' in src and 'if (ticks < FORM_DELAY_TICKS)' in src,
    "persistent applied marker is not blindly trusted": 'if (!data.getBoolean(APPLIED_KEY) || ticks % 20 == 0)' in src,
    "live texture compared to phase-two texture": '!PHASE_TWO_TEXTURE.equals(currentTexture)' in src,
    "phase-two texture restored when runtime value differs": 'setTexture.invoke(entity, PHASE_TWO_TEXTURE)' in src,
    "original texture is captured only when missing": '!data.contains(ORIGINAL_TEXTURE_KEY)' in src,
    "animation remains one-shot per domain activation": 'if (data.getBoolean(DOMAIN_ANIMATION_TRIGGERED_KEY))' in src and 'data.putBoolean(DOMAIN_ANIMATION_TRIGGERED_KEY, true)' in src,
    "reflection failure remains fail-closed": "Fail closed: don't interrupt JujutsuCraft" in src,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
print(f"Result: {sum(checks.values())}/{len(checks)} checks passed")
raise SystemExit(0 if all(checks.values()) else 1)
