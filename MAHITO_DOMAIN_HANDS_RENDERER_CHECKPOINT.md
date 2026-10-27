# Mahito domain hands renderer checkpoint

## Implemented
- Added `mahito_domain_hands` as a dedicated short-lived client-rendered GeoEntity.
- Registered its entity type and renderer.
- Bound it to `perfection_domain_hands_compat.geo.json`, `perfection_domain_hands.png`, and the empty animation resource.
- Spawned the visual at Mahito's position when the existing stage-two domain pulse begins. It lasts 60 ticks (3 seconds) and is visual-only; the existing server-side damage pulse remains unchanged.

## Validation and limits
- `python3 tools/validate_resources.py`: PASS (15 JSON files, geometry/UV/resource references).
- `python3 tools/audit_geckolib_poly_mesh.py`: PASS (10 geometry files, no `poly_mesh`).
- `./gradlew compileJava --no-daemon`: BLOCKED before compilation because the environment cannot resolve `services.gradle.org` (`UnknownHostException`) to download Gradle 8.8.
- Model-space coordinates and visible bounds have now been uniformly reduced by 1/64 to address the extreme source dimensions. In-game launch, final apparent scale, and alignment remain unverified. This does not patch the separately installed `MahitoDomainPatch` JAR or prove its crash fixed.
