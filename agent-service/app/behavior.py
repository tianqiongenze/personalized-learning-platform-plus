"""learning-behavior 事件生产(数据中台①: 行为→画像联动上游)。
Pulsar 客户端惰性单例; 失败 fail-soft(行为记录不因总线故障失败, 与事件总线降级一致)。
"""
from __future__ import annotations

import json
import os
import uuid

_client = None
_producer = None
TOPIC = "persistent://public/default/learning-behavior"


def publish_learning_behavior(student_id: str, skill_id: str, correct: bool,
                              bloom_level: str = "apply", course_id: str | None = None) -> bool:
    try:
        global _client, _producer
        if _producer is None:
            import pulsar
            if _client is None:
                _client = pulsar.Client(os.getenv(
                    "PULSAR_SERVICE_URL", "pulsar://pulsar.infra.svc.cluster.local:6650"),
                    connection_timeout_ms=5000)
            _producer = _client.create_producer(TOPIC)
        _producer.send(json.dumps({
            "eventId": uuid.uuid4().hex,
            "eventType": "LEARNING_BEHAVIOR",
            "sourceService": "agent-service",
            "tenantId": None,
            "aggregateId": f"{student_id}:{skill_id}",
            "payload": {"studentId": student_id, "skillId": skill_id, "correct": bool(correct),
                        "bloomLevel": bloom_level, "courseId": course_id},
        }, ensure_ascii=False).encode("utf-8"))
        return True
    except Exception:
        return False
