# Sukuna animation/model compatibility correction

## Finding
The original addon resource pack contains the exact `models/entity/sukuna/heian_sukuna.geo.json` used by the Heian Sukuna character. It has 62 bones, and its bone names match the current Java runtime geometry exactly. The original `animation_controllers/heian_sukuna.animations_controllers.json` only references `heian_sukuna.idle`, `heian_sukuna.walk`, `heian_sukuna.sprint`, and `heian_sukuna.jump`.

The 80-animation `sukuna_moves.animation.json` set contains animation IDs such as `animation.sukuna.attack1`, but its channels include eight bone names absent from the Heian geometry: `chest`, `forward_locator`, `forward_locator1`, `forward_locator2`, `forward_locator3`, `forward_locator4`, `leftbone1`, and `rightbone`. These names are not in the exact Heian geometry, so those channels cannot simply be remapped without evidence. The animation file is not safely interchangeable with the Heian controller's movement set.

## Correction / next implementation gate
Preserved the original Heian geometry, movement animation file, and controller source under `docs/bedrock-source-reference/`. Before claiming faithful Heian combat animations, the port must identify the original entity/model/controller pairing for each move animation and preserve required locators/particles. Do not silently rename bones or claim the 80 move animations are all correctly playable on Heian Sukuna.

## Validation
This is a source-asset pairing audit, not an in-game animation test. Java compilation and runtime verification remain pending.
