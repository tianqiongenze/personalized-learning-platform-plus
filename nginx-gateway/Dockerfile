# Dockerfile for nginx-gateway
# Uses nginx:alpine as base image

FROM 10.167.2.175:30500/base/nginx:alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Copy nginx configuration
COPY gateway.conf /etc/nginx/nginx.conf

# Copy SSL certificates (if they exist)
COPY ssl/ /etc/nginx/ssl/

# Copy additional conf.d files (if they exist)
COPY conf.d/ /etc/nginx/conf.d/

# Create non-root user
RUN addgroup -g 1000 -S nginxgroup && \
    adduser -u 1000 -S nginxuser -G nginxgroup

# Change ownership of nginx directories
RUN chown -R nginxuser:nginxgroup /var/cache/nginx /var/run /var/log/nginx /etc/nginx

# Switch to non-root user
USER nginxuser

# Expose ports
EXPOSE 80 443

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost/health || exit 1

# Run nginx
CMD ["nginx", "-g", "daemon off;"]