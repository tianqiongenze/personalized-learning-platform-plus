# -*- coding: utf-8 -*-
"""集群内 Locust 分布式压测脚本(worker 侧)
直连业务 ClusterIP; 权重与 k6 一致: login10 / tenant30 / course30 / graphql15 / health15
"""
import random

from locust import HttpUser, constant, task

TID = "ca0da7c6-7cda-44ac-bfcd-da2696f9b851"
USER_POOL = [{"username": "loaduser%d" % i, "password": "Load@12345"} for i in range(1, 61)]


class MixedUser(HttpUser):
    wait_time = constant(0)

    @task(10)
    def login(self):
        u = random.choice(USER_POOL)
        with self.client.post("http://iam-service:8082/api/v1/auth/login", json=u,
                              headers={"X-Tenant-Id": TID}, name="iam_login",
                              catch_response=True) as r:
            if r.status_code != 200:
                r.failure("status=%d" % r.status_code)

    @task(30)
    def tenant_list(self):
        page = random.randint(0, 3)
        with self.client.get("http://tenant-service:8081/api/v1/tenants?page=%d&size=20" % page,
                             headers={"X-Tenant-Id": TID}, name="tenant_list",
                             catch_response=True) as r:
            if r.status_code != 200:
                r.failure("status=%d" % r.status_code)

    @task(30)
    def course_list(self):
        with self.client.get("http://course-service:8083/api/v1/courses?page=0&size=20",
                             headers={"X-Tenant-Id": TID}, name="course_list",
                             catch_response=True) as r:
            if r.status_code != 200:
                r.failure("status=%d" % r.status_code)

    @task(15)
    def graphql(self):
        payload = {"query": "{ courses(page:0,size:20) { content { id name status } totalElements } }"}
        with self.client.post("http://graphql-gateway:8097/graphql", json=payload,
                              headers={"X-Tenant-Id": TID}, name="graphql_gateway",
                              catch_response=True) as r:
            if r.status_code != 200:
                r.failure("status=%d" % r.status_code)

    @task(15)
    def health(self):
        self.client.get("http://tenant-service:8081/api/v1/actuator/health", name="health")
