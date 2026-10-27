# Mahito transformation animation timing fix (2026-10-10)

- Kept the requested 4-server-tick (0.2 s) state/skin/model switch.
- Added a separate synchronized animation-playing flag so the Bedrock-derived `animation.mahito.stage2` clip (declared length 5.5 s) is not interrupted when the entity swaps into its stage-two geometry and texture after 4 ticks.
- The transformation clip now continues on the stage-two model for 110 ticks, then the controller returns to the stage-two idle loop.
- Persisted the animation flag and remaining animation ticks in NBT.

This is a source-level logic fix only. It has not been compiled or tested in-game; visual correctness still depends on the actual GeckoLib runtime and imported geometry/animation data.
