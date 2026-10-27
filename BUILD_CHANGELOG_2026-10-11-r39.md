# r39 changelog

## Files changed

1. `settings.gradle`
   - Removed optional Foojay toolchain resolver plugin `0.7.0` to remove the first offline configuration blocker.
2. `build.gradle`
   - Build toolchain uses locally installed JDK 21.
   - Java compile release is pinned to 17 for Minecraft 1.20.1 runtime compatibility.
3. `logs/GRADLE_DEPENDENCY_PREFLIGHT_2026-10-11-r39.log`
   - Added complete output of the real Gradle dependency preflight.
4. `BUILD_ENVIRONMENT_STATUS_2026-10-11-r39.md`
   - Recorded exact status and remaining blocker.

## Functional gameplay code

No gameplay/skill source was changed in r39. The goal of this step was to remove the confirmed Foojay blocker and get a new, real Gradle error. The next feature change must be made against a compilable ForgeGradle environment to avoid guessing at API errors.
