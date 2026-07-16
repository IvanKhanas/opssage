# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS builder
RUN apk add --no-cache gcompat
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :sre-mcp-server:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /workspace/sre-mcp-server/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
