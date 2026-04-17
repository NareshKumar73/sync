FROM eclipse-temurin:25.0.2_10-jre-alpine
WORKDIR /app

# For offline deployment, we assume the JAR is already built on the host system 
# using 'mvn clean package' (or similar) and exists in the target directory. 
# This avoids downloading maven dependencies during the docker build.
COPY target/sync-0.0.5.jar app.jar

EXPOSE 9005

ENTRYPOINT ["java", "-jar", "app.jar"]
