# Multi-stage build shared by every SmartFarm service/app.
# Build ONE service image:  docker build --build-arg SERVICE_PATH=services/smartfarm-order-service -t smartfarm-order .
# SERVICE_PATH is the Maven module dir; its target/*.jar (Spring Boot fat jar) is the runtime artifact.

# ---- build stage: full reactor so internal module deps resolve ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY libs ./libs
COPY platform ./platform
COPY services ./services
COPY apps ./apps
# Offline-friendly: resolve then package. -DskipTests keeps image builds fast; CI runs tests separately.
RUN mvn -B -q -DskipTests install

# ---- runtime stage: just the one service's jar on a slim JRE ----
FROM eclipse-temurin:17-jre AS runtime
ARG SERVICE_PATH
WORKDIR /app
# Copy the single Spring Boot jar (exclude the *.jar.original produced by the repackage plugin).
COPY --from=build /workspace/${SERVICE_PATH}/target/*.jar /app/app.jar
# Non-root for safety.
RUN useradd -r -u 1001 smartfarm && chown -R smartfarm /app
USER smartfarm
ENV JAVA_OPTS=""
EXPOSE 8080 8081 8083 8084 8085 8086 8092 9091 9093 9094 9095 9096
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
