# Use an official Maven image to build the application
FROM maven:3.9.0-eclipse-temurin-17-alpine AS build
LABEL maintainer="VS_Praktikum"

# Set the working directory
WORKDIR /app

# Copy your pom.xml and the rest of your source code
COPY pom.xml .
COPY src ./src

# Package your app using Maven to create the JAR file
RUN mvn clean package -DskipTests

# Use an OpenJDK runtime to run the application
FROM openjdk:17.0.1-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the built jar file from the Maven build container
COPY --from=build /app/target/star-0.0.1.jar /app/star.jar

# Set up entry point for Java application
ENTRYPOINT ["java", "-jar", "/app/star.jar"]
