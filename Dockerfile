# Stage 1: Build Spring Boot application and bundle static frontend
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy Maven configuration
COPY backend/pom.xml backend/pom.xml

# Copy backend source code
COPY backend/src backend/src

# Bundle frontend assets directly into Spring Boot static classpath
COPY PetAdoptionSystem backend/src/main/resources/static

# Package executable JAR
WORKDIR /app/backend
RUN mvn clean package -DskipTests

# Stage 2: High-performance, lightweight JRE 21 Alpine runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create unprivileged application user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy artifact from build stage
COPY --from=build /app/backend/target/*.jar app.jar

# Default internal container port
EXPOSE 8080

# Tuned for Render 512MB RAM free tier: 384MB heap max with G1 garbage collector
ENTRYPOINT ["java", "-Xmx384m", "-Xms128m", "-XX:+UseG1GC", "-jar", "app.jar"]
