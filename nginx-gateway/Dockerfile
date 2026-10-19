FROM 10.167.2.175:30500/base/nginx:alpine
RUN apk add --no-cache curl
COPY gateway.conf /etc/nginx/nginx.conf
COPY ssl/ /etc/nginx/ssl/
COPY conf.d/ /etc/nginx/conf.d/
RUN addgroup -g 1000 -S nginxgroup && adduser -u 1000 -S nginxuser -G nginxgroup
RUN chown -R nginxuser:nginxgroup /var/cache/nginx /run /var/log/nginx /etc/nginx /etc/nginx/ssl
USER nginxuser
EXPOSE 80 443
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 CMD curl -f http://localhost/health || exit 1
CMD ["nginx", "-g", "daemon off;"]
