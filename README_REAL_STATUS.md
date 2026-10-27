# Jujutsu Enhancement 0.1.0 test project

Target: Minecraft 1.20.1, Forge 47.4.26, Java 17.

## Current implementation
- Forge MDK project initialized from the supplied Forge 1.20.1-47.4.26 MDK.
- Mod ID: `jujutsuenhancement`.
- Main mod class logs startup and reports whether `jujutsucraft` is loaded.
- Optional dependency metadata for JujutsuCraft 50.

## Important status
This is a source project, NOT a playable JAR yet. The build was attempted in the current environment but failed before Gradle could start because it could not resolve `services.gradle.org` (network/DNS unavailable). No Minecraft runtime test has been performed.

## Build on a machine with internet access
1. Install a JDK 17.
2. Extract this project.
3. Open a terminal in this directory.
4. Run `./gradlew build` on Linux/macOS, or `gradlew.bat build` on Windows.
5. The JAR should appear under `build/libs/` if Gradle can download its distribution and dependencies.

## Important resource compatibility note
The supplied `.mcaddon` is a Bedrock Edition add-on. Its behavior/resource packs use Bedrock JSON and JavaScript APIs such as `@minecraft/server`; Forge Java Edition cannot load it directly as a mod. Its models, animations, textures and effects need selective conversion/porting. The supplied JujutsuCraft JAR is a Java Forge mod and declares mod ID `jujutsucraft`, version `50`.
Native Flame Arrow precondition audit: see `NATIVE_FLAME_ARROW_PRECONDITIONS.md` and `tools/audit_native_flame_arrow_preconditions.py`. It confirms update-time owner/skill/counter gates, but not the native skill selector or full activation chain.
