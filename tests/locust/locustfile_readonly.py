# -*- coding: utf-8 -*-
"""只读压测变体(排除 course CSRF 干扰, 定位系统真实容量):
  - 45% tenant 分页读
  - 45% iam 登录(BCrypt)
  - 10% health
"""
import random
from locust import HttpUser, constant, task

USER_POOL = [{"username": "loaduser%d" % i, "password": "Load@12345"} for i in range(1, 61)]

BASE = "http://10.167.2.175"
TENANT = "ca0da7c6-7cda-44ac-bfcd-da2696f9b851"
LOGIN_BODY = {"username": "admin", "password": "Admin@12345"}


class ReadonlyUser(HttpUser):
    wait_time = constant(0)

    @task(45)
    def tenant_list(self):
        self.client.get(f"{BASE}:31965/api/v1/tenants?page=%d&size=20" % random.randint(0, 3),
                        headers={"X-Tenant-Id": TENANT}, name="tenant_list")

    @task(45)
    def iam_login(self):
        u = random.choice(USER_POOL)  # 多用户池: 避免单行写放大触发乐观锁冲突
        self.client.post(f"{BASE}:31826/api/v1/auth/login", json=u,
                         headers={"X-Tenant-Id": TENANT}, name="iam_login")

    @task(10)
    def health(self):
        self.client.get(f"{BASE}:31965/api/v1/actuator/health", name="health")
