#!/usr/bin/env bash
set -euo pipefail
webox_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" && -x "$webox_root/.local/toolchains/java17/bin/java" ]]; then
  export JAVA_HOME="$webox_root/.local/toolchains/java17"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
webox_java_version="$(java -version 2>&1 | head -1)"
case "$webox_java_version" in
  *'version "17.'*) ;;
  *) printf 'JDK 17 is required; found %s. Set JAVA_HOME to a JDK 17 installation.\n' "$webox_java_version" >&2; exit 1 ;;
esac
export MAVEN_USER_HOME="${MAVEN_USER_HOME:-$webox_root/.local/maven}"
export MAVEN_SKIP_RC=1
cd "$webox_root/backend"
exec ./mvnw "-Dmaven.repo.local=$MAVEN_USER_HOME/repository" "$@"
