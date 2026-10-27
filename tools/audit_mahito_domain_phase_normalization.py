from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = (root / 'src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java').read_text(encoding='utf-8')
checks = {
    'transforming phase clears transformed and later phases': 'if (entityData.get(TRANSFORMING)) {' in source and 'entityData.set(TRANSFORMED, false);' in source and 'entityData.set(DOMAIN_INTRO_PLAYING, false);' in source,
    'transforming phase resets downstream timers': 'transformAnimationTicks = 0;\n            domainIntroTicks = 0;' in source,
    'stage-two phase enforces transformed state': '} else if (entityData.get(TRANSFORM_ANIMATION_PLAYING)) {\n            entityData.set(TRANSFORMED, true);' in source,
    'stage-two zero timer schedules normal transition': 'if (transformAnimationTicks == 0) {\n                // Let the normal tick transition run' in source and 'transformAnimationTicks = 1;' in source,
    'domain intro phase enforces transformed state': '} else if (entityData.get(DOMAIN_INTRO_PLAYING)) {\n            entityData.set(TRANSFORMED, true);' in source,
    'idle phase clears stale phase timers': '} else {\n            transformAnimationTicks = 0;\n            domainIntroTicks = 0;' in source,
    'invalid skill animation ids are reset': 'if (skillAnimation < 0 || skillAnimation > 2) entityData.set(SKILL_ANIMATION, 0);' in source,
    'skill animation flag clears when timer expires': 'if (skillAnimationTicks == 0) entityData.set(SKILL_ANIMATION, 0);' in source,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
print(f'Result: {sum(checks.values())}/{len(checks)} checks passed')
raise SystemExit(0 if all(checks.values()) else 1)
