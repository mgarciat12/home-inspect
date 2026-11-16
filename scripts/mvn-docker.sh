#!/usr/bin/env bash
# Runs Maven inside a JDK 25 container (no local JDK needed).
# Testcontainers talks to the host Docker through the mounted socket.
#   scripts/mvn-docker.sh verify
#   scripts/mvn-docker.sh -pl app test -Dtest=ReportServiceTest
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TTY_FLAG=""
[ -t 1 ] && TTY_FLAG="-t"
exec docker run --rm $TTY_FLAG \
  -v "$ROOT":/workspace -w /workspace \
  -v home-inspect-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  --add-host=host.docker.internal:host-gateway \
  maven:3.9-eclipse-temurin-25 mvn -B "$@"
