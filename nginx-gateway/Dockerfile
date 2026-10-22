FROM 10.167.2.175:30500/base/nginx:alpine
RUN apk add --no-cache curl
COPY gateway.conf /etc/nginx/nginx.conf
COPY ssl/ /etc/nginx/ssl/
COPY conf.d/ /etc/nginx/conf.d/
# CSV 铁律: 配置语法实证 —— resolver 指向集群 kube-dns, 构建期(无集群 DNS)无法解析,
# 语法验证放到容器启动 entrypoint(集群内 DNS 可达, nginx -t 失败即拒绝启动)
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
RUN addgroup -g 1000 -S nginxgroup && adduser -u 1000 -S nginxuser -G nginxgroup
RUN chown -R nginxuser:nginxgroup /var/cache/nginx /run /var/log/nginx /etc/nginx /etc/nginx/ssl
USER nginxuser
EXPOSE 80 443
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 CMD curl -f http://localhost/health || exit 1
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
