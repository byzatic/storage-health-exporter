#!/usr/bin/env bash
# ./docker.build.sh up --develop
# ./docker.build.sh build service-name
# ./docker.build.sh logs -f service --develop
set -e

# По умолчанию основной compose файл
COMPOSE_FILE="docker-compose.yml"

# Проверяем флаг --develop
for arg in "$@"; do
  if [[ "$arg" == "--develop" ]]; then
    COMPOSE_FILE="docker-compose.develop.yml"
    break
  fi
done

# Удаляем --develop из аргументов
FILTERED_ARGS=()
for arg in "$@"; do
  if [[ "$arg" != "--develop" ]]; then
    FILTERED_ARGS+=("$arg")
  fi
done

# Запускаем сборку
docker-compose -f "$COMPOSE_FILE" build "${FILTERED_ARGS[@]}"