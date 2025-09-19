#!/usr/bin/env bash
# ./docker.up.sh                         # Использует docker-compose.yml
# ./docker.up.sh --develop               # Использует docker-compose.develop.yml
# ./docker.up.sh --develop service1      # Поднимает только service1 из develop-файла
set -e

# По умолчанию используется обычный compose файл
COMPOSE_FILE="docker-compose.yml"

# Проверяем наличие флага --develop
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

# Запускаем контейнеры
docker-compose -f "$COMPOSE_FILE" up -d "${FILTERED_ARGS[@]}"