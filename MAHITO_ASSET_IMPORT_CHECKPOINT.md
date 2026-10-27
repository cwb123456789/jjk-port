# Mahito Bedrock asset import checkpoint

Imported directly from the user-provided Jujutsu Awakening v1.81 resource pack:
- `geo/mahito.geo.json` from `JujutsuAwa/models/entity/entities/mahito.geo.json` (16 bones, 23 cubes)
- `textures/entity/mahito.png` (64x64 texture atlas)
- `animations/mahito.animation.json` (65 animation clips converted to GeckoLib-style keyframes)
- `animations/mahito_effects.animation.json` (17 animation clips converted to GeckoLib-style keyframes)

The Bedrock animation converter flattened Catmull-Rom interpolation to linear keyframes (8,096 warnings in the main animation file and 942 in the effects file). Bedrock Molang, particle/sound events, animation controllers, and exact interpolation semantics are not ported. This is an asset-import milestone only: no Mahito renderer/entity, transformation state, or skill bridge is wired yet. Geometry/UV and resource checks do not prove runtime animation correctness. Java build and in-game rendering remain unverified.

## Forge renderer prototype follow-up (2026-10-09)

- Added `MahitoPrototypeEntity`, `MahitoPrototypeModel`, and `MahitoPrototypeRenderer` and registered `mahito_prototype` as a separate test entity.
- Added a minimal static idle clip with the exact `animation.mahito.idle` name referenced by the prototype controller. This deliberately avoids loading the large Bedrock-converted animation set before its Molang/events/interpolation are made GeckoLib-compatible.
- Test command: `/summon jujutsuenhancement:mahito_prototype`.
- Static resource validator passes across 12 JSON resources; the model has 16 bones, 23 cubes, and 102 UV faces within its 64x64 atlas.
- Not yet connected to the original JujutsuCraft Mahito entity, not a transformation implementation, and no Java build or in-game render test has succeeded. This is a separate visual smoke-test prototype.
