# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: Runtime
# Debian-based (jammy) instead of Alpine so the Medical Classification Engine's
# Python dependencies (pandas / openpyxl / rapidfuzz / pdfplumber / python-pptx)
# install from prebuilt manylinux wheels — no native compilation on musl.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# System runtime: curl (healthcheck), PostgreSQL 16 client (pg_dump backups),
# and Python 3 for the classification engine. The explicit PGDG repository keeps
# pg_dump compatible with the project's PostgreSQL 16 containers.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /usr/share/keyrings/postgresql.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/postgresql.gpg] http://apt.postgresql.org/pub/repos/apt jammy-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client-16 python3 python3-venv python3-pip \
    && rm -rf /var/lib/apt/lists/*

# Isolated venv for the classification engine (avoids touching the system
# interpreter). Dependency set is kept in sync with the canonical source of
# truth: tools/classification-engine/requirements.txt (used for local dev).
# Inlined here because the backend build context is ./backend and cannot COPY
# the repo-root tools/ folder without breaking the prod build context.
ENV ENGINE_VENV=/opt/engine-venv
RUN python3 -m venv "$ENGINE_VENV" \
    && "$ENGINE_VENV/bin/pip" install --no-cache-dir --upgrade pip \
    && "$ENGINE_VENV/bin/pip" install --no-cache-dir \
        "pandas>=2.0" "openpyxl>=3.1" "rapidfuzz>=3.0" "pdfplumber>=0.11" "python-pptx>=0.6"

# Non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup -m -d /home/appuser appuser

# App dirs + engine mount point + backup mount point
RUN mkdir -p /app/uploads /app/logs /app/backups /app/tools/classification-engine \
    && chown -R appuser:appgroup /app/uploads /app/logs /app/backups /app/tools

# Copy JAR from builder
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar
COPY --chmod=755 docker-entrypoint.sh /app/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
