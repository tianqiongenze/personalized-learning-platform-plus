# 学生 Web 端: nginx 托管静态资源 + 反代 gateway/agent(/agent /gateway → 后端服务)
FROM 10.167.2.175:30500/base/nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/
