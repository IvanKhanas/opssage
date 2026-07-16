# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew \
 && ./gradlew --no-daemon --no-configuration-cache \
    :knowledge-mcp-server:bootJar -x test \
 && cp "$(ls knowledge-mcp-server/build/libs/*.jar | grep -v -- '-plain.jar')" \
    app.jar

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
RUN groupadd --system opssage \
 && useradd --system --gid opssage --home /app opssage
COPY --from=build /workspace/app.jar app.jar
USER opssage
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
