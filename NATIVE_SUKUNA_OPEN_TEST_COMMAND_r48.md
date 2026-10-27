# Native Sukuna OpenProcedure test command — r48

Added `/jujutsuenhancement sukuna_native_open_test` to exercise the exact JujutsuCraft v50.1 `OpenProcedure` against a nearby native Sukuna NPC.

## Guardrails
- Only searches for the exact native `SukunaEntity`, `SukunaFushiguroEntity`, or `SukunaPerfectEntity` classes.
- Requires the entity to be a `Mob` already targeting a living entity.
- Calls the native `OpenProcedure.execute(LevelAccessor, Entity)` method reflectively, matching the signature confirmed from the supplied v50.1 JAR.
- Does not call the procedure on the mod's `HeianSukunaEntity`, which extends base `Entity` and does not satisfy the native LivingEntity/Mob behavior requirements.
- This is a manual test hook only. It does not automatically replace the native skill dispatcher, assign skill ID 107, or prove Bedrock visual/animation parity.

## In-game test
1. Load the mod with JujutsuCraft v50.1 on a test world.
2. Spawn a native Sukuna NPC and make it acquire a living target.
3. Run `/jujutsuenhancement sukuna_native_open_test` within 32 blocks.
4. Observe the native animation/projectile and check `latest.log` for reflective invocation failures.

Java compilation and in-game execution have not been performed in this environment.
