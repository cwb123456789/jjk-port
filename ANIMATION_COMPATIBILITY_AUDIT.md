# Animation-to-geometry compatibility audit

## Why this audit exists

Matching animation and geometry filenames are not enough to prove that the skeletons are compatible. This audit compares each animation's bone names against every converted geometry and prints the best match.

## Current findings

- `malevolent_shrine.animation.json` matches `malevolent_shrine.geo.json`: 21/21 animated bone names.
- `heian_sukuna.animation.json` matches `innate_sukuna.geo.json` only 6/14 animated bone names. It is **not safe to wire this animation to that geometry** yet.
- `heian_shrine.geo.json` (195 bones) and `yuji_domain.geo.json` (2 bones) have no corresponding dedicated animation resource in the current project.

## Next integration order

1. Extract the correct Heian Sukuna character geometry and matching animation set from the Bedrock add-on; do not use a domain/shrine geometry as a character substitute.
2. Convert and validate that geometry/animation pair together.
3. Only then create the GeckoLib model, entity and renderer for the character.
4. Keep shrine/domain scenery entities separate from character entities.

## Verification

Run `python3 tools/validate_resources.py` from the project root. The cross-audit prints compatibility warnings while preserving a successful static-resource validation status when JSON and resource structure are valid. A passing audit is not a Forge build or in-game rendering test.


## Latest geometry-pair verification (2026-10-10)

- Added `tools/audit_sukuna_geometry_pair.py` to compare the preserved Bedrock Heian Sukuna geometry with the runtime GeckoLib geometry.
- The source and runtime skeletons both contain 62 bones and their bone-name sets match exactly. The runtime atlas is intentionally expanded to 512x512 from the source 64x64 atlas.
- The audit confirms that all **currently retained runtime animation channels** target valid runtime bones. This does not mean the eight unsupported source animation bone channels were recovered; those channels remain omitted from runtime output and preserved only in the source reference.
- Run: `python3 tools/audit_sukuna_geometry_pair.py`. A pass is a static compatibility check, not an in-game test.
