FROM eclipse-temurin:25.0.2_10-jre-alpine-3.22
WORKDIR /app

RUN mkdir -p /app/data/tmp

COPY target/sync-0.0.5.jar app.jar

EXPOSE 9005

ENTRYPOINT ["java", "-jar", "app.jar"]
