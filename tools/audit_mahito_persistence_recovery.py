#!/usr/bin/env python3
"""Static regression audit for Mahito prototype save/load timer recovery."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java").read_text()
checks = {
    "animation timers clamped non-negative": "transformAnimationTicks = Math.max(0, transformAnimationTicks);" in source and "domainIntroTicks = Math.max(0, domainIntroTicks);" in source,
    "skill cooldowns clamped non-negative": "soulStrikeCooldown = Math.max(0, soulStrikeCooldown);" in source and "idleSkillCooldown = Math.max(0, idleSkillCooldown);" in source,
    "transforming flag cannot remain stuck at zero timer": "if (transformTicks == 0) transformTicks = 1;" in source and "if (entityData.get(TRANSFORMING)) {" in source,
    "transform animation recovers into domain intro": "} else if (entityData.get(TRANSFORM_ANIMATION_PLAYING)) {" in source and "if (transformAnimationTicks == 0) {" in source and "entityData.set(DOMAIN_INTRO_PLAYING, true);" in source,
    "domain intro flag clears when its timer is exhausted": "if (entityData.get(DOMAIN_INTRO_PLAYING) && domainIntroTicks == 0)" in source and "entityData.set(DOMAIN_INTRO_PLAYING, false);" in source,
    "expired skill animation is cleared": "if (skillAnimationTicks == 0) entityData.set(SKILL_ANIMATION, 0);" in source,
    "transformation state and timers remain persisted": 'tag.putBoolean("Transformed", entityData.get(TRANSFORMED));' in source and 'tag.putInt("TransformAnimationTicks", transformAnimationTicks);' in source,
}
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'} {name}")
print(f"RESULT {sum(checks.values())}/{len(checks)}")
raise SystemExit(0 if all(checks.values()) else 1)
