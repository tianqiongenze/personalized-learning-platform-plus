# locust_plpp.py — PLPP 热路径 Locust 压测(经 nginx-gateway 30081, 与 K6 同场景对齐)
# 用法: locust -f locust_plpp.py --headless -u 200 -r 50 --run-time 3m --host http://10.167.2.175:30081 --csv report/locust-gw
import os
import random
import json as _json
from locust import HttpUser, task, between

GW = os.environ.get("LOCUST_HOST", "http://10.167.2.175:30081")
TENANT = os.environ.get("TENANT_ID", "da98138f-486f-42db-944c-219a5a44bf52")
USER = os.environ.get("K6_USER", "admin")
PASS = os.environ.get("K6_PASS", "Admin@12345")


class PlatformUser(HttpUser):
    wait_time = between(0.1, 0.3)
    host = GW

    def on_start(self):
        preset = os.environ.get("LOCUST_TOKEN", "")
        if preset:
            self.auth = {"Authorization": f"Bearer {preset}", "X-Tenant-Id": TENANT}
            return
        r = self.client.post(
            "/api/v1/auth/login",
            headers={"Content-Type": "application/json", "X-Tenant-Id": TENANT},
            json={"username": USER, "password": PASS},
        )
        token = ""
        for _ in range(5):  # 限流 503 重试
            r = self.client.post(
                "/api/v1/auth/login",
                headers={"Content-Type": "application/json", "X-Tenant-Id": TENANT},
                json={"username": USER, "password": PASS},
            )
            try:
                token = r.json().get("data", {}).get("accessToken", "")
                if token:
                    break
            except Exception:
                pass
        self.auth = {"Authorization": f"Bearer {token}", "X-Tenant-Id": TENANT}
        self.token_ok = bool(token)

    @task(30)
    def kt_mastery(self):
        self.client.get(
            "/api/v1/kt/mastery?studentId=00000000-0000-0000-0000-000000000001",
            headers=self.auth, name="/kt/mastery",
        )

    @task(15)
    def ops_health(self):
        self.client.get("/api/v1/ops/actuator/health", headers=self.auth, name="/ops/health")

    @task(15)
    def resources(self):
        self.client.get(
            "/api/v1/resources?page=0&size=20&courseId=10000000-0000-0000-0000-000000000001",
            headers=self.auth, name="/resources",
        )

    @task(10)
    def learning_paths(self):
        self.client.get("/api/v1/learning-paths?page=0&size=20", headers=self.auth, name="/learning-paths")

    @task(27)
    def kt_predict(self):
        self.client.post(
            "/api/v1/kt/predict",
            headers={**self.auth, "Content-Type": "application/json"},
            json={
                "studentId": f"00000000-0000-0000-0000-00000000000{random.randint(1, 9)}",
                "knowledgePointId": "00000000-0000-0000-0000-000000000001",
                "correct": random.random() > 0.4,
            },
            name="/kt/predict",
        )

    @task(3)
    def lp_create(self):
        self.client.post(
            "/api/v1/learning-paths",
            headers={**self.auth, "Content-Type": "application/json"},
            json={
                "tenantId": TENANT,
                "courseId": "10000000-0000-0000-0000-000000000001",
                "title": f"locust-{id(self)}-{random.randint(0, 10**9)}",
                "totalSteps": 2,
            },
            name="/learning-paths [create]",
        )
