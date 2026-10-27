# Mahito domain render-chain audit

Expanded `tools/audit_entity_render_chain.py` to cover all four registered GeckoLib paths used by this prototype: Heian Sukuna, Malevolent Shrine, Mahito's domain hands, and the approximate domain barrier. The audit verifies entity registration, client renderer registration, model-to-geometry/texture/animation references, and referenced entity animation names where present.

## Results
- `python3 tools/audit_entity_render_chain.py`: 917 checks passed, 0 warnings, 0 errors.
- `python3 tools/validate_resources.py`: 16 resource JSON files validated.
- `python3 tools/audit_geckolib_poly_mesh.py`: 11 geometry files checked; no `poly_mesh` blocks.
- `python3 tools/audit_renderer_assets.py`: 15 resource references checked; 0 errors.

These are static source/resource checks only. Java compilation and in-game rendering remain unverified because the Gradle wrapper cannot download Gradle 8.8 in the current environment (`UnknownHostException: services.gradle.org`).
