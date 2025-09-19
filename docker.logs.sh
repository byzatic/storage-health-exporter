#!/usr/bin/env bash
#
# ./docker.logs.sh                   # логи из обычного docker-compose.yml
# ./docker.logs.sh --develop         # логи из docker-compose.develop.yml
# ./docker.logs.sh service1          # логи только service1 из обычного файла
# ./docker.logs.sh --develop db      # логи db из develop-файла
#
set -e

# Выбор файла docker-compose
COMPOSE_FILE="docker-compose.yml"
for arg in "$@"; do
  if [[ "$arg" == "--develop" ]]; then
    COMPOSE_FILE="docker-compose.develop.yml"
    break
  fi
done

# Удаление флага --develop
FILTERED_ARGS=()
for arg in "$@"; do
  if [[ "$arg" != "--develop" ]]; then
    FILTERED_ARGS+=("$arg")
  fi
done

# Вывод логов
docker-compose -f "$COMPOSE_FILE" logs -f --tail=100 "${FILTERED_ARGS[@]}"