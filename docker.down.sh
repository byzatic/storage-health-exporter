#!/usr/bin/env bash
# ./docker.down.sh --develop      # использует docker-compose.develop.yml
# ./docker.down.sh                # использует docker-compose.yml
# ./docker.down.sh -v --develop   # использует docker-compose.develop.yml и передаёт -v
set -e

# Определяем, использовать ли develop
COMPOSE_FILE="docker-compose.yml"

for arg in "$@"; do
  if [[ "$arg" == "--develop" ]]; then
    COMPOSE_FILE="docker-compose.develop.yml"
    break
  fi
done

# Удаляем флаг --develop из аргументов, чтобы не передавать его в docker-compose
FILTERED_ARGS=()
for arg in "$@"; do
  if [[ "$arg" != "--develop" ]]; then
    FILTERED_ARGS+=("$arg")
  fi
done

docker-compose -f "$COMPOSE_FILE" down "${FILTERED_ARGS[@]}"