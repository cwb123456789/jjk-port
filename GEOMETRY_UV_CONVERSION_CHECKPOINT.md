# Geometry UV conversion checkpoint

## Done
- Added `tools/convert_bedrock_geometry.py` to normalize geometry texture dimensions against paired PNGs while preserving per-face UV orientation, cube coordinates, pivots, rotations, and bone hierarchy.
- Updated geometry atlas dimensions and UV coordinates for `heian_sukuna`, `malevolent_shrine`, `yuji_domain`, and `heian_shrine`.
- The converter intentionally skips `innate_sukuna.geo.json` because this project does not currently contain a same-name `innate_sukuna.png`; a similarly named texture is not assumed to be the correct pairing.
- Resource validator passes: 8 JSON files; heian_sukuna 1,154 cubes / 6,918 per-face UV entries; malevolent_shrine 2,326 cubes / 13,956 UV faces; yuji_domain 401 cubes / 2,406 UV faces; heian_shrine 1,626 cubes / 9,756 UV faces.

## Limits
- This is a texture-atlas coordinate normalization pass, not a complete semantic conversion of all Bedrock geometry features.
- GeckoLib runtime loading and rendered appearance remain unverified because the Forge/Gradle build is still blocked by dependency download/network resolution.
