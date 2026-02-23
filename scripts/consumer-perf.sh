#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <topic> [messages]" >&2
  exit 1
fi

TOPIC=$1
FETCH_MAX=${2:-100000}
COMPOSE_FILE=${COMPOSE_FILE:-infra/docker/docker-compose.single-broker.yml}
BROKER_SERVICE=${BROKER_SERVICE:-kafka}
BOOTSTRAP=${BOOTSTRAP:-localhost:9092}
THREADS=${THREADS:-1}
TIMEOUT=${TIMEOUT:-10000}

set -x
DOCKER_DEFAULT_PLATFORM=${DOCKER_DEFAULT_PLATFORM:-} docker compose -f "$COMPOSE_FILE" exec "$BROKER_SERVICE" \
  kafka-consumer-perf-test --topic "$TOPIC" \
  --messages "$FETCH_MAX" \
  --threads "$THREADS" \
  --timeout "$TIMEOUT" \
  --bootstrap-server "$BOOTSTRAP"
