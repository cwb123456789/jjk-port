# Native Sukuna dispatch-chain evidence (v50.1)

## Confirmed from the supplied JAR

1. `AISukunaProcedure.execute(LevelAccessor, double, double, double, Entity)` checks the entity for null/alive state and invokes `AIActiveProcedure.execute(LevelAccessor, double, double, double, Entity)` early in its execution path.
2. The native `AIActiveProcedure` bytecode accesses entity persistent data under the `skill` key and references native cooldown effects. This is a shared activation/maintenance path, not evidence that a particular skill can be safely triggered by writing an arbitrary integer to `skill`.
3. `AIFlameArrowProcedure` references `skill`, `cnt1`, and the native `FlameArrowEntity` type.
4. A scan of the supplied JAR's class constant pools found a direct class reference to `AIFlameArrowProcedure` in `FlameArrowEntity.class`. It did not find a direct constant-pool caller from `AISukunaProcedure` or `AIActiveProcedure`.

## Not established

- The numeric skill ID for the stove/flame-arrow branch.
- The exact predicate and timing that routes the native Sukuna AI into that branch.
- Whether the branch depends on additional entity subclasses, synchronized data, animation state, advancement/selection setup, or cooldown effects.
- Whether invoking a procedure directly on `HeianSukunaEntity` is safe. It is not currently wired.
- Java compilation and in-game behavior.

## Engineering decision

Do not guess a skill ID, spoof native entity state, or call `AIFlameArrowProcedure` directly from the custom entity. Next evidence-gathering step is to inspect the conditional blocks around `skill` reads in `AISukunaProcedure` and `AIActiveProcedure`, plus the `FlameArrowEntity` constructor/on-tick path. Only implement a bridge after those preconditions are grounded in this exact JAR.

## Reproduction

```bash
python3 tools/audit_native_sukuna_dispatch_chain.py
```

This is static bytecode analysis, not a game test.
