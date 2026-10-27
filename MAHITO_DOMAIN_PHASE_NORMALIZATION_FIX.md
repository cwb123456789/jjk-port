# Mahito domain phase normalization after save load

## Change

`MahitoPrototypeEntity.readAdditionalSaveData` now normalizes mutually exclusive phase flags and timers in priority order:

1. `TRANSFORMING`: clears transformed/stage-two/domain-intro flags and downstream timers; ensures the four-tick transition can finish.
2. `TRANSFORM_ANIMATION_PLAYING`: restores transformed state, clears domain intro, and schedules a one-tick transition if its timer was saved at zero. This preserves the normal transition path that creates domain visuals and applies the opening pulse.
3. `DOMAIN_INTRO_PLAYING`: restores transformed state, clears the stage-two timer, and drops a zero-duration intro.
4. Idle: clears stale stage-two and domain-intro timers.

Invalid skill-animation IDs outside the supported range `0..2` are reset to idle. This is a defensive save-recovery change for the prototype entity; it does not add native JujutsuCraft NPC integration.

## Validation

Run `python3 tools/audit_mahito_domain_phase_normalization.py` for eight targeted source invariants. This audit is static and does not replace Java compilation or in-game testing.
