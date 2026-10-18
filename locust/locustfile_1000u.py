# -*- coding: utf-8 -*-
"""1000 并发多租户压测(Locust) — 目标真实端点
场景: 6 所高校(租户) × 各 167 并发 ≈ 1000 并发
链路: 登录(iam) -> 课程/资源浏览(course, 打多级缓存) -> 学习路径(teaching)
      -> AI 生成异步提交(ai /generation/async, 削峰验证) -> 运维平台总览(ops)
通过标准: P95 < 800ms(业务读), 429 率 < 1%(配额内租户), 错误率 < 0.5%
运行: locust -f locustfile.py --headless -u 1000 -r 50 -t 30m --host http://<入口IP>
"""
import json
import random
import uuid

from locust import HttpUser, SequentialTaskSet, task, between

TENANTS = [f"tenant-{i:02d}" for i in range(1, 7)]
DEMO_UUID = "00000000-0000-0000-0000-000000000001"


class StudentBehavior(SequentialTaskSet):

    def on_start(self):
        self.tenant = random.choice(TENANTS)
        self.username = f"stu-{uuid.uuid4().hex[:8]}"
        # 登录(iam) — 演示环境允许 mock 登录失败继续压测读路径
        resp = self.client.post("/api/v1/auth/login", json={
            "username": self.username, "password": "Passw0rd!234"},
            headers={"X-Tenant-Id": self.tenant}, name="/auth/login")
        self.token = ""
        if resp.status_code == 200:
            self.token = resp.json().get("data", {}).get("accessToken", "")

    def headers(self):
        h = {"X-Tenant-Id": self.tenant}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    @task(6)
    def browse_courses(self):
        self.client.get("/api/v1/courses", headers=self.headers(),
                        params={"page": 0, "size": 20}, name="/courses 分页[缓存命中路径]")

    @task(4)
    def view_course_detail(self):
        course_id = random.choice(["10000000-0000-0000-0000-00000000000%d" % i for i in range(1, 6)])
        self.client.get(f"/api/v1/courses/{course_id}", headers=self.headers(),
                        name="/courses/{id}[L1+L2缓存]")

    @task(3)
    def learning_path(self):
        self.client.get("/api/v1/paths", headers=self.headers(),
                        params={"status": "IN_PROGRESS"}, name="/paths 学习路径")

    @task(2)
    def ai_generation_async(self):
        """异步生成提交(削峰): 仅验证提交毫秒级返回, 不验证生成完成"""
        self.client.post("/api/v1/generation/async", headers=self.headers(), json={
            "resourceType": random.choice(["EXERCISE_SET", "KNOWLEDGE_POINT_TALK", "TRAINING_TASK"]),
            "knowledgePoint": random.choice(["PLC 梯形图", "伺服电机控制", "传感器融合"]),
            "industryTags": "智能网联新能源汽车",
            "studentId": self.username,
        }, name="/generation/async[提交削峰]")

    @task(1)
    def ops_overview(self):
        self.client.get("/api/v1/ops/overview", headers=self.headers(), name="/ops/overview")


class StudentUser(HttpUser):
    tasks = {StudentBehavior: 1}
    wait_time = between(1, 4)


class TeacherUser(HttpUser):
    """教师端: 高写比例"""
    wait_time = between(2, 6)

    def on_start(self):
        self.tenant = random.choice(TENANTS)

    @task
    def create_resource_index(self):
        self.client.post("/api/v1/generation/index", json={
            "resourceId": uuid.uuid4().hex, "title": "压测资源", "content": "x" * 500},
            headers={"X-Tenant-Id": self.tenant}, name="/generation/index[向量入库]")
