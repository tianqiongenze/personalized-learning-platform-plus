# Dockerfile for tenant-service
# Multi-stage build for smaller production image

# Build stage (base pinned to cluster registry; Docker Hub unreachable from build host)
FROM 10.167.2.175:30500/base/maven:3.9.11-eclipse-temurin-25 AS builder

WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .
COPY src/main/resources/db/migration ./src/main/resources/db/migration
# Aliyun mirror: central direct is slow/unreliable from build network
COPY .mvn/settings.xml /root/.m2/settings.xml

# Install local platform-common jar (monorepo sibling, not in any remote repo)
COPY .mvn/platform-common.jar /tmp/platform-common.jar
RUN mvn install:install-file -B -Dfile=/tmp/platform-common.jar \
    -DgroupId=com.platform -DartifactId=platform-common \
    -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM 10.167.2.175:30500/base/eclipse-temurin:25-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup

WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"

# Dev-only startup flags (default empty; e.g. JAVA_OPTS_DEV="-XX:TieredStopAtLevel=1 -noverify")
ENV JAVA_OPTS_DEV=""

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS $JAVA_OPTS_DEV -jar app.jar"]