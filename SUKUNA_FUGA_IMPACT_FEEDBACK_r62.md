# r62 — Sukuna Fuga impact feedback

## Change
- Added a layered explosion and fire-charge sound at the server-authoritative impact point.
- Added bounded radial knockback only after a target accepts splash damage; marks velocity as changed for client synchronization.
- Preserved finite blast radius, direct-hit exclusion, owner/charger exclusion, ally filtering, and owner-aware damage attribution.

## Validation
- Static audit: `tools/audit_sukuna_fuga_impact_feedback_r62.py` (10 assertions).
- Java compilation and in-game validation remain pending; these changes are not confirmed against a live Forge runtime.
