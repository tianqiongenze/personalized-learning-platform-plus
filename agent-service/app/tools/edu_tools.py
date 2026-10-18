"""智能体工具集: Milvus 检索 / 知识图谱查询 / 课程检索 / 学情查询"""
import httpx
from langchain_core.tools import tool
from app.config import COURSE_SERVICE_URL, TEACHING_SERVICE_URL, RERANKER_URL


@tool
def search_course_resources(query: str, top_k: int = 5) -> list:
    """在课程资源向量库中语义检索与查询最相关的教学资源(讲义/习题/实训任务)。"""
    # 实际实现: 经 ai-service /internal/vector-search 或直连 Milvus SDK;
    # 此处封装为对 ai-service 内部检索接口的调用(Java 侧持有 Milvus 连接池)
    with httpx.Client(timeout=15) as client:
        resp = client.post(f"{RERANKER_URL.rstrip('/')}/internal/search", json={
            "query": query, "topK": top_k, "collections": ["edu_resources", "kg33618_docs"],
        })
        resp.raise_for_status()
        return resp.json().get("results", [])


@tool
def query_knowledge_graph(entity: str, relation_hops: int = 1) -> dict:
    """查询 "33618" 产业知识图谱: 返回某知识点/岗位/产业的一跳关联(先修、关联岗位、覆盖课程)。"""
    with httpx.Client(timeout=15) as client:
        resp = client.get(f"{COURSE_SERVICE_URL.rstrip('/')}/knowledge/graph", params={
            "entity": entity, "hops": relation_hops,
        })
        resp.raise_for_status()
        return resp.json()


@tool
def get_student_mastery(student_id: str, course_id: str | None = None) -> dict:
    """查询学生知识点掌握度(知识追踪 BKT/DKT 模型输出), 支持按课程过滤。"""
    with httpx.Client(timeout=15) as client:
        resp = client.get(f"{TEACHING_SERVICE_URL.rstrip('/')}/kt/mastery", params={
            "studentId": student_id, **({"courseId": course_id} if course_id else {}),
        })
        resp.raise_for_status()
        return resp.json()


@tool
def get_learning_path(student_id: str) -> dict:
    """查询学生当前进行中的个性化学习路径与步骤进度。"""
    with httpx.Client(timeout=15) as client:
        resp = client.get(f"{TEACHING_SERVICE_URL.rstrip('/')}/paths", params={
            "studentId": student_id, "status": "IN_PROGRESS",
        })
        resp.raise_for_status()
        return resp.json()


ALL_TOOLS = [search_course_resources, query_knowledge_graph,
             get_student_mastery, get_learning_path]
