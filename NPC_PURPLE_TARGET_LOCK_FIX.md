# NPC 200% Hollow Purple target-lock fix

The Gojo NPC prototype now validates its locked Sukuna-possessed Megumi target throughout the five-second charge. If the target dies, changes level, no longer matches the expected JujutsuCraft entity class, or moves beyond 32 blocks, the charge is cancelled and both the charge timestamp and target UUID are cleared. The NPC no longer launches the charged projectile in its current look direction when the original target disappears.

Validation: `python3 tools/audit_npc_purple_target_lock.py` checks the source-level target lock, range, cancellation, state cleanup, and launch direction. This is a static regression check only; it does not replace Java compilation or in-game testing.
