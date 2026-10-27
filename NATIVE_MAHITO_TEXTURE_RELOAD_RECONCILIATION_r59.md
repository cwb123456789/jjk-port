# r59 — Native Mahito phase-two texture reconciliation

## Change

The native Mahito bridge previously treated the persistent `...phase2_applied` flag as proof that the runtime texture still held `mahito_phase2__body`. After a world/entity reload, the persistent flag could survive while the base mod reconstructed its synchronized texture field from defaults; the bridge would then skip applying the phase-two texture.

After the existing four-tick domain delay, the bridge now checks the live texture when first applying the form and every 20 active domain ticks. If the runtime value differs from `mahito_phase2__body`, it re-applies the phase-two texture. The original texture is still recorded only once, and the native domain-start animation remains one-shot per domain activation.

## Scope and limitations

- Applies only to the exact native JujutsuCraft `MahitoEntity` class while native skill `1520` is active.
- Server-side bridge only; reflection errors still fail closed and do not interrupt native skill execution.
- Does not replace the native skill dispatcher or native model renderer, and does not prove the model/animation is visually correct in game.

## Verification

- Regression/static audits: 29/29 passed.
- Resource JSON validation: passed (18 JSON files).
- ZIP integrity: verified separately for the packaged archive.
- Java compilation and in-game verification: not completed.
