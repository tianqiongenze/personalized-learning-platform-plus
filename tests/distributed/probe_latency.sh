#!/bin/sh
# 集群内端点延迟直测(压测期间执行, 区分客户端/服务端瓶颈)
TID=ca0da7c6-7cda-44ac-bfcd-da2696f9b851
echo -n "tenant:  "
for i in 1 2 3; do curl -s -o /dev/null -w "%{time_total}s " -m 10 -H "X-Tenant-Id: $TID" "http://10.109.66.156:8081/api/v1/tenants?page=0&size=20"; done
echo
echo -n "course:  "
for i in 1 2 3; do curl -s -o /dev/null -w "%{time_total}s " -m 10 -H "X-Tenant-Id: $TID" "http://10.102.175.199:8083/api/v1/courses?page=0&size=20"; done
echo
echo -n "login:   "
for i in 1 2 3; do curl -s -o /dev/null -w "%{time_total}s " -m 15 -X POST "http://10.102.114.74:8082/api/v1/auth/login" -H "Content-Type: application/json" -H "X-Tenant-Id: $TID" -d '{"username":"loaduser5","password":"Load@12345"}'; done
echo
echo -n "graphql: "
for i in 1 2 3; do curl -s -o /dev/null -w "%{time_total}s " -m 10 -X POST "http://10.109.175.248:8097/graphql" -H "Content-Type: application/json" -H "X-Tenant-Id: $TID" -d '{"query":"{ courses(page:0,size:20) { totalElements } }"}'; done
echo
