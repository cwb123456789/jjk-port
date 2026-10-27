# Build environment status — r39

## Actual changes in r39

- `settings.gradle`: removed the optional `org.gradle.toolchains.foojay-resolver-convention:0.7.0` plugin so offline configuration does not stop at the toolchain downloader.
- `build.gradle`: configured the locally installed JDK 21 as the build toolchain and set `JavaCompile.options.release = 17`, so produced classes remain Java 17 bytecode. This is an environment workaround, not a claim that ForgeGradle supports every JDK 21 path without issue.
- `logs/GRADLE_DEPENDENCY_PREFLIGHT_2026-10-11-r39.log`: recorded the real offline dependency preflight and stacktrace.

## Actual command and result

Command:

```bash
/mnt/data/toolchains/gradle-8.8/bin/gradle dependencies --offline --no-daemon --stacktrace
```

Result: **BUILD FAILED before dependency graph evaluation**.

First error:

```text
Plugin [id: 'net.minecraftforge.gradle', version: '[6.0,6.2)'] was not found
```

This means removing Foojay advanced the configuration to the ForgeGradle plugin request, but ForgeGradle is not available in Gradle's offline plugin/module cache. The user-provided `ForgeGradle-6.0.54.jar` exists outside this project, but merely having that file on disk does not make it resolvable through the plugins DSL.

## Verification boundaries

- Gradle 8.8 starts: verified.
- Settings configuration gets past the former Foojay plugin failure: verified.
- ForgeGradle plugin resolution: failed offline.
- Minecraft/Forge and GeckoLib resolution: not reached.
- `compileJava`: not reached.
- In-game test: not performed.

## Next required build action

Provide a complete local Maven/cache installation for ForgeGradle 6.0.54 and its transitive plugin dependencies, or run the dependency-fetch kit on a machine with repository access and bring back the resulting cache bundle. Do not treat the standalone ForgeGradle JAR as a complete offline dependency set.
