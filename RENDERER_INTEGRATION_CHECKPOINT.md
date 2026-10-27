# GeckoLib renderer integration checkpoint

Added GeckoLib 4.8.4 as a compile/runtime dependency, registered a static `malevolent_shrine` entity type, and added a first-pass `GeoModel` + `GeoEntityRenderer` pair. The entity is stationary and intended for a first render smoke test with `/summon jujutsuenhancement:malevolent_shrine`.

## Not yet verified

- No Gradle build has completed in this environment because the Gradle 8.8 distribution download previously failed with DNS/network resolution.
- No Minecraft launch or render test has been performed.
- Bedrock-to-GeckoLib geometry conversion may still have rendering issues, including cube UVs, coordinate scale, pivots, and texture dimensions.
- The model currently uses a placeholder empty animation resource and is static.

This is integration source code, not a verified playable release.

## Latest follow-up

- Added `docs/JUJUTSUCRAFT_API_AUDIT.md`, based on static inspection of the provided JujutsuCraft ver50.1 JAR. It records confirmed public procedure entry-point signatures for Malevolent Shrine, Hollow Purple, AI variants, and domain battle.
- Added `tools/validate_resources.py`. The current check validates JSON syntax and compares the Malevolent Shrine animation's bone names with its geometry. Result: 6 resource JSON files checked; the 21 animated bone names all exist in the 126-bone geometry.
- This is resource validation only. It does not validate GeckoLib loading, model UVs, actual rendering, or skill behavior in Minecraft.


## Render visibility follow-up (2026-10-09)

- Increased the shrine entity bounding dimensions from 1x1 to 32x30 blocks. The converted geometry has approximate cube extents of 451 x 430 x 473 model pixels (about 28 x 27 x 30 blocks at 16 model pixels per block), so the old tiny bounding box risked frustum culling the visible structure.
- The entity remains non-physical (`noPhysics`) and stationary, so the larger dimensions are for visibility/spawn bounds, not intended collision behavior.
- Static resource validation still does not substitute for a real client render test. Build remains blocked until Gradle 8.8 and dependencies can be resolved.

## Animation schema correction (2026-10-09)

- Corrected `tools/convert_bedrock_animation.py` so animation keyframes are emitted as GeckoLib-style objects (`{"vector": [x, y, z], "easing": "linear"}`), rather than copying Bedrock vector arrays directly. This addresses a concrete incompatibility in the previous first-pass converter.
- Regenerated `assets/jujutsuenhancement/animations/malevolent_shrine.animation.json` from the provided Bedrock source. It contains 1 animation, 21 animated bones, and 540 keyframes.
- Extended `tools/validate_resources.py` to validate keyframe object shape and supported channels in addition to JSON syntax, bone references, and renderer texture paths.
- Static validation passed: 6 resource JSON files checked; all 21 animated bone names match the geometry; renderer texture path exists; keyframe schema passes.
- The Bedrock-to-GeckoLib interpolation is still approximate: 200 Catmull-Rom keyframes were flattened to linear interpolation. Axis/pivot behavior, UV rendering, actual GeckoLib parsing, and in-game visuals remain unverified because Gradle/Minecraft has not successfully built/launched in this environment.

## Geometry UV and coordinate audit (2026-10-09)

- Extended `tools/validate_resources.py` to inspect every converted geometry's cube origin/size values, UV face structure, finite UV coordinates, and UV rectangles against each geometry's declared texture atlas dimensions.
- The Malevolent Shrine geometry has 126 bones, 2,326 cubes, and 13,956 UV faces. All UV faces remain inside the declared 256x256 atlas; no out-of-range UVs or non-finite cube coordinates were reported.
- Confirmed the registered entity controller references `animation.malevolent_shrine.form`, which exists in the paired GeckoLib animation resource.
- These are static data checks only. They do not prove that GeckoLib renders the UVs correctly or that Bedrock pivots/axes/interpolation match in-game.

## Bone hierarchy and transform-data audit (2026-10-09)

- Extended `tools/validate_resources.py` to check duplicate bone names, unresolved parent references, finite 3D bone pivots, negative cube dimensions, finite animation vectors, and finite non-negative animation timestamps.
- Re-ran the full static audit across all four converted geometry files and the shrine animation. The existing resource set passes: no duplicate bone names, unresolved parents, invalid pivots, negative cube dimensions, invalid frame times, or non-finite animation vectors were reported.
- This is a structural sanity check, not proof that Bedrock and GeckoLib use identical transform semantics. Exact pivot/axis behavior and visual alignment still require an in-game render test.

## Texture atlas scale correction (2026-10-09)

- Compared the converted geometry's declared atlas dimensions with the actual PNG dimensions.
- Found a concrete mismatch in `yuji_domain.geo.json`: UV coordinates and declared atlas were 4096x4096, but the supplied `yuji_domain.png` is 512x512. With normalized GPU UVs this would sample only the upper-left 1/8 of the image in each axis.
- Corrected the Yuji Domain geometry to declare a 512x512 atlas and divided every face UV origin and UV size by 8. This preserves normalized UV placement relative to the supplied 512x512 texture.
- Re-ran `tools/validate_resources.py`: the Yuji Domain's 2,406 UV faces now fit the declared 512x512 atlas; all geometry/animation/texture checks pass.
- This fixes a mathematically identifiable atlas mismatch but is not a substitute for confirming the visual appearance in Minecraft. The Malevolent Shrine intentionally uses a 256x256 UV coordinate space with a 4096x4096 high-resolution texture; do not blindly force every geometry declaration to equal the PNG pixel dimensions.

## Heian Sukuna animation conversion (2026-10-09)

- Extracted the original `heian_sukuna.animation.json` from the provided Jujutsu Awakening resource pack and converted it with `tools/convert_bedrock_animation.py` into `assets/jujutsuenhancement/animations/heian_sukuna.animation.json`.
- The converted resource contains 26 animations. The converter reports 8,007 Catmull-Rom interpolation warnings because the current converter approximates those curves as linear; this is explicitly not animation-parity verified.
- Static resource validation passes with the new file present (7 JSON resources checked). This animation has not yet been paired to a Heian Sukuna entity/model renderer, and it has not been tested in Minecraft.
