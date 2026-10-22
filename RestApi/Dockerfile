FROM maven:3.9.9-eclipse-temurin-17-alpine AS build

WORKDIR /workspace
# Cache the dependency download separately from the source code.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/placement-analytics-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=75s --retries=5 \
  CMD wget -q -O - http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
