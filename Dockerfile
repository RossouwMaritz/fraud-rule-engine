# Pinned by digest (the multi-platform index of 25.0.5_8, 2026-10-29), so a rebuild gets the same base. Update both
# digests together, on purpose, for a new JDK or security fixes.
FROM eclipse-temurin:25-jdk@sha256:884a2f8692cc1e2c7e21f95914adf0ae0eafee9a7eb663a12169cc44468256ec AS build
WORKDIR /build
# Dependencies first, so a code change reuses this layer.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests \
    && mv target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre@sha256:945247f9d092703e0c6be92243ef3c51640229e23ab34d353c8dcdc73e6cd4e6
# No curl or other extra tools: the orchestrator runs the health checks (compose, or Kubernetes probes).
# A fixed numeric UID, so Kubernetes' runAsNonRoot can check it without looking up a user name.
RUN useradd --system --uid 10001 --no-create-home app
USER 10001
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
ENTRYPOINT ["java", "-jar", "app.jar"]
