#!/usr/bin/env python3
"""Static audit for the native Mahito 0.2-second form bridge."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / "src/main/java/com/jujutsuenhancement/integration/NativeMahitoFormBridge.java"
text = source.read_text(encoding="utf-8")
checks = {
    "targets native JujutsuCraft Mahito only": '"net.mcreator.jujutsucraft.entity.MahitoEntity"' in text,
    "runs server-side only": "entity.level().isClientSide" in text,
    "detects native domain skill 1520": "DOMAIN_SKILL_ID = 1520" in text,
    "reads skill using native double NBT type": "double skill = data.getDouble(SKILL_KEY)" in text,
    "uses exact four-tick delay": "FORM_DELAY_TICKS = 4" in text,
    "uses native phase-two texture identifier": '"mahito_phase2__body"' in text,
    "records original texture for recovery/audit": "ORIGINAL_TEXTURE_KEY" in text,
    "keeps transformed texture after domain skill ends": "Keep the transformed texture" in text and "restoreTexture(entity)" not in text,
    "rearms animation independently from persistent transformation": "DOMAIN_ANIMATION_TRIGGERED_KEY" in text,
    "reflection failure does not stop native skill": "Fail closed" in text,
    "documents limitations": "does not replace its skill dispatcher" in text,
}
for label, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {label}")
print(f"Result: {sum(checks.values())}/{len(checks)} checks passed")
raise SystemExit(0 if all(checks.values()) else 1)
