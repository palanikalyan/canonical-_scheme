FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy entire repo
COPY . .

# Build Central-Event-Publisher-central first
COPY Central-Event-Publisher-central ./Central-Event-Publisher-central
RUN mvn -f Central-Event-Publisher-central/pom.xml clean install -U

# Then build Canonical microservice
COPY Canonical_Validation_MicroService ./Canonical_Validation_MicroService
RUN mvn -f Canonical_Validation_MicroService/pom.xml clean package -DskipTests

# Production stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /app/Canonical_Validation_MicroService/target/*.jar app.jar

# Install curl and wget for health checks
RUN apk add --no-cache curl wget

# Create non-root user for security
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

# Set working directory
WORKDIR /app

# Create temp directory for file processing
RUN mkdir -p ./temp && chown -R appuser:appuser ./temp

# Copy JAR file from builder stage
COPY --from=builder /app/Canonical_Validation_MicroService/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8086

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8086/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]