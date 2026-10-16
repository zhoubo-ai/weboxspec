#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if [[ -z "${JAVA_HOME:-}" && -x "$project_dir/.local/toolchains/java17/bin/java" ]]; then
  export JAVA_HOME="$project_dir/.local/toolchains/java17"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
: "${WEBOX_E2E_FIXTURE_KEY:?An ephemeral fixture key is required}"
"$project_dir/scripts/backend.sh" test-compile dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=target/test-classpath.txt -q
cd "$project_dir/backend"
exec java -Duser.timezone=UTC -cp "target/test-classes:target/classes:$(cat target/test-classpath.txt)" com.webox.support.E2eBackend
