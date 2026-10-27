# Hollow Purple ally damage filter

## Change
The server-side projectile now resolves its shooter from the persisted owner UUID and skips entities that `owner.isAlliedTo(target)` identifies as allies. The explicit shooter UUID exclusion remains in place for entity types that do not report alliance with themselves.

## Validation
- `tools/audit_purple_friendly_fire.py`: static source regression checks.
- This does not replace Java compilation or in-game testing. If JujutsuCraft NPC factions do not expose alliance through Minecraft's `isAlliedTo`, those factions may need a dedicated adapter after runtime verification.
