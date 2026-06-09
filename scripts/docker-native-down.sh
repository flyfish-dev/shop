#!/bin/sh

set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/deploy/docker/docker-compose.native.yml}"

cd "${ROOT_DIR}"
docker compose -f "${COMPOSE_FILE}" down
