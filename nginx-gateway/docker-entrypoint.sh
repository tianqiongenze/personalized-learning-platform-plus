#!/bin/sh
# nginx-gateway entrypoint: 启动前实证配置语法(CSV 铁律)
# 构建期无法跑 nginx -t(resolver 指向集群内 kube-dns, 构建环境无集群 DNS),
# 放到运行时 entrypoint: nginx -t 失败即拒绝启动, 防止带病配置进入生产。
set -e
nginx -t
exec nginx -g 'daemon off;'
