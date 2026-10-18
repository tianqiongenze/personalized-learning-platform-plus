# -*- coding: utf-8 -*-
"""全链路混合场景压测(Locust, 集群真实流量):
  - 40% tenant 分页读   (CRDB + 二级缓存)
  - 30% IAM 登录        (BCrypt + JWT 签发, 密码学热点)
  - 20% course 列表读    (native SQL + 缓存)
  - 10% course 健康探测
用户数/速率由 CLI 指定。统计输出 CSV + HTML。
"""
import json
import os
import random

from locust import HttpUser, constant, task, between

BASE = "http://10.167.2.175"
TENANT = "f4f85538-0a3a-4638-810e-3c9015cbee02"
TENANT_UUID_POOL = [
    "f4f85538-0a3a-4638-810e-3c9015cbee02",
]
KP_POOL = None  # 从热键清单抽取的 student id(可选)

LOGIN_BODY = {"username": "admin", "password": "Admin@12345"}


class MixedChainUser(HttpUser):
    # 无等待时间(极限施压); 如需真实节律改 between(0.5, 1.5)
    wait_time = constant(0)

    def on_start(self):
        self.token = None

    @task(40)
    def tenant_list(self):
        page = random.randint(0, 3)
        with self.client.get(
                f"{BASE}:31965/api/v1/tenants?page=%d&size=20" % page,
                headers={"X-Tenant-Id": TENANT},
                name="tenant_list(crdb+cache)",
                catch_response=True) as r:
            if r.status_code != 200:
                r.failure("status=%d" % r.status_code)

    @task(30)
    def iam_login(self):
        with self.client.post(
                f"{BASE}:31826/api/v1/auth/login",
                json=LOGIN_BODY,
                headers={"X-Tenant-Id": TENANT},
                name="iam_login(bcrypt+jwt)",
                catch_response=True) as r:
            if r.status_code == 200:
                try:
                    d = r.json()
                    if not d.get("success") or not d.get("data", {}).get("accessToken"):
                        r.failure("no_token")
                except Exception:
                    r.failure("bad_json")
            else:
                r.failure("status=%d" % r.status_code)

    @task(20)
    def course_list(self):
        with self.client.get(
                f"{BASE}:31429/api/v1/courses?page=0&size=20",
                headers={"X-Tenant-Id": TENANT},
                name="course_list(native_sql)",
                catch_response=True) as r:
            if r.status_code != 200:
                r.failure("status=%d" % r.status_code)

    @task(10)
    def health(self):
        with self.client.get(f"{BASE}:31965/api/v1/actuator/health",
                             name="health", catch_response=True) as r:
            if r.status_code != 200:
                r.failure("status=%d" % r.status_code)
