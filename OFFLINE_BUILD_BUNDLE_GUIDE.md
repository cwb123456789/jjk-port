# Offline build bundle export

This project cannot ship Minecraft/ForgeGradle dependencies until they have actually been resolved. Use `tools/export_offline_build_bundle.sh` on a machine with Gradle 8.8 and repository access.

The script performs `dependencies`, `compileJava`, and `jar` before exporting. If any step fails, it does not create a bundle. The archive includes the project and the selected `GRADLE_USER_HOME` cache, and includes a manifest and build log.

## Linux/macOS

```bash
GRADLE_USER_HOME="$HOME/.gradle" tools/export_offline_build_bundle.sh /path/to/gradle-8.8/bin/gradle
```

## Windows

Use WSL/Git Bash for the shell script, or run the dependency preparation PowerShell script first and archive the project plus the Gradle user cache. A cache populated on one operating system may not contain all native/platform-specific artifacts for another.

## Important limits

- A successful online compile is required before export.
- The archive can be large because it contains Gradle caches.
- Minecraft assets/runtime files and redistribution/licensing constraints may mean the archive is not a complete standalone game installation.
- Verify on the target environment using `./gradlew --offline compileJava jar` (or `gradlew.bat --offline compileJava jar`).
