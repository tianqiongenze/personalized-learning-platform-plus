# 重庆 "33618" 现代制造业集群产业知识图谱（kg33618）

本目录为「千人千面个性化课程资源系统」的 **RAG 知识底座** 种子数据，承载申报书创新点三
（LLM + RAG 以 "33618" 产业知识图谱为底座的 6 类资源生成）。

在此之前 NebulaGraph 已部署但 `kg33618` space 为空，AI 生成链路检索不到任何产业先验，
生成的资源只能靠 LLM 常识「编」产业情境。本目录补齐了真实、结构化的产业图谱数据。

---

## 1. 文件清单

| 文件 | 说明 |
|------|------|
| `schema.ngql` | NebulaGraph DDL：建 `kg33618` space + 4 个 TAG + 5 类 EDGE + 4 个索引 |
| `seed_33618.ngql` | 种子数据（`INSERT VERTEX` / `INSERT EDGE`），由 `build_kg.py` 生成 |
| `kg33618.json` | 与 ngql 同集合的 JSON 版本（含 `industry`/`post`/`subject` 冗余字段便于离线使用），供评测 / 单测加载 |
| `build_kg.py` | 生成器：结构化定义 → 同时产出 `seed_33618.ngql` 与 `kg33618.json` |
| `README.md` | 本文件 |

> `seed_33618.ngql` 与 `kg33618.json` 均由 `build_kg.py` 生成，改数据请改生成器后重跑
> `python build_kg.py`，保证两份产物一致。二者顶点/边集合完全一致；JSON 为便于离线消费，
> 在 `post`/`knowledge_point` 顶点上额外冗余了 `industry`/`post`/`subject` 字段
> （这些关系在 ngql 中由 `belongs_to` / `requires` 边表达）。

---

## 2. 图谱结构（Schema）

Schema 与既有代码 **严格对齐**，改动需同步三处：

* `k8s/base/nebula.yaml` 的 `nebula-init` Job（space/tag/edge 定义）
* `agent-service/app/tools/edu_tools.py` → `query_knowledge_graph`（查询入口）
* `agent-service/app/agents/generation_graph.py` → `retrieve_context`（检索节点）

### 2.1 实体（TAG）

| TAG | 属性 | 含义 |
|-----|------|------|
| `industry` | `name`, `cluster` | 产业（33618 集群体系中的一个集群） |
| `post` | `name` | 岗位（中职/高职对口就业岗位） |
| `knowledge_point` | `name`, `subject`, `difficulty` | 知识点（教学最小单元） |
| `resource` | `res_id`, `type` | 6 类生成资源落库实体（生成引擎写入） |

### 2.2 关系（EDGE）

| EDGE | 方向 | 属性 | 语义 |
|------|------|------|------|
| `belongs_to` | (post) → (industry) | — | 岗位归属产业 |
| `requires` | (post) → (knowledge_point) | — | 岗位要求掌握的知识点 |
| `prerequisite` | (kp) → (kp) | `weight` | 先修：学完起点才能学终点（基础→进阶） |
| `relates_to` | (kp) → (kp) | `weight` | 跨岗位/跨产业弱关联 |
| `covers` | (resource) → (knowledge_point) | — | 资源覆盖知识点（生成引擎写回） |

### 2.3 主键（VID）命名约定

`FIXED_STRING(64)`，全部为 ASCII 可读业务主键：

```
ind_<产业缩写>            例: ind_icv   (智能网联新能源汽车)
post_<产业缩写>_<两位序号> 例: post_icv_01
kp_<产业缩写>_<岗位序号>_<知识点序号>  例: kp_icv_01_05
```

### 2.4 规模统计

| 维度 | 数量 | 申报/任务下限 |
|------|------|----------------|
| 产业 `industry` | **14** | ≥ 10 |
| 岗位 `post` | **50** | ≥ 40 |
| 知识点 `knowledge_point` | **271** | ≥ 200 |
| 关系（边）合计 | **590** | ≥ 400 |
| ├ `belongs_to` | 50 | |
| ├ `requires` | 271 | |
| ├ `prerequisite` | 221 | |
| └ `relates_to` | 48 | |

覆盖的产业集群（14 个）：

* **3 大万亿级主导**：智能网联新能源汽车、新一代电子信息制造业、先进材料
* **3 大五千亿级支柱**：智能装备及智能制造、食品及农产品加工、软件信息服务
* **4 个千亿级特色优势**：高端摩托车、新能源及新型储能、轨道交通装备、生物医药
* **4 个「新星」集群**：卫星互联网、低空经济（无人机）、传感器及仪器仪表、功率半导体及集成电路

---

## 3. 如何导入

