# 运营 Web 端: nginx 静态托管 + 反代 ops-service
FROM 10.167.2.175:30500/base/nginx:1.27-alpine
COPY deploy-nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/
