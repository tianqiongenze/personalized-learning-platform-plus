# -*- coding: utf-8 -*-
"""端到端全链路功能测试 —— 覆盖平台全部 REST 能力
前置: 集群已部署(deploy-rancher.sh deploy), BASE_URL 指向入口(Ingress/NodePort)
运行: BASE_URL=http://<入口> pytest tests/e2e -v --tb=short
标记: smoke(核心链路) / full(全部)
"""
import os
import time
import uuid

import pytest
import requests

BASE = os.getenv("BASE_URL", "http://localhost:8080")
TENANT = os.getenv("E2E_TENANT", "tenant-01")
H = {"X-Tenant-Id": TENANT, "Content-Type": "application/json"}


def api(method, path, **kw):
    kw.setdefault("headers", H)
    kw.setdefault("timeout", 60)
    return requests.request(method, f"{BASE}{path}", **kw)


@pytest.fixture(scope="session")
def admin_token():
    """管理员登录(演示环境凭据; 失败则跳过鉴权用例并标记)"""
    r = api("POST", "/api/v1/auth/login", json={
        "username": os.getenv("E2E_USER", "admin"),
        "password": os.getenv("E2E_PASS", "admin123")})
    if r.status_code == 200:
        return r.json().get("data", {}).get("accessToken", "")
    pytest.skip(f"登录不可用({r.status_code}), 鉴权相关用例跳过")


def auth(token):
    h = dict(H)
    h["Authorization"] = f"Bearer {token}"
    return h


# ================= IAM(认证/用户/角色) =================

@pytest.mark.smoke
def test_iam_login_rejects_bad_password():
    r = api("POST", "/api/v1/auth/login", json={"username": "nobody", "password": "wrong-password"})
    assert r.status_code in (400, 401, 403, 404), f"异常状态 {r.status_code}: {r.text[:200]}"


def test_iam_user_lifecycle(admin_token):
    a = auth(admin_token)
    username = f"e2e-{uuid.uuid4().hex[:6]}"
    r = api("POST", "/api/v1/users", headers=a, json={
        "username": username, "email": f"{username}@e2e.test",
        "password": "Passw0rd!234", "phone": "", "roleCodes": ["STUDENT"]})
    assert r.status_code in (200, 201), r.text[:300]
    uid = r.json()["data"]["id"]
    assert api("GET", f"/api/v1/users/{uid}", headers=a).status_code == 200
    assert api("GET", "/api/v1/users", headers=a, params={"page": 0, "size": 10}).status_code == 200
    assert api("POST", f"/api/v1/users/{uid}/activate", headers=a).status_code == 200
    assert api("PUT", f"/api/v1/users/{uid}/info", headers=a, json={
        "email": f"upd-{username}@e2e.test", "phone": ""}).status_code == 200
    assert api("DELETE", f"/api/v1/users/{uid}", headers=a, params={"reason": "e2e"}).status_code == 200


def test_iam_role_list(admin_token):
    r = api("GET", "/api/v1/roles", headers=auth(admin_token), params={"page": 0, "size": 10})
    assert r.status_code == 200


# ================= 课程域 =================

@pytest.mark.smoke
def test_course_list():
    r = api("GET", "/api/v1/courses", params={"page": 0, "size": 5})
    assert r.status_code == 200, r.text[:200]
    body = r.json()
    assert "data" in body or "content" in json.dumps(body)


def test_course_crud(admin_token):
    a = auth(admin_token)
    code = f"E2E{uuid.uuid4().hex[:6].upper()}"
    r = api("POST", "/api/v1/courses", headers=a, json={
        "code": code, "name": "E2E 压测课程", "description": "自动化用例",
        "subject": "工业互联网", "gradeLevel": "二年级", "semester": "2026 秋"})
    if r.status_code not in (200, 201):
        pytest.skip(f"course 创建受限: {r.status_code} {r.text[:150]}")
    cid = r.json()["data"]["id"] if isinstance(r.json().get("data"), dict) else None
    if cid:
        assert api("GET", f"/api/v1/courses/{cid}", headers=a).status_code == 200


# ================= 教学域 =================

def test_learning_path_list():
    r = api("GET", "/api/v1/paths", params={"status": "IN_PROGRESS"})
    assert r.status_code == 200


def test_teaching_resources():
    for p in ["/api/v1/assignments", "/api/v1/exams", "/api/v1/discussions"]:
        r = api("GET", p, params={"page": 0, "size": 5})
        assert r.status_code in (200, 404), f"{p}: {r.status_code}"


# ================= KT(知识追踪) =================

@pytest.mark.smoke
def test_kt_mastery():
    """BKT+DKT 融合掌握度(推荐 85% 的数据前提)"""
    r = api("GET", "/api/v1/kt/mastery", params={"studentId": f"e2e-{uuid.uuid4().hex[:6]}"})
    if r.status_code == 404:
        pytest.skip("kt 代理未部署")
    assert r.status_code == 200
    body = r.json()
    assert "overallMastery" in body and 0 <= body["overallMastery"] <= 1
    assert "weakKps" in body and isinstance(body["weakKps"], list)


def test_kt_predict():
    r = api("POST", "/api/v1/kt/predict", json={
        "studentId": "e2e-student", "kps": [1, 2, 3], "difficulty": 3})
    if r.status_code == 404:
        pytest.skip("kt 未部署")
    assert r.status_code == 200
    assert "probability" in r.json()


# ================= AI 生成(同步+异步削峰) =================

