# Native Sukuna Open test tick-phase fix — r52

## What changed

- Moved the opt-in native Open test driver from `LivingTickEvent` to `TickEvent.LevelTickEvent` at `Phase.END`.
- The base `SukunaEntity` invokes `AISukunaProcedure` in its entity tick. Driving the dispatcher during the living tick can run before that native AI procedure and allow the AI tick to overwrite test state immediately afterward.
- The END-phase driver restores the test's `skill=107` and a session-owned `cnt1` snapshot before each dispatcher call, then saves the resulting counter for the next tick.
- Added a hard 60-tick session bound and retained missing-target and reflection-failure cleanup.

## Scope and limitations

This remains a manual, opt-in test command (`/jujutsuenhancement sukuna_native_open_test`), not automatic NPC skill selection. The source was statically audited, but not compiled or tested in Minecraft. Forge event ordering, the native procedure's state transitions, animation, projectile spawn, and damage must still be confirmed in-game. Because this test temporarily overrides `skill` and `cnt1`, use it only on a disposable/test NPC.
