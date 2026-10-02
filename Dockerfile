# syntax=docker/dockerfile:1.7
# Shared, hardened image build. SERVICE_PATH must identify one runnable Maven module.
FROM maven:3.9-eclipse-temurin-17 AS build
ARG SERVICE_PATH
WORKDIR /workspace
COPY pom.xml ./
COPY libs ./libs
COPY platform ./platform
COPY services ./services
COPY apps ./apps
RUN test -n "$SERVICE_PATH" \
    && mvn -B -q -pl "$SERVICE_PATH" -am -DskipTests package \
    && JAR="$(find "$SERVICE_PATH/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print -quit)" \
    && test -n "$JAR" \
    && cp "$JAR" /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy AS runtime
ARG OCI_SOURCE="https://github.com/hatrongvu13/smartfarm-platform-maven-starter"
ARG OCI_REVISION="unknown"
LABEL org.opencontainers.image.source="$OCI_SOURCE" \
      org.opencontainers.image.revision="$OCI_REVISION" \
      org.opencontainers.image.vendor="SmartFarm"
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1001 smartfarm \
    && useradd --system --uid 1001 --gid smartfarm --home-dir /app --shell /usr/sbin/nologin smartfarm \
    && mkdir -p /app /tmp/smartfarm /var/log/smartfarm \
    && chown -R smartfarm:smartfarm /app /tmp/smartfarm /var/log/smartfarm
WORKDIR /app
COPY --from=build --chown=smartfarm:smartfarm /workspace/app.jar /app/app.jar
USER 1001:1001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/smartfarm -Djava.io.tmpdir=/tmp/smartfarm" \
    SERVER_PORT=8080
EXPOSE 8080 8081 8083 8084 8085 8086 8092 9091 9093 9094 9095 9096
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail --silent --show-error "http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness" >/dev/null || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