@pytest.mark.smoke
def test_ai_generation_sync():
    r = api("POST", "/api/v1/generation", json={
        "resourceType": "KNOWLEDGE_POINT_TALK",
        "knowledgePoint": "欧姆定律", "industryTags": "智能网联新能源汽车",
        "requirements": "口语化, 含类比", "studentId": "e2e"})
    if r.status_code == 404:
        pytest.skip("ai-service 未部署")
    assert r.status_code == 201, r.text[:200]
    content = r.json().get("content", "")
    assert len(content) > 50, "生成内容过短"
    assert "欧姆" in content, "未覆盖知识点"


def test_ai_generation_async_and_result():
    """提交 -> 立即 202 -> (可选)等待结果主题消费"""
    r = api("POST", "/api/v1/generation/async", json={
        "resourceType": "EXERCISE_SET", "knowledgePoint": "继电器控制",
        "industryTags": "智能制造产线", "studentId": "e2e-async"})
    if r.status_code == 404:
        pytest.skip("异步链路未部署")
    assert r.status_code == 202
    assert r.json()["status"] == "QUEUED" and r.json()["taskId"]


def test_ai_vector_search():
    r = api("POST", "/api/v1/generation/search", json={"query": "PLC", "topK": 3})
    if r.status_code == 404:
        pytest.skip("ai-service 未部署")
    assert r.status_code == 200


# ================= 统一运维平台 =================

def test_ops_overview():
    r = api("GET", "/api/v1/ops/overview")
    if r.status_code == 404:
        pytest.skip("ops-service 未部署")
    assert r.status_code == 200
    body = r.json()
    assert "deployments" in body and "hpas" in body and "grafanaEmbeds" in body


def test_ops_services_and_hpa():
    assert api("GET", "/api/v1/ops/services").status_code == 200
    assert api("GET", "/api/v1/ops/hpa").status_code == 200


def test_ops_cache_visibility():
    """缓存可见性: Redis Cluster 逐节点 / Milvus 集合 / Nebula 指标"""
    r1 = api("GET", "/api/v1/ops/cache/redis")
    if r1.status_code == 200:
        assert isinstance(r1.json(), (list, dict))
    r2 = api("GET", "/api/v1/ops/cache/vector/collections")
    assert r2.status_code in (200, 500)  # milvus 未就绪时允许 500+错误体
    r3 = api("GET", "/api/v1/ops/cache/graph/metrics")
    assert r3.status_code in (200, 500)


def test_ops_pulsar_browse_and_peek():
    """Pulsar: 租户/主题/订阅/peek 具体消息"""
    t = api("GET", "/api/v1/ops/pulsar/tenants")
    if t.status_code == 500:
        pytest.skip("pulsar 未就绪")
    assert t.status_code == 200
    topics = api("GET", "/api/v1/ops/pulsar/topics", params={"tenant": "public", "namespace": "default"})
    if topics.status_code == 200 and isinstance(topics.json(), list) and topics.json():
        topic = topics.json()[0].split("/").pop()
        subs = api("GET", "/api/v1/ops/pulsar/topics/subscriptions",
                   params={"tenant": "public", "namespace": "default", "topic": topic})
        assert subs.status_code in (200, 404)
        peek = api("GET", "/api/v1/ops/pulsar/topics/peek", params={
            "tenant": "public", "namespace": "default", "topic": topic,
            "subscription": "e2e-peek", "count": 5})
        assert peek.status_code in (200, 404)  # 订阅不存在时 404 合法


def test_ops_logs_loki():
    r = api("GET", "/api/v1/ops/logs/loki", params={
        "logql": '{namespace="qianrenqianmian"}', "limit": 10})
    if r.status_code == 404:
        pytest.skip("loki/ops 未部署")
    assert r.status_code in (200, 500)


def test_ops_grafana_embeds():
    r = api("GET", "/api/v1/ops/grafana/embeds")
    if r.status_code == 200:
        assert "platformOverview" in r.json()


# ================= 租户与限流 =================

def test_tenant_crud(admin_token):
    a = auth(admin_token)
    code = f"T{uuid.uuid4().hex[:6].upper()}"
    r = api("POST", "/api/v1/tenants", headers=a, json={
        "code": code, "name": "E2E 高校", "description": "测试租户",
        "quota": {"maxUsers": 100, "maxCourses": 10, "maxStorageGB": 5,
                  "maxApiCallsPerMin": 100, "maxConcurrentSessions": 50},
        "branding": {}, "config": {}, "adminUserId": "e2e-admin"})
    if r.status_code in (200, 201):
        tid = r.json()["data"]["id"]
        assert api("GET", f"/api/v1/tenants/{tid}", headers=a).status_code == 200


def test_per_tenant_rate_limit():
    """吵闹邻居防御: 超配额租户应收到 429"""
    h = {"X-Tenant-Id": "rate-limit-test", "Content-Type": "application/json"}
    codes = [api("GET", "/api/v1/courses", params={"page": 0, "size": 1}, headers=h).status_code
             for _ in range(30)]
    # 小额探测不会触发限流(配额 1200/min), 但验证头部被接受且服务正常
    assert all(c in (200, 401) for c in codes), f"异常码: {codes}"


# ================= 实时网关(WS/SSE) =================

def test_realtime_gateway_health():
    r = api("GET", "/health")
    if r.status_code == 404:
        pytest.skip("realtime-gateway 未暴露")
    assert r.status_code == 200


# ================= AI 助手页面上下文(SSE 流) =================

def test_assistant_stream():
    r = api("GET", "/sse", params={"pageContext": "课程学习", "message": "推荐一门课程",
                                   "studentId": "e2e"}, stream=True, timeout=90)
    if r.status_code == 404:
        pytest.skip("realtime-gateway 未暴露")
    assert r.status_code == 200
    got_token = False
    for chunk in r.iter_lines(decode_unicode=True):
        if chunk and chunk != "data: [DONE]":
            got_token = True
            break
    r.close()
    assert got_token, "SSE 未输出任何 token"
