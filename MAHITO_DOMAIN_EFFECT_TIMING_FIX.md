# Mahito domain effect timing fix (r21)

## Issue
The domain hands and barrier each have a 60-tick lifetime, but were spawned when the 4-tick transformation completed. The entity then plays a 110-tick (5.5-second) stage-two transformation animation before starting the 60-tick domain intro. As a result, the temporary domain visuals expired before the intro animation became visible.

## Change
- Move `expandTransfiguredDomain()` from the 4-tick transformation-completion branch to the transition where `transformAnimationTicks` reaches zero and the domain intro starts.
- Keep the 110-tick transformation animation and 60-tick domain intro duration unchanged.
- The domain damage pulse, hands entity, barrier entity, particles, and sound now begin together with the visible domain intro.

## Validation scope
This is a source-level timing correction. It does not establish successful Java compilation or in-game behavior; those still require a working Gradle environment and runtime test.
