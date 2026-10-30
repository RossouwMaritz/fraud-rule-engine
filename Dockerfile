FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
# Dependencies first, so a code change reuses this layer.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests \
    && mv target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
# The JRE image has no curl or wget for the health check.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --no-create-home app
USER app
WORKDIR /app
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
# Heap scales with the container's memory limit instead of the JVM's default of a quarter of it. Only 40%, because
# under the load test the JVM used ~270MB outside the heap (metaspace, code, GC) and malloc ~190MB more: a 75% heap
# got the container OOM-killed at 768M. The heap itself peaked at ~160MB. Two malloc arenas, not one per CPU,
# cut ~95MB of that malloc overhead. On a full heap the JVM exits, so it's restarted instead of running half broken.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:+ExitOnOutOfMemoryError" MALLOC_ARENA_MAX=2
# 8080 is the API, 8081 health and metrics (MANAGEMENT_SERVER_PORT).
EXPOSE 8080 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD curl -fs http://localhost:${MANAGEMENT_SERVER_PORT:-8081}/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
