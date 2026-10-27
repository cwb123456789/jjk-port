# Mahito stale domain-visual cleanup after reload

## Change

`MahitoPrototypeEntity` now marks saved domain visual UUIDs for deferred cleanup when the restored phase is no longer an active domain intro, or the intro timer is already exhausted. Cleanup runs on the first server tick rather than directly inside `readAdditionalSaveData`, because entity UUID lookup can be premature while entities are still being deserialized and attached to a level.

Both hands and barrier UUID references are cleared after the cleanup attempt. This handles stale references left by older saves or interrupted phase transitions without changing the normal active-intro lifetime.

## Validation boundary

The new audit verifies the intended source invariants only. It does not replace Java compilation, chunk reload testing, or an in-game test with a real save.
