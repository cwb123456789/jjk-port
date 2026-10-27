# Mahito Domain Visual Cleanup Fix

## Change

`MahitoPrototypeEntity` now tracks the UUIDs of the hand-sign and barrier entities created for its stage-two domain opening. The UUIDs are saved/restored with the prototype entity. When the prototype is actually killed or discarded, it removes only those two recognized visual entity types and clears the stored UUIDs.

The cleanup intentionally does not run for `UNLOADED_TO_CHUNK` or `UNLOADED_WITH_PLAYER`, because normal world/chunk lifecycle events must not be treated as domain cancellation.

## Validation

- `tools/audit_mahito_domain_visual_cleanup.py`: 8/8 static checks passed.
- `tools/validate_resources.py`: 16 JSON files and associated geometry/UV/animation/texture checks passed.
- Mahito domain effect timing: 7/7 passed.
- Mahito domain intro duration: 6/6 passed.
- Mahito persistence recovery: 7/7 passed.
- Mahito transform skill lock: 5/5 passed.
- Mahito domain test commands: 13/13 passed.

These are static checks. Java compilation and in-game lifecycle testing have not been completed.
