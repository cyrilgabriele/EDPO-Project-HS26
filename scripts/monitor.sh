#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE=${COMPOSE_FILE:-infra/docker/docker-compose.single-broker.yml}
BROKER_SERVICE=${BROKER_SERVICE:-kafka}
BOOTSTRAP=${BOOTSTRAP:-localhost:9092}
GROUP=${GROUP:-}
TOPIC=${TOPIC:-}
INTERVAL=${INTERVAL:-20}
CONTAINERS=${CONTAINERS:-}

consumer_args=(docker compose -f "$COMPOSE_FILE" exec "$BROKER_SERVICE" kafka-consumer-groups --bootstrap-server "$BOOTSTRAP")
if [[ -n $GROUP ]]; then
  consumer_args+=(--group "$GROUP")
else
  consumer_args+=(--all-groups)
fi
consumer_args+=(--describe)

topic_args=(docker compose -f "$COMPOSE_FILE" exec "$BROKER_SERVICE" kafka-topics --bootstrap-server "$BOOTSTRAP" --describe)
if [[ -n $TOPIC ]]; then
  topic_args+=(--topic "$TOPIC")
fi

while true; do
  echo "===== $(date -u) ====="
  echo "# Consumer lag"
  "${consumer_args[@]}" || true
  echo "# Topic layout"
  "${topic_args[@]}" || true
  echo "# Docker stats"
  if [[ -n $CONTAINERS ]]; then
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" $CONTAINERS || true
  else
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" || true
  fi
  sleep "$INTERVAL"
  echo
done
