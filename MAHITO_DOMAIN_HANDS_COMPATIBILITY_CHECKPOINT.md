# Mahito perfection-domain hand model compatibility checkpoint

## Imported asset
- Source: `咒术觉醒v1.81.mcaddon` resource pack, `JujutsuAwa/models/entity/mahito/perfection_hands/perfection_domain.geo.json`.
- Ported to `assets/jujutsuenhancement/geo/perfection_domain_hands.geo.json` with UV coordinates rescaled from the declared 21x21 atlas to the actual 128x128 texture.
- Texture: `assets/jujutsuenhancement/textures/entity/perfection_domain_hands.png`.
- Retains cube-based hand/sign geometry (410 cubes before removing the unsupported mesh bone).

## Compatibility change
The source has a `barrier` bone using Bedrock `poly_mesh` (62 positions, 72 polygons). GeckoLib 4.8.4's `PolyMesh` deserializer is the exact class named in the user's client crash, with `Array must have size 1, but has size 3`. The imported compatibility asset therefore omits that one unsupported `barrier` mesh bone while retaining the cube-based hands and other cube geometry. This is a compatibility asset, not a full domain-barrier replacement. The renderer-bound compatibility geometry is uniformly scaled by 1/64 in model space (cube origins/sizes, pivots, and visible bounds) because the raw source coordinates were far too large for the effect entity. UV coordinates and rotation angles are preserved.

## Verification boundary
JSON syntax and cube/UV audits can validate resource structure but cannot establish in-game appearance. The compatibility asset is bound to the new short-lived hand-sign entity and renderer; Java compilation and in-game rendering remain unverified because the Gradle wrapper cannot fetch Gradle 8.8 in this environment (DNS/network unavailable).
