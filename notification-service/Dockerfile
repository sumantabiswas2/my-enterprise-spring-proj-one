# syntax=docker/dockerfile:1
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -q -pl notification-service -am -DskipTests package

# Runtime stage (non-root, lightweight)
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/notification-service/target/*.jar app.jar
USER app
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
