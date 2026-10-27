# r60 — Sukuna Fuga damage attribution

## Change

The Fuga prototype was aimed using the interacting player's look direction, but the projectile was also assigned that player as its owner. This caused damage attribution and allied-entity filtering to use the player rather than Sukuna.

The bridge now launches the projectile with Sukuna as owner and the charging player as the separately ignored entity. Aim remains player-controlled; the projectile excludes both its owner and the charger from collision, and damage sources resolve to Sukuna when the entity is available.

## Validation

- Added `tools/audit_sukuna_fuga_damage_attribution_r60.py`.
- This is a source-level regression check only; it does not replace Java compilation or an in-game combat test.
