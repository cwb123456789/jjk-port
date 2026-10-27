# Native `OpenProcedure` entity requirements — JujutsuCraft v50.1

## Verified from the supplied JAR

`javap -c -p` inspection of `net.mcreator.jujutsucraft.procedures.OpenProcedure` shows that the procedure:

- reads and increments the entity persistent-data field `cnt1`;
- reads `COOLDOWN_TICKS` and applies JujutsuCraft's `COOLDOWN_TIME` effect when the entity is a server-side `LivingEntity`;
- branches on `Mob` to obtain a living attack target and rotate toward it;
- invokes `PlayAnimationProcedure`;
- contains ranged-ammunition setup and native entity-spawn logic.

## Integration conclusion

The current custom `HeianSukunaEntity` extends base `Entity`, not `LivingEntity` or `Mob`. Therefore, calling `OpenProcedure.execute(level, customSukuna)` would skip essential LivingEntity/Mob branches and cannot be considered a valid implementation of the native 「灶」 skill. Merely setting `skill=107` is also insufficient evidence that the complete AI dispatch will execute correctly.

The next implementation decision must be one of:

1. implement the Sukuna as a proper `Mob`/`LivingEntity` with a deliberate compatibility layer, or
2. reproduce the required gameplay behavior with custom entities while reusing verified source animation/assets where licensing permits.

Do not invoke `OpenProcedure` directly on the current base `Entity`. The current small-fireball attack remains explicitly a placeholder, not the native skill.

## Validation

Run `python tools/audit_native_open_entity_requirements.py` from the project root. This checks the observed bytecode requirements and the custom entity's declared superclass. It is a static audit, not an in-game test.
