# Native Sukuna skill-dispatch test — r49

## Change

The `sukuna_native_open_test` command no longer calls `OpenProcedure` directly. It sets the selected native Sukuna entity's persistent `skill` value to `107.0` and reflectively invokes `CursedTechniqueSukunaProcedure.execute(LevelAccessor, double, double, double, Entity)`. The supplied JujutsuCraft v50.1 bytecode confirms the native dispatcher routes skill ID 107 to `OpenProcedure`.

## Why

Directly calling `OpenProcedure` bypasses the game's skill-ID dispatcher branch. Calling the dispatcher better exercises the native route and catches signature/dispatch mismatches earlier.

## Scope and caveats

- This is still a manual test command, not autonomous NPC AI integration.
- The command changes the selected entity's `skill` persistent-data value to 107. The native procedure may subsequently mutate skill state.
- Static bytecode checks do not prove the effect, animation, damage, or projectile behavior in a live game.
- Java compilation and in-game verification have not been completed in this environment.
