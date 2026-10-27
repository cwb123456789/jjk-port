# Heian Sukuna in-game test plan

## Current entry point
- Entity ID: `jujutsuenhancement:heian_sukuna`
- Intended command: `/summon jujutsuenhancement:heian_sukuna ~ ~ ~`
- Model resource: `assets/jujutsuenhancement/geo/heian_sukuna.geo.json`
- Texture resource: `assets/jujutsuenhancement/textures/entity/heian_sukuna.png`
- Animation resource: `assets/jujutsuenhancement/animations/heian_sukuna.animation.json`

## Test sequence once a build environment is available
1. Run `./gradlew compileJava` and resolve Java/GeckoLib/Forge errors first.
2. Run the client with Forge 1.20.1 and GeckoLib 4.8.4 installed.
3. Execute the summon command above.
4. Confirm model loads, texture UVs are correct, entity is not invisible or culled, and idle animation plays.
5. Inspect `latest.log` for GeckoLib geometry/animation parse errors.
6. Test at 16, 32, and 64 blocks away to catch bounding-box/frustum issues.

## Known blocker
The Gradle wrapper currently cannot download Gradle 8.8 because this environment cannot resolve `services.gradle.org` (`UnknownHostException`). Consequently no compile or in-game test result is claimed. Static resource validation is not a substitute for those tests.
