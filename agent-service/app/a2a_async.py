"""P4 续: A2A 异步任务执行(Pulsar 持久化队列 + 世界模型选型钩子)

P4 补全(此前 execute_task 为同步执行):
  1) async: POST /a2a/v1/tasks?async=true → 任务入 Pulsar 队列, 立即返回 queued
     worker 协程消费执行, 完成/失败后 push notification 回调
  2) 智能路由: recommend_next 技能自动接入学习世界模型(what-if 增益最大知识点)
  3) 无 Pulsar 时降级为进程内 asyncio 队列(fail-soft, 与历史行为兼容)
"""
from __future__ import annotations

import asyncio
import json
import os
import time

from app import a2a

_worker_started = False
_pulsar_producer = None
_local_queue: asyncio.Queue | None = None
TOPIC = os.getenv("A2A_TASK_TOPIC", "persistent://public/default/a2a-tasks")


def _pulsar_available() -> bool:
    return bool(os.getenv("A2A_PULSAR_ENABLED", ""))


async def enqueue(task: dict) -> dict:
    """任务入队(Pulsar 优先, 本地队列降级)"""
    global _local_queue
    if _pulsar_available():
        try:
            import pulsar  # noqa: PLC0415
            global _pulsar_producer
            if _pulsar_producer is None:
                from app import config
                client = pulsar.Client(config.PULSAR_SERVICE_URL)
                _pulsar_producer = client.create_producer(TOPIC)
            _pulsar_producer.send(json.dumps(task, ensure_ascii=False).encode())
            return {"queued": True, "backend": "pulsar", "taskId": task["id"]}
        except Exception as e:  # noqa: BLE001
            a2a._TASKS[task["id"]]["status"] = {
                "state": "degraded-to-local", "reason": str(e)[:80]}
    if _local_queue is None:
        _local_queue = asyncio.Queue(maxsize=500)
    await _local_queue.put(task)
    start_worker()
    return {"queued": True, "backend": "local", "taskId": task["id"]}


def _execute_recommend_next(text: str) -> dict:
    """recommend_next 技能 → 学习世界模型动作选择(内部能力接线)"""
    from app import world_model as wm
    req = {}
    try:
        req = json.loads(text) if text.strip().startswith("{") else {}
    except Exception:
        req = {}
    masteries = req.get("masteries") or {}
    candidates = req.get("candidates") or []
    if not candidates:
        return {"name": "reply.txt", "mimeType": "text/plain",
                "content": "需要 JSON: {masteries:{kp:值}, candidates:[kp], prereqMap?}"}
    st = wm.init_state(masteries, req.get("learningStyle", "unknown"))
    r = wm.recommend_next(st, candidates, req.get("prereqMap"))
    return {"name": "recommendation.json", "mimeType": "application/json",
            "content": json.dumps(r, ensure_ascii=False)}


async def run_worker() -> None:
    """单 worker 协程: 消费任务 → 执行 → 回调。Pulsar 模式由独立 consumer 驱动(生产)。"""
    global _local_queue
    while True:
        task = await _local_queue.get()
        try:
            skill = task.get("skillId", "")
            if skill == "recommend_next":
                text = "".join(p.get("text", "") for p in
                               task.get("message", {}).get("parts", [])
                               if isinstance(p, dict))
                artifact = _execute_recommend_next(text)
                done = a2a.complete_task(task["id"], artifact) or task
            else:
                # 其余技能复用同步执行路径(线程池避免阻塞事件循环)
                done = await asyncio.to_thread(a2a.execute_task, task)
            await asyncio.to_thread(a2a.push_notification, done)
        except Exception as e:  # noqa: BLE001
            t = a2a.get_task(task.get("id", ""))
            if t:
                t["status"] = {"state": "failed", "reason": str(e)[:120],
                               "timestamp": int(time.time() * 1000)}
        finally:
            _local_queue.task_done()


def start_worker() -> None:
    global _worker_started
    if _worker_started or _local_queue is None:
        return
    try:
        loop = asyncio.get_event_loop()
        loop.create_task(run_worker())
        _worker_started = True
    except RuntimeError:
        pass
