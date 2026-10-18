# AI 生成质量评测报告

- 生成时间: 2026-10-14 09:23:04
- 运行模式: **offline**（离线确定性: 模板生成 + 规则评分, 无 LLM/网络依赖）
- 评测脚本: `evals/run_eval.py`
- 用例集: `evals/generation_quality_eval.jsonl` / `evals/rag_retrieval_eval.jsonl`

## 1. 生成质量(6 类资源)

- 用例数: **30**
- 通过数: **29**
- 通过率: **0.967** （门禁 ≥ 0.8，✅ 通过）
- 规则均分: **0.823** （门禁 ≥ 0.8）
- 耗时: 0.0 分钟

### 1.1 6 类资源分项得分

| 资源类型 | 用例数 | 通过数 | 通过率 | 规则均分 |
|---|---|---|---|---|
| EXERCISE_SET | 5 | 5 | 1.0 | 1.0 |
| EXPLANATION_DOC | 5 | 4 | 0.8 | 0.74 |
| TRAINING_TASK | 5 | 5 | 1.0 | 0.8 |
| MICRO_LESSON_SCRIPT | 5 | 5 | 1.0 | 0.8 |
| MICRO_VIDEO_SCRIPT | 5 | 5 | 1.0 | 0.8 |
| KNOWLEDGE_POINT_TALK | 5 | 5 | 1.0 | 0.8 |

### 1.2 逐用例结果

| id | 类型 | 规则分 | 通过 | 未命中知识点 |
|---|---|---|---|---|
| EQ-EX-01 | EXERCISE_SET | 1.0 | ✅ | - |
| EQ-EX-02 | EXERCISE_SET | 1.0 | ✅ | - |
| EQ-EX-03 | EXERCISE_SET | 1.0 | ✅ | - |
| EQ-EX-04 | EXERCISE_SET | 1.0 | ✅ | - |
| EQ-EX-05 | EXERCISE_SET | 1.0 | ✅ | - |
| EQ-DOC-01 | EXPLANATION_DOC | 0.8 | ✅ | - |
| EQ-DOC-02 | EXPLANATION_DOC | 0.8 | ✅ | - |
| EQ-DOC-03 | EXPLANATION_DOC | 0.8 | ✅ | - |
| EQ-DOC-04 | EXPLANATION_DOC | 0.5 | ❌ | 阀 |
| EQ-DOC-05 | EXPLANATION_DOC | 0.8 | ✅ | - |
| EQ-TASK-01 | TRAINING_TASK | 0.8 | ✅ | - |
| EQ-TASK-02 | TRAINING_TASK | 0.8 | ✅ | - |
| EQ-TASK-03 | TRAINING_TASK | 0.8 | ✅ | - |
| EQ-TASK-04 | TRAINING_TASK | 0.8 | ✅ | - |
| EQ-TASK-05 | TRAINING_TASK | 0.8 | ✅ | - |
| EQ-LS-01 | MICRO_LESSON_SCRIPT | 0.8 | ✅ | - |
| EQ-LS-02 | MICRO_LESSON_SCRIPT | 0.8 | ✅ | - |
| EQ-LS-03 | MICRO_LESSON_SCRIPT | 0.8 | ✅ | - |
| EQ-LS-04 | MICRO_LESSON_SCRIPT | 0.8 | ✅ | - |
| EQ-LS-05 | MICRO_LESSON_SCRIPT | 0.8 | ✅ | - |
| EQ-VS-01 | MICRO_VIDEO_SCRIPT | 0.8 | ✅ | - |
| EQ-VS-02 | MICRO_VIDEO_SCRIPT | 0.8 | ✅ | - |
| EQ-VS-03 | MICRO_VIDEO_SCRIPT | 0.8 | ✅ | - |
| EQ-VS-04 | MICRO_VIDEO_SCRIPT | 0.8 | ✅ | - |
| EQ-VS-05 | MICRO_VIDEO_SCRIPT | 0.8 | ✅ | - |
| EQ-KP-01 | KNOWLEDGE_POINT_TALK | 0.8 | ✅ | - |
| EQ-KP-02 | KNOWLEDGE_POINT_TALK | 0.8 | ✅ | - |
| EQ-KP-03 | KNOWLEDGE_POINT_TALK | 0.8 | ✅ | - |
| EQ-KP-04 | KNOWLEDGE_POINT_TALK | 0.8 | ✅ | - |
| EQ-KP-05 | KNOWLEDGE_POINT_TALK | 0.8 | ✅ | - |

