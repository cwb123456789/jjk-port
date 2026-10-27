# Mahito Domain Visual Reload Recovery

## Change

An active domain intro restored from NBT now schedules a server-tick validation of its hand-sign and barrier visual entities. If a saved UUID does not resolve to the expected visual entity after the world has attached loaded entities, the missing visual is recreated with an age derived from the remaining intro duration.

The repair path does not replay the domain's opening damage pulse. Existing visuals keep their UUID and lifetime. The repair flag is transient and is cleared after the validation pass or when the intro ends.

## Scope and limitations

- This is a recovery path for the prototype entity and its two domain visual entities.
- The recreated effect age is clamped to the 0–60 tick lifetime range.
- Static source audits cannot prove entity lookup ordering or duplicate avoidance under every real chunk-load sequence. A Forge runtime test remains required.
- Java compilation and in-game testing have not been completed in this environment.
