# Mahito domain hand model fallback

The supplied Bedrock resource pack contains `perfection_domain.geo.json` with 72 `poly_mesh` polygon entries. GeckoLib 4.8.4 logs show a poly-mesh parsing failure (`Array must have size 1, but has size 3`) for the external `mahitodomainpatch:geo/perfection_domain.geo.json`.

Added `assets/jujutsuenhancement/geo/perfection_domain_hands_compat.geo.json`, derived from the same pack’s `perfection_domain2.geo.json` alternative, which has the same 410 cube count and 195 bones but no poly-mesh entries. UV coordinates were scaled to the local `perfection_domain_hands.png` dimensions. This is a compatibility asset in this mod only; it does **not** overwrite or patch the external `MahitoDomainPatch` JAR and has not yet been connected to a renderer.

## Validation
- JSON parses successfully.
- 195 bones / 410 cubes.
- 0 `poly_mesh` entries.
- Game launch and visual parity remain unverified.
