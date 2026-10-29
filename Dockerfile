# syntax=docker/dockerfile:1

# ==========================================
# Stage 1: Build Application
# ==========================================
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Maven Wrapper and POM first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings from Windows checkouts (CRLF -> LF) and set execution permissions
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Pre-fetch project dependencies offline
RUN ./mvnw dependency:go-offline -B || true

# Copy application source code
COPY src/ ./src/

# Package executable Spring Boot JAR (skipping test execution during image build)
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# Stage 2: Production Runtime
# ==========================================
FROM eclipse-temurin:21-jre-jammy AS runner

WORKDIR /app

# Create a non-privileged system user and group for running the container
RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --ingroup appgroup --no-create-home appuser

# Create persistent storage directory for uploaded product images with appropriate ownership
RUN mkdir -p /app/data/product-images && \
    chown -R appuser:appgroup /app

# Copy packaged JAR from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

# Configure default runtime environment variables
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    APP_PRODUCT_IMAGES_DIRECTORY=/app/data/product-images

USER appuser

EXPOSE 8080

# Tuned for Render Free Tier (512 MB RAM limit):
# -Xmx256m caps heap at 256 MB
# -Xss512k halves thread stack footprint
# -XX:MaxMetaspaceSize=128m caps Metaspace
# -XX:+ExitOnOutOfMemoryError ensures prompt restart instead of hanging
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:--Xmx256m -Xss512k -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError} -Dserver.port=${PORT:-8080} -Dserver.address=0.0.0.0 -Dspring.profiles.active=prod -jar app.jar"]
