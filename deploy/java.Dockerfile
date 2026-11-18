FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /build
COPY services/${SERVICE}/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline
COPY services/${SERVICE}/src ./src
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home appuser
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER appuser
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "app.jar"]
