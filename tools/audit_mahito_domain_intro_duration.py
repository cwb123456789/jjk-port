#!/usr/bin/env python3
"""Static regression check for Mahito's domain-intro timer boundary."""
from pathlib import Path

source = Path('src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java').read_text()
checks = {
    'tick tracks whether the intro starts this tick': 'boolean beganDomainIntroThisTick = false;' in source,
    'intro timer is set to the intended 60 ticks': 'domainIntroTicks = 60; // Keep the full 60-tick (3-second) opening sequence.' in source,
    'newly started intro is not decremented immediately': '&& !beganDomainIntroThisTick\n                && domainIntroTicks > 0 && --domainIntroTicks == 0' in source,
    'intro still clears after its countdown': 'entityData.set(DOMAIN_INTRO_PLAYING, false);' in source,
    'intro countdown is persisted': 'tag.putInt("DomainIntroTicks", domainIntroTicks);' in source,
    'intro countdown is restored': 'domainIntroTicks = tag.getInt("DomainIntroTicks");' in source,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ' - ' + name)
if not all(checks.values()):
    raise SystemExit(1)
print(f'{sum(checks.values())}/{len(checks)} checks passed')
