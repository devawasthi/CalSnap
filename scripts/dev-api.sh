#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/../backend"
export APP_ENV=local
export PUBLIC_ORIGIN="${PUBLIC_ORIGIN:-http://localhost:5173}"
export JWT_SECRET="${JWT_SECRET:-local-development-only-change-before-deploy-9284}"
export DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5438/calsnap}"
export VISION_MODE="${VISION_MODE:-stub}"
mkdir -p .local
cp target/calsnap-1.0.0.jar .local/runtime.jar
exec java -Djava.awt.headless=true -jar .local/runtime.jar