### 3.1 前置：NebulaGraph 已就绪

`k8s/base/nebula.yaml` 已部署 `graphd(9669) / metad(9559) / storaged(9779)`，
并已由 init Job 注册 storage host。以下命令在能访问 graphd 的机器上执行。

### 3.2 方式 A：nebula-console（推荐）

```bash
# 1) 建 space / tag / edge / index
nebula-console -addr <graphd-host> -port 9669 -u root -p nebula -f schema.ngql

# 2) 灌入种子数据（末尾自动 REBUILD 索引 + SUBMIT JOB STATS）
nebula-console -addr <graphd-host> -port 9669 -u root -p nebula -f seed_33618.ngql
```

K8s 集群内可用一次性 Pod：

```bash
kubectl -n infra run nebula-import --rm -it --restart=Never \
  --image=vesoft/nebula-console:v3.8.0 -- \
  sh -c 'nebula-console -addr nebula-graphd -port 9669 -u root -p nebula -f /dev/stdin' \
  < schema.ngql
```

> 也可把 `schema.ngql` + `seed_33618.ngql` 挂进已有 init Job（`nebula.yaml`）替代其内联 DDL。

### 3.3 方式 B：离线 / 单测加载 JSON

```python
import json
kg = json.load(open("data/kg33618/kg33618.json", encoding="utf-8"))
kg["stats"]        # {'industry': 14, 'post': 50, 'knowledge_point': 271, 'edges_total': 590, ...}
kg["vertices"]["knowledge_point"]   # 知识点列表
kg["edges"]                          # 关系列表 [{type, from, to, props}, ...]
```

### 3.4 导入后校验

```nGQL
USE kg33618;
SHOW TAGS;
SHOW EDGES;
SHOW STATS;                       -- 复核各 tag/edge 计数
MATCH (p:post)-[:belongs_to]->(i:industry {name:"智能网联新能源汽车"}) RETURN p.name;
MATCH (k:knowledge_point {name:"PLC梯形图编程"})-[:prerequisite*1..3]->(n) RETURN n.name;
```

---

## 4. 作为 RAG 底座如何被生成引擎检索

生成链路（`agent-service`，LangGraph 状态机）关键节点 `retrieve_context`：

```
parse_intent → retrieve_context → generate → review → (revise) → publish
                    │
                    ├─ search_course_resources(query, top_k=8)
                    │     → ai-service/reranker /internal/search
                    │     → collections: ["edu_resources", "kg33618_docs"]   ← 向量侧(产业文档)
                    │
                    └─ query_knowledge_graph(entity=知识点, hops=1)
                          → course-service GET /knowledge/graph?entity=&hops=
                          → NebulaGraph kg33618 (本图谱)                   ← 图侧(产业先验)
```

1. **图侧（本图谱）**：`query_knowledge_graph` 以当前知识点为锚点，在 `kg33618` 中做 1 跳扩展，
   取回其 **先修知识点、关联岗位、所属产业**，注入生成 Prompt 的 `{context}`，
   使「产业情境」来自真实集群/岗位而非模型臆造。
2. **向量侧**：产业文档（可由 `kg33618.json` 的 `industry/post/knowledge_point` 文本化后
   灌入 `kg33618_docs` collection）参与语义召回，与图结果合并成 RAG 上下文。
3. **路径规划**：`prerequisite` 链为个性化学习路径提供拓扑序，`relates_to` 提供跨岗位迁移推荐。
4. **落库回写**：生成资源以 `resource` 顶点入库，并用 `covers` 边指向覆盖的知识点，形成闭环。

> 依赖的查询入口当前为 `course-service` 的 `/knowledge/graph`；若该端点尚未实现，
> 可直接经 nebula-java / nebula-console 执行 README 第 3.4 节的 `MATCH` 查询等价实现。

---

## 5. 数据可信度与边界说明

* 产业集合取自重庆 "33618" 现代制造业集群体系的公开提法
  （3 大万亿级主导 + 3 大五千亿级支柱 + 6 大千亿级特色优势 + 18 个「新星」集群）。
* 岗位名、知识点名贴合高职/中职对口专业（新能源汽车技术、电气自动化技术、电子信息工程
  技术、软件技术、城市轨道交通车辆技术等），**无占位符**。
* `prerequisite` 先修链按各岗位知识点由浅入深的自然教学顺序构建；
  `relates_to` 跨岗位/跨产业关联为代表性示例（`build_kg.py` 中 `EXTRA_RELATES`），
  可按真实人才培养方案继续扩充。
* 本数据为**教学用示例种子集**，用于打通 RAG 检索链路与评测；生产环境应结合院校
  人才培养方案与产业调研报告持续校订。
