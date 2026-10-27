#!/usr/bin/env bash
# Resolve project dependencies online, verify a Java compile, then export source + Gradle cache.
# Usage: tools/export_offline_build_bundle.sh [/path/to/gradle-8.8/bin/gradle] [output.tar.gz]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_BIN="${1:-${GRADLE_BIN:-}}"
OUT="${2:-$ROOT/build/offline-build-bundle.tar.gz}"
if [[ -z "$GRADLE_BIN" && -n "${GRADLE_HOME:-}" ]]; then GRADLE_BIN="$GRADLE_HOME/bin/gradle"; fi
if [[ -z "$GRADLE_BIN" ]] && command -v gradle >/dev/null 2>&1; then GRADLE_BIN="$(command -v gradle)"; fi
if [[ -z "$GRADLE_BIN" || ! -x "$GRADLE_BIN" ]]; then
  echo "ERROR: Install/provide Gradle 8.8 first; no usable local Gradle executable found." >&2
  exit 2
fi
VERSION="$($GRADLE_BIN --version 2>&1)" || { printf '%s\n' "$VERSION" >&2; exit 3; }
printf '%s\n' "$VERSION" | grep -Eq 'Gradle 8\.8([[:space:]]|$)' || { echo 'ERROR: Gradle 8.8 is required.' >&2; exit 4; }
JAVA_HOME_USED="${JAVA_HOME:-}"
java -version 2>&1 | head -n 1
cd "$ROOT"
mkdir -p build/offline-build-bundle
LOG="$ROOT/build/offline-build-bundle/resolve-and-compile.log"
echo 'Resolving dependencies and compiling sources (this phase requires repository access unless caches are complete).'
set +e
"$GRADLE_BIN" --no-daemon --refresh-dependencies dependencies compileJava jar 2>&1 | tee "$LOG"
RESULT=${PIPESTATUS[0]}
set -e
if [[ "$RESULT" -ne 0 ]]; then
  echo "ERROR: dependency resolution/build failed (exit $RESULT); bundle not exported. See $LOG" >&2
  exit "$RESULT"
fi
GRADLE_USER_HOME_USED="${GRADLE_USER_HOME:-$HOME/.gradle}"
if [[ ! -d "$GRADLE_USER_HOME_USED/caches" ]]; then echo "ERROR: Gradle cache directory missing: $GRADLE_USER_HOME_USED/caches" >&2; exit 5; fi
OUT_ABS="$(mkdir -p "$(dirname "$OUT")" && cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")"
STAGE="$ROOT/build/offline-build-bundle/stage"
rm -rf "$STAGE"
mkdir -p "$STAGE/project" "$STAGE/gradle-user-home"
# Copy project without transient build output or VCS metadata; keep the built JAR.
tar -C "$ROOT" --exclude='./.git' --exclude='./.gradle' --exclude='./build/offline-build-bundle' -cf - . | tar -C "$STAGE/project" -xf -
# Include the resolved user cache, including wrapper distributions and ForgeGradle/Minecraft artifacts.
cp -a "$GRADLE_USER_HOME_USED/." "$STAGE/gradle-user-home/"
{
  echo 'JujutsuEnhancement offline build bundle manifest'
  echo "Created UTC: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "Gradle: 8.8"
  echo "Java runtime: $(java -version 2>&1 | head -n 1)"
  echo "GRADLE_USER_HOME source: $GRADLE_USER_HOME_USED"
  echo 'Pre-export tasks passed: dependencies, compileJava, jar'
  echo 'Note: portability depends on platform-specific Gradle/native artifacts and licenses; verify on the target machine with --offline.'
  echo 'Project JARs:'
  find "$STAGE/project/build/libs" -maxdepth 1 -type f -name '*.jar' -printf '%f %s bytes\n' 2>/dev/null || true
} > "$STAGE/OFFLINE_BUNDLE_MANIFEST.txt"
tar -C "$STAGE" -czf "$OUT_ABS" .
echo "Offline bundle exported: $OUT_ABS"
sha256sum "$OUT_ABS"
echo 'On the target machine, unpack, set GRADLE_USER_HOME to gradle-user-home, then test: ./gradlew --offline compileJava jar'
