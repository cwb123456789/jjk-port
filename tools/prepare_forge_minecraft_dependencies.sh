#!/usr/bin/env bash
# Resolve ForgeGradle + Minecraft/Forge development dependencies for this project.
# Requires network access on the first run; Gradle caches the resolved artifacts locally.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java is not installed or not on PATH." >&2
  exit 2
fi
JAVA_VERSION="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
if [[ -z "$JAVA_VERSION" || "$JAVA_VERSION" -lt 17 ]]; then
  echo "ERROR: Gradle/ForgeGradle requires Java 17 or newer to run this setup; found: ${JAVA_VERSION:-unknown}." >&2
  exit 2
fi

if [[ ! -x ./gradlew ]]; then
  chmod +x ./gradlew 2>/dev/null || true
fi
if [[ ! -f ./gradlew ]]; then
  echo "ERROR: gradlew not found in project root: $ROOT" >&2
  exit 2
fi

mkdir -p build/dependency-setup
LOG="build/dependency-setup/prepare-dependencies.log"
echo "Project: $ROOT"
echo "Java: $(java -version 2>&1 | head -n 1)"
echo "Minecraft/Forge versions from gradle.properties:"
grep -E '^(minecraft_version|forge_version|mapping_channel|mapping_version)=' gradle.properties || true
echo ""
echo "Resolving Gradle wrapper, ForgeGradle plugin, Minecraft mappings, Forge userdev and declared mod dependencies."
echo "First run requires access to Gradle Plugin Portal, Maven Central, Minecraft/Forge Maven, and the GeckoLib Maven repository."
echo "Full log: $LOG"
set +e
./gradlew --no-daemon --refresh-dependencies dependencies compileJava 2>&1 | tee "$LOG"
RESULT=${PIPESTATUS[0]}
set -e
if [[ "$RESULT" -ne 0 ]]; then
  echo ""
  echo "DEPENDENCY PREPARATION FAILED (exit $RESULT). See $LOG for the first missing URL/artifact."
  echo "If the error is DNS/network-related, run this script on a machine with internet access, then rerun it there after network access is restored."
  exit "$RESULT"
fi

echo ""
echo "Dependency resolution and compileJava completed."
echo "Gradle cache location: ${GRADLE_USER_HOME:-$HOME/.gradle}"
echo "Build output (if jar task is run): build/libs/"
