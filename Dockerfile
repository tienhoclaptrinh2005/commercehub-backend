FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B --no-transfer-progress -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B --no-transfer-progress -DskipTests package


FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system commercehub \
    && useradd --system --gid commercehub --home-dir /app commercehub

WORKDIR /app

COPY --from=build --chown=commercehub:commercehub \
    /workspace/target/commercehub-backend-*.jar \
    /app/app.jar

USER commercehub

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/readyz || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
