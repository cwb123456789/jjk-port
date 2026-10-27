# Flame Arrow Minecraft 1.20.1 fire API compatibility fix

`SukunaFlameArrowEntity` now uses `LivingEntity#setSecondsOnFire(int)` rather than `igniteForSeconds(int)`. The former is the expected Mojang-mapped entity fire API for the project's Minecraft 1.20.1 target. This is a source/API compatibility correction only; Java compilation and in-game behavior remain unverified because Gradle 8.8 cannot be downloaded in the current network environment.

Validation performed:
- `tools/audit_flame_arrow_fire_api.py`
- all `tools/audit_*.py` scripts
- `tools/validate_resources.py`
- ZIP integrity test
