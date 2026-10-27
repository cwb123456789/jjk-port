# Mahito transformed-skill opening lock

## Change

`MahitoPrototypeEntity.interact` now consumes interactions while either the stage-two transformation animation or the domain-opening intro is playing. This prevents Soul Strike and Idle Transfiguration burst effects from firing on top of the opening sequence.

## Preserved behavior

- The visual form switch still occurs after 4 server ticks (0.2 seconds).
- The stage-two animation remains 110 ticks.
- The domain intro remains 60 ticks and starts after the stage-two animation.
- Skill effects and their cooldowns are unchanged after both animation states finish.

## Verification

`tools/audit_mahito_transform_skill_lock.py` is a static regression audit. It does not replace Java compilation or an in-game timing test.
