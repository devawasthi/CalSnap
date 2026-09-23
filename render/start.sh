#!/bin/sh
set -eu

stop() {
  kill -TERM "${api_pid:-}" "${nginx_pid:-}" 2>/dev/null || true
  wait "${api_pid:-}" "${nginx_pid:-}" 2>/dev/null || true
}
trap stop INT TERM EXIT

if [ -z "${DATABASE_URL:-}" ]; then
  DATABASE_URL="jdbc:postgresql://${DATABASE_HOST:-localhost}:${DATABASE_PORT:-5432}/${DATABASE_NAME:-calsnap}"
  export DATABASE_URL
fi

PORT=8080 java -Xms48m -Xmx300m -XX:+UseSerialGC -Djava.awt.headless=true -jar /app/app.jar &
api_pid=$!
nginx -g 'daemon off;' &
nginx_pid=$!

while kill -0 "${api_pid}" 2>/dev/null && kill -0 "${nginx_pid}" 2>/dev/null; do
  sleep 2
done

exit 1
