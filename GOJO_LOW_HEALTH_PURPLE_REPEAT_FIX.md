# Gojo NPC low-health Purple repeat fix (2026-10-10)

- Changed the low-health NPC trigger from a one-shot charged 200% Purple into repeatable regular Hollow Purple shots while Gojo is alive and at or below 20% health.
- Added a separate 40-tick (2-second) cooldown timestamp in persistent entity data. The cooldown advances only after the projectile is successfully added to the server level.
- Kept the Meguna-triggered five-second 200% Purple one-shot flag separate, so an emergency shot no longer permanently consumes that independent behavior.
- A successful emergency shot clears a stale in-progress target lock/charge before returning.
- Updated `tools/audit_emergency_purple_targeting.py` and added `tools/audit_npc_low_health_purple_repeat.py`.

Validation: all 37 `tools/audit_*.py` scripts completed successfully with per-script timeout protection; `tools/validate_resources.py` passed all 16 JSON/resource checks. Java compilation and in-game testing remain unverified because the Gradle distribution is not available in this environment.
