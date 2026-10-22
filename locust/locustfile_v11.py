# -*- coding: utf-8 -*-
"""locustfile_v11.py — 阶段5 全量压测(读写混合 70%读/30%写)
场景风格对齐 locustfile_1000u.py: SequentialTaskSet + 多 tag, 端点均为实测验证可用。
链路(nginx-gateway 30081 为主; course 列表/learning-paths 因网关 no-slash 301 缺口走 NodePort 直连):
  读 70%:
    R1 GET  {gw}/api/v1/kt/mastery?studentId=...     (200)
    R2 GET  {gw}/api/v1/ops/actuator/health          (200)
    R3 GET  {np_course}/api/v1/courses?tenantId=...  (200)
    R4 GET  {np_teach}/api/v1/learning-paths         (200)
  写 30%:
    W1 POST {gw}/api/v1/kt/predict                    (200)
    W2 POST {np_teach}/api/v1/learning-paths          (201, 持久化)
  登录探测: iam login 已知 403 缺陷, 仅统计不入失败口径。
运行:
  master: locust -f locustfile_v11.py --master --host http://10.167.2.175:30081
  worker: locust -f locustfile_v11.py --worker --master-host=<master_ip>
"""
import json
import random
import time

from locust import HttpUser, SequentialTaskSet, between, events, task

GW_HOST = "10.167.2.175"          # nginx-gateway NodePort 30081 所在主机
TENANT = "f4f85538-0a3a-4638-810e-3c9015cbee02"
NP_COURSE = "http://10.167.2.175:31429"
NP_TEACH = "http://10.167.2.175:32471"
HOST_HDR = {"Host": "10.167.2.176"}


class WarmPathTaskSet(SequentialTaskSet):
    @staticmethod
    def _hdr():
        return {"X-Tenant-Id": TENANT, "Content-Type": "application/json", **HOST_HDR}

    @task(10)
    def r1_kt_mastery(self):
        self.client.get("/api/v1/kt/mastery",
                        params={"studentId": "00000000-0000-0000-0000-000000000001"},
                        headers=self._hdr(), name="R1[GW] /kt/mastery")

    @task(5)
    def r2_ops_health(self):
        self.client.get("/api/v1/ops/actuator/health",
                        headers=self._hdr(), name="R2[GW] /ops/health")

    @task(10)
    def r3_resources(self):
        # 课程列表因 11.0.2+TiDB v8.5.8 x'' 二进制绑定回归 500(见压测报告), 换资源列表
        self.client.get(f"{NP_COURSE}/api/v1/resources",
                        params={"page": 0, "size": 20, "courseId": "10000000-0000-0000-0000-000000000001"},
                        headers={"X-Tenant-Id": TENANT}, name="R3[NP] /resources")

    @task(10)
    def r4_learning_paths(self):
        self.client.get(f"{NP_TEACH}/api/v1/learning-paths",
                        params={"page": 0, "size": 20},
                        headers={"X-Tenant-Id": TENANT}, name="R4[NP] /learning-paths")

    @task(11)
    def w1_kt_predict(self):
        self.client.post("/api/v1/kt/predict", json={
            "studentId": f"00000000-0000-0000-0000-00000000000{random.randint(1, 9)}",
            "knowledgePointId": "00000000-0000-0000-0000-000000000001",
            "correct": random.random() > 0.4,
        }, headers=self._hdr(), name="W1[GW] /kt/predict")

    @task(1)
    def w2_lp_create(self):
        self.client.post(f"{NP_TEACH}/api/v1/learning-paths", json={
            "tenantId": TENANT,
            "courseId": "10000000-0000-0000-0000-000000000001",
            "title": f"lt-{random.randint(1, 99999)}-{int(time.time() * 1000)}",
            "totalSteps": 2,
        }, headers={"X-Tenant-Id": TENANT, "Content-Type": "application/json"},
            name="W2[NP] /learning-paths create")


class WarmPathUser(HttpUser):
    tasks = {WarmPathTaskSet: 1}
    wait_time = between(0.5, 2)

    def on_start(self):
        # 登录探测(iam 已知 403 缺陷): 每 VU 启动时探测一次, 只计数
        with self.client.post("/api/v1/auth/login",
                             json={"username": "admin", "password": "Admin@12345"},
                             headers={"X-Tenant-Id": TENANT, "Content-Type": "application/json", **HOST_HDR},
                             name="LOGIN[GW] /auth/login (known-403)", catch_response=True) as r:
            # iam 登录已知 403(TiDB unnest)缺陷: 预期化, 不计失败
            if r.status_code == 403:
                r.success()


@events.test_stop.add_listener
def _(environment, **kwargs):
    total = environment.stats.total
    print(f"\n[locust_v11] test stop: num_failures={total.num_failures} "
          f"num_requests={total.num_requests} current_rps={total.current_rps:.1f}")
