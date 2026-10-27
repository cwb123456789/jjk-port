# Build dependency inventory — 2026-10-11

This inventory was derived from the checked-in Gradle files. It is not a claim that these dependencies were successfully downloaded or compiled.

## Project targets

- Minecraft: `1.20.1`
- Forge: `47.4.26` (`minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"`)
- Java compile toolchain: Java 17 (`java.toolchain.languageVersion = JavaLanguageVersion.of(17)`)
- Mappings: Mojang official mappings for `1.20.1`
- Gradle wrapper: `8.8`

## Gradle plugins and libraries

| Component | Declaration | Repository / resolver |
|---|---|---|
| ForgeGradle | `net.minecraftforge.gradle` version range `[6.0,6.2)` | Gradle Plugin Portal and Forge Maven (`https://maven.minecraftforge.net/`) from `settings.gradle` |
| Foojay toolchain resolver | `org.gradle.toolchains.foojay-resolver-convention` version `0.7.0` | Gradle Plugin Portal; may be involved if Gradle needs to provision a Java 17 toolchain |
| GeckoLib | `software.bernie.geckolib:geckolib-forge-1.20.1:4.8.4`, wrapped in `fg.deobf(...)` | `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/` |
| Minecraft + Forge development bundle | `net.minecraftforge:forge:1.20.1-47.4.26` | Forge Maven; ForgeGradle performs setup/remapping and may resolve additional Minecraft artifacts |
| Other transitive libraries | Resolved transitively by ForgeGradle/Forge/GeckoLib | Forge Maven, Maven Central, plugin repositories, and GeckoLib repository as applicable |

## Current environment check

- Java runtime detected: OpenJDK `21.0.12.1`.
- Project requests a Java 17 toolchain. A local JDK 17 was not verified in this attempt; the Foojay resolver may need network access to provision one.
- Gradle 8.8 is **not included** in the uploaded setup-kit ZIP. That ZIP contains scripts which download the official distribution and verify its SHA-256.
- The Gradle wrapper build was attempted. It stopped before evaluating the project because Java could not resolve `services.gradle.org` (`UnknownHostException`).
- A direct HTTPS probe to `maven.minecraftforge.net` also failed DNS resolution. Therefore ForgeGradle, Forge/Minecraft and GeckoLib resolution could not yet begin or be verified.

## Next build sequence once network or artifacts are available

1. Install/provide the complete Gradle 8.8 binary distribution and verify `gradle --version` reports 8.8.
2. Ensure a JDK 17 toolchain is available locally, or allow the configured Foojay resolver to provision it.
3. Run `tools/build_with_local_gradle.sh /absolute/path/to/gradle-8.8/bin/gradle` from this project root.
4. If dependency resolution fails, preserve the complete Gradle log and address the first unresolved plugin/artifact/repository before rerunning. Do not treat static audits as a successful compile.
5. After `compileJava` passes, run the relevant Forge client/server launch or GameTest checks; compilation alone does not verify in-game behavior.

## Validation boundary

The wrapper attempt and static audit log are included under `logs/`. The 13 static audit suites pass, but Java compilation and in-game behavior remain unverified due to the DNS failure described above.
