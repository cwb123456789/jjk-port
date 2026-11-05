#!/usr/bin/env python3
"""Static regression checks for the standalone Gojo NPC emergency (low-health) Purple."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
s = (root / 'src/main/java/com/jujutsuenhancement/entity/GojoAwakenedEntity.java').read_text(encoding='utf-8')
checks = {
    'low-health NPC branch is evaluated after one-shot Meguna priority': s.index('if (!purple200Used)') < s.index('// Low health: unlimited purple.'),
    'low-health trigger uses 30 percent health threshold': 'getHealth() <= getMaxHealth() * 0.3F' in s,
    'low-health trigger starts unlimited Purple': 'startSkill(ANIM_PURPLE_UNLIMITED, target)' in s,
    'unlimited Purple has independent cooldown': 'now >= nextUnlimitedTime' in s and 'case ANIM_PURPLE_UNLIMITED -> nextUnlimitedTime = now + cooldownTicks' in s,
    'Meguna 200-percent attack remains one-shot': 'if (!purple200Used)' in s and 'purple200Used = true' in s,
    'unlimited projectile is created by the release event': 'if (skillTick == UNLIMITED_RELEASE) fireUnlimited(level)' in s,
    # limitless/limitless5b.mcfunction plays animation.hollowpurple (24 s) for the NPC skill; that is purple_normal.
    # animation.limitless_purple (22.975 s) is a different clip and must not be used here.
    'ordinary Purple plays the 24 s hollowpurple clip': 'thenPlay("animation.gojo.purple_normal")' in s,
}
for name, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {name}")
print(f"Gojo low-health unlimited Purple audit: {sum(checks.values())}/{len(checks)}")
raise SystemExit(0 if all(checks.values()) else 1)
