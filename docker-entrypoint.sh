#!/usr/bin/env bash
set -e
set -u

JAR_PATH="/app/app.jar"
JAVA_OPTS=(
  -server
  "-Xms${XMS:-512m}"
  "-Xmx${XMX:-1024m}"
)

main() {
  echo "[INFO] external args> ${JAVA_OPTS[*]}"
  exec java "${JAVA_OPTS[@]}" -jar "$JAR_PATH"
}

main "$@"