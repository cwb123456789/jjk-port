# Native Sukuna Open test driver — r51

## Change

The `/jujutsuenhancement sukuna_native_open_test` command now schedules skill 107 on a supported native Sukuna mob, resets `cnt1`, and marks a short-lived test session. A server-side `LivingTickEvent` handler invokes `CursedTechniqueSukunaProcedure.execute(LevelAccessor, double, double, double, Entity)` once per tick while that session is active.

## Why

JujutsuCraft v50.1's `CursedTechniqueSukunaProcedure` routes skill 107 to `OpenProcedure`. The supplied `OpenProcedure` bytecode increments the entity's `cnt1` state and uses timed phases, including a later `RangeAttackProcedure` call. Invoking the dispatcher only once advances that state machine by one tick and does not test the full attack sequence. The bounded driver advances it across successive ticks instead.

## Guards

- Server side only.
- Only the three allowlisted native Sukuna entity classes.
- The command requires a live target at start; losing the target cancels the test and resets `skill` to 0.
- The session stops when the native procedure changes `skill` away from 107 or the `cnt1` counter exceeds 45.
- Each native entity has a 100-tick cooldown for this manual test command.
- Reflection/runtime failures cancel the test and clear skill 107.

## Limitations

This remains a manual test harness, not automatic NPC skill selection. Static audits do not establish successful runtime animation, projectile spawn, damage, or compatibility with other JujutsuCraft versions. Java compilation and in-game validation have not been completed in this environment.
