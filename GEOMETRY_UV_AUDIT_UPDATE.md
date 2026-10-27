# Bedrock geometry UV audit update

## Change
- Replaced the stale `ACTION REQUIRED` warning in `tools/audit_gecko_geometry.py` with structural validation of each per-face UV object.
- For Heian Sukuna and Malevolent Shrine, the audit now verifies all six faces (`north`, `east`, `south`, `west`, `up`, `down`), two-number `uv` and `uv_size` arrays, numeric finite coordinates, and UV rectangles within the declared texture atlas.
- Negative `uv_size` values are permitted because they can encode mirrored/flipped UV orientation; they are not treated as malformed.

## Current static result
- `heian_sukuna.geo.json`: 1,154 cubes; 1,153 cubes with per-face UV; atlas 512x512; no out-of-bounds UV rectangles.
- `malevolent_shrine.geo.json`: 2,326 cubes; all 2,326 cubes with per-face UV; atlas 4096x4096; no out-of-bounds UV rectangles.

## Limits
This is a static schema/atlas audit only. It does not establish that GeckoLib 4.8.4 accepts every model feature or that either model renders correctly in Minecraft. Forge compilation and in-game render verification remain outstanding.
