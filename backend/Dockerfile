FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml maven-settings.xml ./
RUN mvn -s maven-settings.xml -B dependency:go-offline
COPY src ./src
RUN mvn -s maven-settings.xml -B package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S calsnap && adduser -S calsnap -G calsnap
WORKDIR /app
COPY --from=build --chown=calsnap:calsnap /build/target/calsnap-1.0.0.jar /app/app.jar
RUN mkdir -p /app/.local/photos && chown -R calsnap:calsnap /app
USER calsnap
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-Djava.awt.headless=true","-jar","/app/app.jar"]
