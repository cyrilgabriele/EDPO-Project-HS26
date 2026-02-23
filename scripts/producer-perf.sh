#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <topic> [num-records] [record-size-bytes]" >&2
  exit 1
fi

TOPIC=$1
NUM_RECORDS=${2:-100000}
RECORD_SIZE=${3:-512}
THROUGHPUT=${THROUGHPUT:--1}
COMPOSE_FILE=${COMPOSE_FILE:-infra/docker/docker-compose.single-broker.yml}
BROKER_SERVICE=${BROKER_SERVICE:-kafka}
BOOTSTRAP=${BOOTSTRAP:-localhost:9092}
PRODUCER_PROPS=${PRODUCER_PROPS:-acks=all,compression.type=snappy,linger.ms=5,batch.size=65536}

IFS=',' read -ra PROP_LIST <<< "$PRODUCER_PROPS"
PROP_ARGS=("bootstrap.servers=$BOOTSTRAP")
for entry in "${PROP_LIST[@]}"; do
  [[ -n $entry ]] || continue
  PROP_ARGS+=("$entry")
fi

set -x
DOCKER_DEFAULT_PLATFORM=${DOCKER_DEFAULT_PLATFORM:-} docker compose -f "$COMPOSE_FILE" exec "$BROKER_SERVICE" \
  kafka-producer-perf-test --topic "$TOPIC" \
  --num-records "$NUM_RECORDS" \
  --record-size "$RECORD_SIZE" \
  --throughput "$THROUGHPUT" \
  --producer-props "${PROP_ARGS[@]}"
