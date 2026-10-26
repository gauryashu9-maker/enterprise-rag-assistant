# Stage 1: Build the application using official Maven + Java 21
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies first so repeated builds are fast
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the executable jar
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal Java 21 runtime environment
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Security best practice: run container as a non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the built jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose Spring Boot default port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]