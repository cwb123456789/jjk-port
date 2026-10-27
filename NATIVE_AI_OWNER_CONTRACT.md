# Native Sukuna AI owner contract — JujutsuCraft v50.1

## Confirmed from the supplied JAR

- `AISukunaProcedure.execute(...)` delegates to `AIActiveProcedure.execute(...)`.
- `AIActiveProcedure.execute(...)` returns when its owner argument is not a `LivingEntity`.
- If the owner is a `Mob`, the procedure reads its current living attack target via `Mob.getTarget()`; a non-`Mob` living entity has no target supplied through that branch.
- The project `HeianSukunaEntity` currently extends base `Entity`, not `LivingEntity` or `Mob`.

## Consequence

The current custom Heian Sukuna cannot use the native `AISukunaProcedure` → `AIActiveProcedure` route as-is. Assigning `skill` NBT or directly calling `OpenProcedure` does not fix this superclass contract. A native-compatible implementation requires either a deliberately designed `Mob`/`LivingEntity` entity (including attributes, target selection, persistence, and renderer/gameplay regression checks) or a separate custom skill implementation that explicitly manages projectile ownership, targeting, movement, cooldowns, and cleanup.

Do not change the entity superclass as a mechanical one-line fix: it changes collision, health, attributes, AI, save/load and interaction behavior and needs a complete integration pass.

## Reproducible check

Run `python3 tools/audit_native_ai_owner_contract.py` from the project root. This is a static audit against the supplied JAR and source, not a compile or in-game test.
