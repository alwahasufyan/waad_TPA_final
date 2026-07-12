#!/bin/sh
set -eu

# Named Docker volumes are mounted after image build, so Dockerfile-time chown
# does not apply to existing/local upload volumes. Fix only runtime-owned
# directories, then drop privileges before starting Spring Boot.
mkdir -p /app/uploads/classification /app/logs
chown -R appuser:appgroup /app/uploads /app/logs 2>/dev/null || true

if command -v runuser >/dev/null 2>&1; then
  exec runuser -u appuser -- sh -c 'exec java $JAVA_OPTS -jar /app/app.jar'
fi

exec su -s /bin/sh appuser -c 'exec java $JAVA_OPTS -jar /app/app.jar'
