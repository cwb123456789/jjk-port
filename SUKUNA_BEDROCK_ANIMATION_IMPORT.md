# Sukuna Bedrock animation import checkpoint

## What changed in this step

- Imported all 80 animation IDs from the add-on's `sukuna_moves.animation.json` into the runtime GeckoLib animation resource, while preserving the existing idle/domain definitions.
- Updated `tools/convert_bedrock_animation.py` to reject non-numeric vectors (including Bedrock Molang expressions such as `query.target_x_rotation`) rather than emitting data that GeckoLib may fail to parse.
- Removed only animation channels whose bone names are absent from the current Java Sukuna geometry. The animation IDs remain present, and the runtime resource now passes the project's bone compatibility validation.
- Added `tools/audit_sukuna_bedrock_animation_port.py` to verify animation ID coverage, keyframe schema, and geometry bone compatibility.

## Validation and fidelity limits

- All 80 source animation IDs are present in the runtime animation JSON.
- Runtime keyframes are numeric vectors and all referenced runtime animation bones exist in the Java geometry.
- Eight Bedrock bone names are not present in the current Java geometry: `chest`, `forward_locator`, `forward_locator1` through `forward_locator4`, `leftbone1`, and `rightbone`. Channels targeting those names were omitted in 27 animations; their original data remains in the preserved Bedrock source JSON.
- The converter flattens Bedrock Catmull-Rom interpolation to linear interpolation. This changes motion timing/smoothness and is not visually identical.
- Importing definitions does not make each animation play. The current entity controller still chooses the idle animation; skill-to-animation triggers have not been wired.
- No successful Gradle build or in-game visual test has been completed. This is a verified resource import, not a verified Bedrock-equivalent release.

## Next fidelity-critical work

1. Reconcile the Java Sukuna geometry with the exact Bedrock geometry used by these animations, including locators and pivots.
2. Implement or approximate Catmull-Rom interpolation without breaking GeckoLib's animation parser.
3. Bind imported animation IDs to actual skill state transitions only after the native skill trigger conditions are confirmed.
