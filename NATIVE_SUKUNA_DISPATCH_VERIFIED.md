# JujutsuCraft v50.1 Sukuna dispatcher verification

A `javap -c -p` inspection of the supplied JujutsuCraft v50.1 JAR confirms:

- `CursedTechniqueSukunaProcedure` dispatches skill value `107` to `OpenProcedure.execute(LevelAccessor, Entity)`.
- It dispatches skill value `120` to `MalevolentShrineProcedure.execute(LevelAccessor, double, double, double, Entity)`.
- `MalevolentShrineActiveProcedure.execute(LevelAccessor, Entity)` exists as a separate active/update path.

## Integration boundary

This confirms dispatcher mappings and method signatures only. It does **not** establish that calling either procedure on the custom `HeianSukunaEntity` is safe or sufficient. The original procedures may expect JujutsuCraft-specific entity classes, persistent NBT, capabilities, cooldown fields, and ongoing update calls. The custom entity currently does not implement those native contracts. Therefore this step does not invoke the procedures directly and does not claim that the actual Stove skill is implemented.

Audit: `tools/audit_native_sukuna_dispatch_ids.py`.

## Follow-up: `OpenProcedure` is not a standalone native skill API

Further bytecode inspection confirms `OpenProcedure` branches on `LivingEntity` and `Mob`, reads `cnt1` and `COOLDOWN_TICKS`, applies the native cooldown effect, invokes `PlayAnimationProcedure`, and contains ranged-ammunition/entity-spawn setup. The current custom `HeianSukunaEntity` extends base `Entity`, so direct invocation would skip required entity-specific branches. See `NATIVE_OPEN_ENTITY_REQUIREMENTS.md` and run `python tools/audit_native_open_entity_requirements.py` to reproduce the static check.
