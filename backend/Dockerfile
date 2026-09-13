# Stage 1: Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy root gradle files for the build context (assumes build runs from repo root)
COPY gradlew .
RUN chmod +x gradlew
COPY gradle gradle
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle.properties .

# Copy backend module
COPY backend backend

# Build the Shadow JAR
RUN ./gradlew :backend:shadowJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the fat JAR from the build stage
COPY --from=build /app/backend/build/libs/backend-all.jar ./backend-all.jar

# Render assigns a dynamic port via the PORT env var.
# The application is already configured to read this variable.
EXPOSE 8080

# Run the application
# Binds to 0.0.0.0 by default in Application.kt
CMD ["java", "-jar", "backend-all.jar"]
