# Mahito domain hands scale checkpoint

## Change
The imported cube geometry contained model-space bounds of roughly 2,835 units across while its declared visible bounds were about 201 units. At GeckoLib's normal model-to-world conversion this would be dramatically oversized relative to the 4x4-block effect entity. The compatibility geometry now scales cube origins, cube dimensions, bone pivots, and visible bounds uniformly by 1/64. UV coordinates and rotation angles are unchanged.

## Checks
- `python3 tools/validate_resources.py`: PASS (15 JSON files; geometry, UV, animation schema, and renderer texture references).
- `python3 tools/audit_geckolib_poly_mesh.py`: PASS (10 geometry files; no `poly_mesh`).
- `python3 tools/audit_renderer_assets.py`: PASS (resource references).

## Limits
This is a mathematically grounded sizing correction, not a visual confirmation. The exact in-game size, orientation, and hand pose still require a Forge client run. Java compilation is currently blocked because Gradle 8.8 cannot be downloaded in this environment due to DNS/network failure. This asset does not patch the separate `MahitoDomainPatch` mod that appeared in the crash report.
