# JujutsuCraft v50.1 Flame Arrow native procedure audit

Static bytecode audit of `AIFlameArrowProcedure.execute(LevelAccessor, double, double, double, Entity)` found these runtime gates:

- The owner is resolved through the projectile's `OWNER_UUID` persistent-data value.
- The projectile's `NameRanged_ranged` must equal the owner's `NameRanged`.
- The owner's `skill` persistent-data value must be positive.
- The owner's `cnt1` value must be at most 30 at this update stage.
- The procedure copies `x_power` and `y_power` values and branches on `FlameArrowEntity` synchronized flags.

This procedure is an AI/update routine for an already-created native `FlameArrowEntity`, not a public “cast stove” API. Static evidence does not establish the skill selector number, prerequisites, animation setup, or the complete creation/dispatch chain. The integration intentionally does not invoke this procedure directly until the full native dispatch sequence is confirmed; doing so could create a nonfunctional or desynchronized skill.

Validation command: `python3 tools/audit_native_flame_arrow_preconditions.py`
