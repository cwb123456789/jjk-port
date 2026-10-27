#!/usr/bin/env bash
# Build helper for environments where Gradle's wrapper distribution cannot be downloaded.
# Usage: tools/build_with_local_gradle.sh [/path/to/gradle-8.8/bin/gradle]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CANDIDATE="${1:-${GRADLE_BIN:-}}"
if [[ -z "$CANDIDATE" && -n "${GRADLE_HOME:-}" ]]; then
  CANDIDATE="$GRADLE_HOME/bin/gradle"
fi
if [[ -z "$CANDIDATE" ]] && command -v gradle >/dev/null 2>&1; then
  CANDIDATE="$(command -v gradle)"
fi
if [[ -z "$CANDIDATE" || ! -x "$CANDIDATE" ]]; then
  cat >&2 <<'MSG'
ERROR: No executable local Gradle was found.

Provide a complete Gradle 8.8 installation using one of:
  tools/build_with_local_gradle.sh /path/to/gradle-8.8/bin/gradle
  GRADLE_HOME=/path/to/gradle-8.8 tools/build_with_local_gradle.sh
  GRADLE_BIN=/path/to/gradle-8.8/bin/gradle tools/build_with_local_gradle.sh

The wrapper needs https://services.gradle.org/distributions/gradle-8.8-bin.zip,
which may be unavailable in restricted/DNS-blocked environments. A .part file
is not a usable Gradle installation.
MSG
  exit 2
fi
VERSION_OUTPUT="$("$CANDIDATE" --version 2>&1)" || { printf '%s\n' "$VERSION_OUTPUT" >&2; exit 3; }
printf '%s\n' "$VERSION_OUTPUT" | grep -Eq 'Gradle 8\.8([[:space:]]|$)' || {
  echo "ERROR: Gradle 8.8 is required by this helper; found:" >&2
  printf '%s\n' "$VERSION_OUTPUT" | head -8 >&2
  exit 4
}
cd "$ROOT"
printf '%s\n' 'Gradle 8.8 detected; starting compileJava.'
"$CANDIDATE" compileJava --no-daemon
