#!/usr/bin/env bash
# Build one service on the host (warm ~/.m2 cache) and redeploy it via the
# local docker-compose override that does no Maven inside the container.
#
# Usage:
#   scripts/fast-rebuild.sh <service> [<service> ...]
#
# Examples:
#   scripts/fast-rebuild.sh user-service
#   scripts/fast-rebuild.sh portfolio-service transaction-service
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <service> [<service> ...]" >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

services=("$@")
pl_args=""
for svc in "${services[@]}"; do
  if [[ ! -d "$svc" ]]; then
    echo "no such service directory: $svc" >&2
    exit 1
  fi
  pl_args="${pl_args:+$pl_args,}$svc"
done

echo "==> mvn package: $pl_args"
mvn -pl "$pl_args" -am -DskipTests -T 1C -q package

echo "==> docker compose up -d --build ${services[*]}"
docker compose \
  -f docker/docker-compose.yml \
  -f docker/docker-compose.local.yml \
  up -d --build "${services[@]}"

echo "==> done"