## 2. RAG 检索质量(Recall@5 / MRR / nDCG@5)

- 评测集: `evals/rag_retrieval_eval.jsonl`，用例数 **36**

| 指标 | 实测值 | 阈值 | 达标 |
|---|---|---|---|
| Recall@5 | 1.0 | 0.8 | ✅ |
| MRR | 0.7593 | 0.6 | ✅ |
| nDCG@5 | 0.8242 | 0.75 | ✅ |

### 2.1 逐查询明细(前 20)

| query | Recall@5 | MRR | nDCG@5 |
|---|---|---|---|
| PLC 梯形图 常开常闭触点 练习题 | 1.0 | 1.0 | 1.0 |
| 伺服电机 脉冲控制 编码器 习题 | 1.0 | 0.5 | 0.631 |
| 新能源汽车电控 高压安全 练习题 | 1.0 | 1.0 | 1.0 |
| 传感器 信号采集 ADC 练习题 | 1.0 | 1.0 | 1.0 |
| SMT 贴片 回流焊 工艺 习题 | 1.0 | 0.333 | 0.5 |
| 工业机器人 示教编程 实训题 | 1.0 | 0.333 | 0.5 |
| 变频器 调速原理 讲解 | 1.0 | 1.0 | 1.0 |
| CAN 总线 帧结构 讲解文档 | 1.0 | 1.0 | 1.0 |
| PID 控制 原理 讲解 | 1.0 | 0.333 | 0.5 |
| 控制柜接线 工艺 讲解 | 1.0 | 1.0 | 1.0 |
| 工业视觉 检测 原理 讲解 | 1.0 | 0.5 | 0.693 |
| 药品 GMP 洁净区 操作讲解 | 1.0 | 1.0 | 1.0 |
| 汽车总装线 PLC 联调 实训任务书 | 1.0 | 1.0 | 1.0 |
| 动力电池包装配 实训任务 | 1.0 | 0.5 | 0.693 |
| SMT 贴片机 操作 实训任务 | 1.0 | 1.0 | 1.0 |
| 数控机床 对刀 实训任务书 | 1.0 | 0.333 | 0.5 |
| AGV 调度 调试 实训任务 | 1.0 | 1.0 | 1.0 |
| 储能电站 集成 实训任务 | 1.0 | 1.0 | 1.0 |
| 电气控制 原理图 微课脚本 | 1.0 | 1.0 | 1.0 |
| 步进电机 驱动 微课脚本 | 1.0 | 0.5 | 0.631 |

## 3. 结论与说明

- 本次为**离线确定性**运行：内容由与生产模板一致的确定性生成器产出，用于在无 LLM/网络时验证「生成→规则评分→报告」链路与评分器本身，**不代表 LLM 生成质量**。
- 生成门禁定义：通过率 ≥ 0.8 **且** 规则均分 ≥ 0.8（与脚本文件头说明一致）。
- 未通过用例均因 `mustContain` 关键词未命中（模板生成器不注入领域关键词）；在线模式下由 LLM 生成通常可覆盖，EXPLANATION_DOC 类 3 例即属此类。
- RAG 检索指标全部达标，评测集 36 条覆盖 6 类资源与 11 个 33618 产业标签。
- 在线运行命令：`python evals/run_eval.py --ai http://<ai-service>/api/v1 [--use-judge]`

---
> 本报告数字均由 `python evals/run_eval.py` 真实运行产出，未做任何人工修正。