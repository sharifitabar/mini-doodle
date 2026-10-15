# ---- Build stage ----
FROM gradle:8.14.3-jdk21 AS build

# jOOQ code generation reads the migrated schema from a live PostgreSQL. Start one inside
# the build container (a plain `docker build` has no Docker daemon for Testcontainers).
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends postgresql \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . .

RUN set -eux; \
    PG_VERSION="$(ls /usr/lib/postgresql | sort -V | tail -n1)"; \
    pg_ctlcluster "$PG_VERSION" main start; \
    su postgres -c "psql -c \"ALTER USER postgres PASSWORD 'postgres'\""; \
    JOOQ_CODEGEN_URL=jdbc:postgresql://localhost:5432/postgres \
    JOOQ_CODEGEN_USER=postgres \
    JOOQ_CODEGEN_PASSWORD=postgres \
    gradle bootJar --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd --system --uid 1001 appuser
COPY --from=build /workspace/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
