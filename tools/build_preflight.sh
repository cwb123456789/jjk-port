#!/usr/bin/env bash
# Diagnose the build prerequisites and repository reachability without pretending to compile.
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
printf '%s\n' '=== Java ==='
if command -v java >/dev/null 2>&1; then java -version 2>&1 | head -4; else echo 'ERROR: java not found'; fi
printf '\n%s\n' '=== Gradle 8.8 ==='
CANDIDATE="${1:-${GRADLE_BIN:-}}"
if [[ -z "$CANDIDATE" && -n "${GRADLE_HOME:-}" ]]; then CANDIDATE="$GRADLE_HOME/bin/gradle"; fi
if [[ -z "$CANDIDATE" ]] && command -v gradle >/dev/null 2>&1; then CANDIDATE="$(command -v gradle)"; fi
if [[ -n "$CANDIDATE" && -x "$CANDIDATE" ]]; then
  "$CANDIDATE" --version 2>&1 | head -12
else
  echo 'ERROR: no executable local Gradle found; the wrapper needs to download Gradle 8.8.'
fi
printf '\n%s\n' '=== Required repositories ==='
for host in services.gradle.org maven.minecraftforge.net dl.cloudsmith.io plugins.gradle.org; do
  if getent hosts "$host" >/dev/null 2>&1; then
    printf 'DNS OK: %s\n' "$host"
  else
    printf 'DNS FAIL: %s\n' "$host"
  fi
done
printf '\n%s\n' '=== Project dependency declarations ==='
grep -nE 'net\.minecraftforge\.gradle|foojay-resolver|geckolib-forge|net\.minecraftforge:forge|minecraft_version=|forge_version=|mapping_version=|languageVersion' \
  "$ROOT/settings.gradle" "$ROOT/build.gradle" "$ROOT/gradle.properties" 2>/dev/null || true
printf '\n%s\n' 'Preflight only: this script does not download dependencies or claim a successful build.'
