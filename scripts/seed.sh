#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U calsnap -d calsnap < scripts/seed.sql
