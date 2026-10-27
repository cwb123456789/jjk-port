#!/usr/bin/env python3
"""Static guard for the Bedrock-derived Sukuna Fuga charge/release prototype."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
entity_path = root / "src/main/java/com/jujutsuenhancement/entity/HeianSukunaEntity.java"
bridge_path = root / "src/main/java/com/jujutsuenhancement/integration/JujutsuCraftSkillBridge.java"
anim_path = root / "src/main/resources/assets/jujutsuenhancement/animations/heian_sukuna.animation.json"
geo_path = root / "src/main/resources/assets/jujutsuenhancement/geo/heian_sukuna.geo.json"
entity = entity_path.read_text()
bridge = bridge_path.read_text()
anims = json.loads(anim_path.read_text())["animations"]
geo = json.loads(geo_path.read_text())["minecraft:geometry"][0]
geometry_bones = {bone["name"] for bone in geo["bones"]}
checks = {
    "charge duration is 55 ticks": "FUGA_CHARGE_TICKS = 55" in entity,
    "charge animation state is synchronized": "entityData.set(SKILL_ANIMATION, 6)" in entity,
    "hold state follows charge completion": "entityData.set(SKILL_ANIMATION, 7)" in entity,
    "ready state has a bounded release window": "FUGA_READY_WINDOW_TICKS = 100" in entity and "fugaReadyTicksRemaining = FUGA_READY_WINDOW_TICKS" in entity,
    "expired ready state clears ownership and animation": "else if (fugaReadyTicksRemaining > 0 && --fugaReadyTicksRemaining == 0)" in entity and "cancelFugaCharge();" in entity,
    "charger identity prevents release theft": "isFugaCharger(player.getUUID())" in bridge,
    "early release is rejected": "!sukuna.isFugaReady()" in bridge,
    "successful spawn commits cooldown and attack": "sukuna.commitFugaRelease(now, SINGLE_COOLDOWN_TICKS)" in bridge,
    "Bedrock activation clip imported": "animation.fuga_activate" in anims,
    "Bedrock hold clip imported": "animation.fuga_activate_hold" in anims,
    "activation clip is non-looping": anims["animation.fuga_activate"].get("loop") is False,
    "hold clip loops": anims["animation.fuga_activate_hold"].get("loop") is True,
    "activation bones match Java geometry": set(anims["animation.fuga_activate"].get("bones", {})).issubset(geometry_bones),
    "hold bones match Java geometry": set(anims["animation.fuga_activate_hold"].get("bones", {})).issubset(geometry_bones),
    "controller maps charge, hold, and release states": "if (attack == 6) return state.setAndContinue(FUGA_ACTIVATE)" in entity and "if (attack == 7) return state.setAndContinue(FUGA_HOLD)" in entity and "if (attack == 8) return state.setAndContinue(FUGA_RELEASE)" in entity,
    "release animation exists and uses compatible bones": "animation.fuga_release" in anims and set(anims["animation.fuga_release"].get("bones", {})).issubset(geometry_bones),
    "release animation has a finite duration": 0 < anims["animation.fuga_release"].get("animation_length", 0) <= 2.0,
    "release state timer is configured": "FUGA_RELEASE_TICKS = 30" in entity and "entityData.set(SKILL_ANIMATION, 8)" in entity,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'} {label}")
if not all(checks.values()):
    raise SystemExit(1)
