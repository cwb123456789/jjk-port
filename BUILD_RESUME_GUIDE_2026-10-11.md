# Build resume guide — 2026-10-11

## Verified blocker

This environment cannot resolve `services.gradle.org`, `downloads.gradle.org`, or `github.com` at the time of this check. The Gradle wrapper cache contains only a zero-byte `gradle-8.8-bin.zip.part`, not a usable distribution. Therefore the wrapper cannot reach Java compilation here.

## Resume with a complete local Gradle 8.8 distribution

After a complete Gradle 8.8 binary distribution has been provided and unpacked, run from the project root:

```bash
tools/build_with_local_gradle.sh /absolute/path/to/gradle-8.8/bin/gradle
```

Alternatively set `GRADLE_HOME` or `GRADLE_BIN`. The helper checks the reported Gradle version and invokes `compileJava --no-daemon`; it does not bypass dependency resolution, so Forge/Minecraft dependencies may still require an available Gradle/Maven cache or network access.

## Status boundary

The helper's shell syntax and missing-Gradle diagnostic are tested. Java compilation and Minecraft runtime behavior are **not** verified by this note.
