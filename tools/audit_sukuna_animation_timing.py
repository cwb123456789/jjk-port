#!/usr/bin/env python3
"""Check Heian Sukuna attack clip lengths against the entity's synced-state timer."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANIMATION = ROOT / 'src/main/resources/assets/jujutsuenhancement/animations/heian_sukuna.animation.json'
JAVA = ROOT / 'src/main/java/com/jujutsuenhancement/entity/HeianSukunaEntity.java'


def main():
    animations = json.loads(ANIMATION.read_text(encoding='utf-8')).get('animations', {})
    java = JAVA.read_text(encoding='utf-8')
    match = re.search(r'ATTACK_ANIMATION_TICKS\s*=\s*(\d+)', java)
    if not match:
        print('FAIL: attack animation timer constant not found')
        return 1
    timer = int(match.group(1))
    lengths = []
    for index in range(1, 6):
        name = f'animation.heian_sukuna.attack{index}'
        clip = animations.get(name)
        if not clip:
            print(f'FAIL: missing {name}')
            return 1
        length = clip.get('animation_length')
        bones = clip.get('bones', {})
        if not isinstance(length, (int, float)) or length <= 0 or not bones:
            print(f'FAIL: {name} has invalid duration or no animated bones')
            return 1
        lengths.append(float(length))
    # GeckoLib uses seconds; Minecraft entity ticks run at 20 ticks/second.
    required = max(1, round(max(lengths) * 20))
    ok = timer >= required
    print(f'{"PASS" if ok else "FAIL"}: attack timer {timer} ticks; longest imported clip {max(lengths):.3f}s ({required} ticks)')
    return 0 if ok else 1

if __name__ == '__main__':
    raise SystemExit(main())
