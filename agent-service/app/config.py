"""智能体服务配置(环境变量驱动, 复用集群既有 LLM/向量库/图库)"""
import os

LLM_BASE_URL = os.getenv("LLM_BASE_URL", "http://llm-service.middleware.svc.cluster.local:8000/v1")
LLM_API_KEY = os.getenv("LLM_API_KEY", "EMPTY")
LLM_MODEL = os.getenv("LLM_MODEL", "qwen2.5-14b-instruct")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "bge-m3")

MILVUS_HOST = os.getenv("MILVUS_HOST", "milvus.middleware.svc.cluster.local")
MILVUS_PORT = int(os.getenv("MILVUS_PORT", "19530"))

COURSE_SERVICE_URL = os.getenv("COURSE_SERVICE_URL", "http://course-service.platform-plus:8083/api/v1")
TEACHING_SERVICE_URL = os.getenv("TEACHING_SERVICE_URL", "http://teaching-service.platform-plus:8084/api/v1")

REDIS_URL = os.getenv("REDIS_URL", "redis://:CHANGE_ME_REDIS@redis-cluster-access.middleware.svc.cluster.local:6379/0")

PULSAR_SERVICE_URL = os.getenv("PULSAR_SERVICE_URL", "pulsar://pulsar.middleware.svc.cluster.local:6650")

RERANKER_URL = os.getenv("RERANKER_URL", "http://vector-reranker.middleware:8090")

# MCP 工具调用的域服务基址(集群内 Service DNS)
KT_BASE = os.getenv("KT_BASE", "http://kt-service.qianrenqianmian:8095")
COURSE_BASE = os.getenv("COURSE_BASE", "http://course-service.qianrenqianmian:8083/api/v1")
TENANT_BASE = os.getenv("TENANT_BASE", "http://tenant-service.qianrenqianmian:8081/api/v1")
