# Hollow Purple damage attribution fix

`HollowPurpleEntity` previously applied hits using `DamageSources.magic()` regardless of whether its owner was loaded. That discarded projectile/attacker attribution for kills and owner-aware damage hooks.

The hit path now uses `damageSources().indirectMagic(this, owner)` when the owner entity can be resolved. If the owner is unavailable (for example, unloaded), it retains the generic magic-damage fallback rather than dropping the hit. Existing ally filtering and per-projectile hit de-duplication remain unchanged.

Validation: `tools/audit_hollow_purple_damage_attribution.py` performs static source checks. This is not a substitute for Gradle compilation or in-game testing.
