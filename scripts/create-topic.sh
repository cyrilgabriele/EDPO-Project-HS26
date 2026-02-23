#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <topic-name> [partitions] [replication]" >&2
  exit 1
fi

TOPIC=$1
PARTITIONS=${2:-3}
REPLICATION=${3:-1}
COMPOSE_FILE=${COMPOSE_FILE:-infra/docker/docker-compose.single-broker.yml}
BROKER_SERVICE=${BROKER_SERVICE:-kafka}
BOOTSTRAP=${BOOTSTRAP:-localhost:9092}

set -x
DOCKER_DEFAULT_PLATFORM=${DOCKER_DEFAULT_PLATFORM:-} docker compose -f "$COMPOSE_FILE" exec "$BROKER_SERVICE" \
  kafka-topics --create --if-not-exists \
  --topic "$TOPIC" \
  --bootstrap-server "$BOOTSTRAP" \
  --partitions "$PARTITIONS" \
  --replication-factor "$REPLICATION"
