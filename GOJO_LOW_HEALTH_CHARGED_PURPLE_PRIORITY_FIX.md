# Gojo low-health vs. 200% Purple priority fix

## Problem
The low-health emergency Purple branch returned early every time its 40-tick cooldown allowed a shot. If Gojo was already at or below 20% health when a Sukuna-possessed Megumi NPC entered the 20-block trigger radius, this branch could keep preventing the one-shot 200% Purple charge from starting.

## Change
- Added a shared finder for the nearest living, non-allied `SukunaFushiguroEntity` within the 20-block trigger radius.
- When no 200% Purple has been used, no charge is active, and a valid Meguna target is nearby, the NPC starts the 200% charge before using the low-health fallback.
- The low-health shot retains its own cooldown and does not clear the charge timestamp or locked target.
- Once the one-shot 200% attack has already been used, the low-health repeat behavior remains available.

## Verification
- `audit_npc_low_health_purple_repeat.py`: static regression checks for both independent paths.
- `audit_npc_purple_charge_climax.py`, `audit_npc_purple_target_lock.py`, `audit_charged_purple_state.py`: targeted static checks.
- `validate_resources.py`: static resource/geometry/texture checks.
- Java/Gradle compile and in-game verification are not claimed.
