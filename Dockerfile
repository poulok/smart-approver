FROM maven:3.8-openjdk-11 as build

WORKDIR /app

# Copy POM first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src/ /app/src/

# Build the application
RUN mvn package -DskipTests

# Create runtime image
FROM openjdk:11-jre-slim

WORKDIR /app

# Copy the built JAR file
COPY --from=build /app/target/*.jar app.jar

# Create a directory for GitHub App private key
RUN mkdir -p /app/config
VOLUME /app/config

# Expose the application port
EXPOSE 8080

# Run the application with Spring profiles
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
