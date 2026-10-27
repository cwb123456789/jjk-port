# Game crash triage — 2026-10-05 client log

## Confirmed root cause from the supplied FCL log

The crash occurs during the `Rendering overlay` resource-loading phase, before this project's prototype entity needs to be summoned. GeckoLib 4.8.4 reports:

- Failing resource: `mahitodomainpatch:geo/perfection_domain.geo.json`
- Failing deserializer: `software.bernie.geckolib.loading.json.raw.PolyMesh`
- Exception: `Array must have size 1, but has size 3`

This points to a geometry-format incompatibility in the separately installed `MahitoDomainPatch` mod, specifically its `perfection_domain.geo.json` poly-mesh data. The trace does **not** identify `jujutsuenhancement` assets as the failing resource, so changing this project's Mahito geometry alone would not fix that reported crash.

## Next action to resolve the launch crash

1. Temporarily remove `MahitoDomainPatch-1.20.1-GeoModel-Java17-Fixed*.jar` and launch the same profile again. This is a diagnostic isolation test, not a permanent fix.
2. If the game opens, the patch mod needs a GeckoLib-4.8.4-compatible `perfection_domain.geo.json` (for example, converted cube geometry rather than unsupported poly-mesh data), or a version of that mod built for the installed GeckoLib version.
3. If it still crashes, capture the newest crash report and identify its first `Caused by` resource path; it may be a separate failure.

Added `tools/audit_geckolib_poly_mesh.py` to flag poly-mesh geometry in this project's own assets. This audit cannot inspect the separate installed mod JAR unless that JAR is provided.

## Verification boundary

The diagnosis is based on the stack trace in the supplied log. The remove-and-launch isolation test has not been run from this environment, and no claim is made that the user's game is fixed yet.
