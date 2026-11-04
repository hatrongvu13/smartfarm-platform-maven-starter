# syntax=docker/dockerfile:1.7
# Shared, hardened image build. SERVICE_PATH must identify one runnable Maven module:
#   platform/api-gateway | services/identity-service | services/farm-service
FROM maven:3.9-eclipse-temurin-17 AS build
ARG SERVICE_PATH
WORKDIR /workspace
COPY pom.xml ./
COPY libs ./libs
COPY platform ./platform
COPY services ./services
RUN test -n "$SERVICE_PATH" \
    && mvn -B -q -pl "$SERVICE_PATH" -am -DskipTests package \
    && JAR="$(find "$SERVICE_PATH/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print -quit)" \
    && test -n "$JAR" \
    && cp "$JAR" /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy AS runtime
ARG OCI_SOURCE="https://github.com/hatrongvu13/farmverse-platform-maven-starter"
ARG OCI_REVISION="unknown"
LABEL org.opencontainers.image.source="$OCI_SOURCE" \
      org.opencontainers.image.revision="$OCI_REVISION" \
      org.opencontainers.image.vendor="FarmVerse"
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1001 farmverse \
    && useradd --system --uid 1001 --gid farmverse --home-dir /app --shell /usr/sbin/nologin farmverse \
    && mkdir -p /app /tmp/farmverse /var/log/farmverse \
    && chown -R farmverse:farmverse /app /tmp/farmverse /var/log/farmverse
WORKDIR /app
COPY --from=build --chown=farmverse:farmverse /workspace/app.jar /app/app.jar
USER 1001:1001
# Mọi service trong container dùng chung hai cổng:
#   SERVER_PORT 8080            cổng ứng dụng (TLS khi bật profile mtls ở identity/farm)
#   MANAGEMENT_SERVER_PORT 9090 actuator, HTTP, loopback — chỉ healthcheck trong container gọi được
# MANAGEMENT_SERVER_PORT ghi đè management.server.port của từng service (9080/9082/9092 khi chạy trên máy).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/farmverse -Djava.io.tmpdir=/tmp/farmverse" \
    SERVER_PORT=8080 \
    MANAGEMENT_SERVER_PORT=9090
# Chỉ cổng ứng dụng. Cổng quản trị bind 127.0.0.1 nên không expose.
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail --silent --show-error "http://127.0.0.1:${MANAGEMENT_SERVER_PORT}/actuator/health/readiness" >/dev/null || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
