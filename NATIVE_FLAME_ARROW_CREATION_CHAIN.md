# JujutsuCraft v50.1: native flame-arrow creation chain

## Confirmed from the supplied JAR

Static bytecode inspection of the supplied `JujutsuCraft-ver50.1-forge-1.20.1.jar` confirms:

1. `CursedTechniqueSukunaProcedure` dispatches `skill == 107` to `OpenProcedure.execute(LevelAccessor, Entity)`.
2. `OpenProcedure` creates the registered `JujutsucraftModEntities.FLAME_ARROW` entity on a `ServerLevel`.
3. It passes the created entity and source entity to `SetRangedAmmoProcedure.execute(Entity, Entity)`.
4. `SetRangedAmmoProcedure` initializes ranged identity state, including `NameRanged`, `NameRanged_ranged`, and `OWNER_UUID`; it also copies skill/cooldown data for entities tagged as `forge:ranged_ammo`.
5. Each native `FlameArrowEntity` tick calls `AIFlameArrowProcedure.execute(...)`.
6. `AIFlameArrowProcedure` resolves `OWNER_UUID` and requires the projectile's `NameRanged_ranged` to match the owner's `NameRanged`, owner `skill > 0`, and owner `cnt1 <= 30` at that stage. It propagates owner `x_power`, `y_power`, and `z_power` into the projectile state.

## Integration limit

This confirms the native projectile creation and update path, but it does **not** prove that invoking `OpenProcedure` on the custom `HeianSukunaEntity` is safe or equivalent to native activation. `OpenProcedure` contains `LivingEntity` and `Mob` branches for cooldown, target acquisition, animation/hand state, and attack behavior. The custom Heian entity currently extends base `Entity`, so those branches will be skipped or receive no target.

Do not directly invoke `AIFlameArrowProcedure` as a cast API. Do not guess additional skill IDs for 「灶」 or 「开」. The next implementation decision must either provide a compatible native `LivingEntity`/`Mob` owner or reproduce the minimal required native ranged-entity setup deliberately, with server-side ownership, targeting, power, cooldown, and cleanup handled explicitly.

## Reproducible checks

The following static audits passed against the supplied JAR:

- `python3 tools/audit_native_stove_dispatch.py`
- `python3 tools/audit_native_stove_skill.py`
- `python3 tools/audit_native_flame_arrow_preconditions.py`
- `python3 tools/audit_native_sukuna_dispatch_chain.py`
- `python3 tools/audit_native_sukuna_dispatch_ids.py`

These are bytecode/static checks only. They do not establish Java compilation or in-game behavior.
