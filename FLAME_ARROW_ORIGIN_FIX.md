# Flame Arrow Origin Fix

The custom Sukuna flame-arrow prototype previously spawned from the interacting player's eye position. That made the projectile appear to be a player-fired projectile rather than an attack emitted by the Heian Sukuna entity.

The interaction bridge now spawns the projectile at 65% of the Sukuna entity's bounding-box height and aims from that origin toward a point 24 blocks along the player's view ray. A near-zero direction falls back to the player's look vector. The clicked Sukuna UUID remains excluded from collision, and the sound originates at the projectile spawn point.

Validation performed: the origin-specific static audit passed 7/7 checks, all 34 audit scripts passed, and resource validation passed for 16 JSON files. `./gradlew compileJava --no-daemon` was attempted but stopped before compilation because `services.gradle.org` could not be resolved. No in-game test was performed.
