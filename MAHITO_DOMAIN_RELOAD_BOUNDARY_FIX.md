# Mahito domain reload-boundary fix

- If a save captures `TRANSFORM_ANIMATION_PLAYING=true` with a zero animation timer, recovery now restores a one-tick timer and lets the ordinary server tick perform the transition. This keeps the domain intro, visual entities, and opening pulse on the same transition path instead of restoring only the animation flag.
- Clamp saved ages for the hands and barrier visuals to `0..LIFETIME_TICKS`; malformed or stale NBT cannot make their lifetime counter negative or extend it beyond the expiry boundary.
- These are source-level safeguards. The project still requires a successful Gradle compile and an in-game save/reload test to confirm runtime behavior.
