# r71 — offline ForgeGradle resolution check

Date: 2026-10-11

## Attempt

Command:

```bash
/mnt/data/toolchains/gradle-8.8/bin/gradle --offline compileJava --stacktrace
```

Working directory: project root.

## Result

Build fails while evaluating `build.gradle` at line 5. Gradle cannot resolve the plugin marker artifact `net.minecraftforge.gradle:net.minecraftforge.gradle.gradle.plugin:[6.0,6.2)` from its plugin repositories, including in offline mode. Java compilation does not start. This does not establish whether the Java source compiles.

A local `/mnt/data/ForgeGradle-6.0.54.jar` exists and contains `META-INF/gradle-plugins/net.minecraftforge.gradle.properties` pointing to `net.minecraftforge.gradle.userdev.UserDevPlugin`, but the local file alone is not a complete Maven repository and the expected plugin marker/complete dependency graph is not present in the inspected Gradle module cache. No speculative build-file change was applied because that would not supply missing transitive dependencies.

## Status

- Gradle 8.8 executable: present.
- ForgeGradle JAR: present locally.
- Plugin marker and complete offline dependency graph: not found in inspected cache.
- `compileJava`: blocked before Java compilation.
- Installable JAR: not produced.

## Next required action

Provide network access to Forge Maven / Gradle Plugin Portal, or provide a complete Gradle dependency cache / Maven repository for ForgeGradle 6.0.54 and its transitive dependencies. Then rerun `compileJava` and address any actual source errors.
