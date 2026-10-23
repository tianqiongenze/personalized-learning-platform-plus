#!/usr/bin/env python3
"""课程数据重填充 — 覆盖 本科/专科/职业本科(重庆33618产业对接) 层次
背景: courses 表 24,011 门全部为 K12 压测数据(grade_level=一年级~高三), 不删(压测依赖),
新增高质量课程集。表: courses/chapters/knowledge_points(course_db @ TiDB)。
执行后 GraphQL courses(page 0) 按 created_at DESC 返回, 新课立即可见。
"""
import sys, uuid, json, pymysql
from datetime import datetime

TENANT = 'da98138f-486f-42db-944c-219a5a44bf52'
PW = sys.argv[1]

# (code_prefix, grade_level, subject, name, description, cluster33618)
COURSES = [
    # ---- 本科基础(12) ----
    ('BK', '本科', '计算机', '数据结构与算法', '线性表、树、图与经典算法设计, 配套 OJ 实训。', None),
    ('BK', '本科', '计算机', '操作系统原理', '进程调度、内存管理、文件系统与并发控制。', None),
    ('BK', '本科', '计算机', '计算机网络', 'TCP/IP 协议栈、路由交换与网络安全基础。', None),
    ('BK', '本科', '计算机', '数据库系统概论', '关系模型、SQL、事务与并发控制、索引优化。', None),
    ('BK', '本科', '计算机', '机器学习导论', '监督/无监督学习、模型评估与典型算法实现。', None),
    ('BK', '本科', '计算机', '软件工程', '需求分析、架构设计、敏捷开发与软件质量保障。', None),
    ('BK', '本科', '数学', '线性代数', '矩阵理论、向量空间与特征值分解及其工程应用。', None),
    ('BK', '本科', '数学', '概率论与数理统计', '随机变量、大数定律、参数估计与假设检验。', None),
    ('BK', '本科', '物理', '大学物理', '力学、电磁学、热学与近代物理基础实验。', None),
    ('BK', '本科', '电子', '电路分析基础', '电阻电路、动态电路、正弦稳态分析与仿真。', None),
    ('BK', '本科', '机械', '机械设计基础', '机构学、零件设计与传动系统综合设计。', None),
    ('BK', '本科', '管理', '管理学原理', '计划、组织、领导、控制四大职能与案例分析。', None),
    # ---- 专科(高职)技能(12) ----
    ('ZK', '专科', '自动化', 'PLC 应用技术', '西门子 S7-1200 编程、梯形图与工业组态实训。', None),
    ('ZK', '专科', '自动化', '工业机器人操作与运维', 'ABB/KUKA 机器人示教编程、工作站运维。', '装备制造'),
    ('ZK', '专科', '汽车', '汽车检测与维修技术', '整车电气检测、发动机 diagnostics 与保养规范。', None),
    ('ZK', '专科', '电子', '电子产品装接与调试', 'SMT 工艺、PCB 焊接、整机调试与不良分析。', '新一代电子信息制造业'),
    ('ZK', '专科', '信息', '网络搭建与运维', '交换/路由配置、防火墙策略与企业网运维。', None),
    ('ZK', '专科', '信息', '大数据技术应用', 'Hadoop/Spark 部署、数据清洗与可视化报表。', None),
    ('ZK', '专科', '信息', '移动应用开发', 'uni-app 跨端开发、小程序上架与联调。', None),
    ('ZK', '专科', '机械', '数控加工技术', '数控车/铣编程、CAM 后处理与精度检测。', '装备制造'),
    ('ZK', '专科', '电商', '电子商务实务', '店铺运营、直播电商与供应链履约。', None),
    ('ZK', '专科', '财经', '会计信息化', '业财一体化软件操作、报表编制与税务申报。', None),
    ('ZK', '专科', '医护', '护理学基础', '基础护理操作、院内感染控制与人文关怀。', None),
    ('ZK', '专科', '物流', '物流管理基础', '仓储、运输、配送中心运作与 WMS 实训。', None),
    # ---- 重庆 33618 产业对接(职业本科, 21) ----
    ('ZB', '职业本科', '智能网联新能源汽车', '新能源汽车动力电池技术', '动力电池结构、BMS 管理与热失控防护; 对接 33618 万亿级产业集群。', '智能网联新能源汽车'),
    ('ZB', '职业本科', '智能网联新能源汽车', '智能网联汽车环境感知技术', '摄像头/毫米波/激光雷达融合感知与标注; 对接 33618。', '智能网联新能源汽车'),
    ('ZB', '职业本科', '智能网联新能源汽车', '电动汽车驱动与控制', '电机驱动、整车控制 VCU 与能量管理策略。', '智能网联新能源汽车'),
    ('ZB', '职业本科', '智能网联新能源汽车', '汽车软件与 OTA 技术', '车载软件架构、AUTOSAR 基础与 OTA 升级工程。', '智能网联新能源汽车'),
    ('ZB', '职业本科', '新一代电子信息制造业', '嵌入式系统设计', 'ARM 体系、RTOS 与外设驱动开发; 对接 33618。', '新一代电子信息制造业'),
    ('ZB', '职业本科', '新一代电子信息制造业', '集成电路制造工艺', '光刻、刻蚀、薄膜沉积工艺流程与洁净间规范。', '新一代电子信息制造业'),
    ('ZB', '职业本科', '新一代电子信息制造业', 'FPGA 应用开发', 'Verilog HDL、时序约束与数字系统原型验证。', '新一代电子信息制造业'),
    ('ZB', '职业本科', '先进材料', '锂电池材料技术', '正负极材料、电解液体系与材料表征方法。', '先进材料'),
    ('ZB', '职业本科', '先进材料', '轻合金材料成型技术', '铝/镁合金熔铸、挤压成型与热处理工艺。', '先进材料'),
    ('ZB', '职业本科', '先进材料', '材料现代分析方法', 'XRD/SEM/TEM 原理与材料失效分析。', '先进材料'),
    ('ZB', '职业本科', '装备制造', '智能制造装备技术', '产线数字化、工业母机与在线检测系统。', '装备制造'),
    ('ZB', '职业本科', '装备制造', '多轴数控编程与仿真', '五轴加工工艺、VERICUT 仿真与后处理。', '装备制造'),
    ('ZB', '职业本科', '软件信息服务', '云原生应用开发', '容器化、K8s 编排与微服务治理实战。', '软件信息服务'),
    ('ZB', '职业本科', '软件信息服务', '大数据平台运维', '集群部署、监控告警与数据管道治理。', '软件信息服务'),
    ('ZB', '职业本科', '软件信息服务', '人工智能应用开发', 'LLM 应用、RAG 与智能体工程化落地。', '软件信息服务'),
    ('ZB', '职业本科', '新型显示', '显示面板制造技术', 'OLED/LCD 阵列、成盒与模组工艺。', '新型显示'),
    ('ZB', '职业本科', '新型储能', '储能系统集成技术', '电芯选型、PCS 协同与储能电站安全。', '新型储能'),
    ('ZB', '职业本科', '生物医药', '生物制药工艺基础', '细胞培养、纯化与 GMP 质量体系。', '生物医药'),
    ('ZB', '职业本科', '高端摩托车', '摩托车电控技术', 'ECU 匹配、电喷系统与整车测试。', '高端摩托车'),
    ('ZB', '职业本科', '船舶及海洋装备', '船舶焊接技术', '焊接工艺评定、船体分段建造与无损检测。', '船舶及海洋装备'),
    ('ZB', '职业本科', '食品及农产品加工', '食品智能制造', '杀菌工艺、产线自动化与食品安全体系。', '食品及农产品加工'),
]

CHAPTER_TITLES = ['基础认知', '核心原理', '工程实践', '综合案例']

def main():
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')
    conn = pymysql.connect(host='10.167.2.175', port=30210, user='root', password=PW, database='course_db')
    cur = conn.cursor()
    cur.execute("SELECT MAX(code) FROM courses WHERE code LIKE 'BK9%' OR code LIKE 'ZK9%' OR code LIKE 'ZB9%'")
    n_courses = n_ch = n_kp = 0
    sqls = []
    for prefix, level, subject, name, desc, cluster in COURSES:
        n_courses += 1
        cid = str(uuid.uuid4())
        code = f'{prefix}9{n_courses:03d}'
        settings = json.dumps({'cluster33618': cluster} if cluster else {}, ensure_ascii=False)
        sqls.append(("INSERT INTO courses (id,code,name,description,subject,grade_level,semester,status,tenant_id,creator_id,settings,version,created_at,updated_at) "
                     f"VALUES ('{cid}','{code}','{name}','{desc}','{subject}','{level}','2026秋','PUBLISHED','{TENANT}','system-refill','{settings}',0,'{now}','{now}')"))
        for si, ch_title in enumerate(CHAPTER_TITLES, 1):
            ch_id = str(uuid.uuid4())
            n_ch += 1
            sqls.append((f"INSERT INTO chapters (id,code,course_id,name,description,sort_order,difficulty,estimated_hours,parent_id,status,tenant_id,version,created_at,updated_at) "
                         f"VALUES ('{ch_id}','{code}-CH{si}','{cid}','{ch_title}','{name} · {ch_title}',{si},3,8,NULL,'PUBLISHED','{TENANT}',0,'{now}','{now}')"))
            for ki in (1, 2):
                kp_id = str(uuid.uuid4())
                n_kp += 1
                sqls.append((f"INSERT INTO knowledge_points (id,code,chapter_id,course_id,name,description,difficulty,status,tenant_id,version,created_at,updated_at) "
                             f"VALUES ('{kp_id}','{code}-KP{si}{ki}','{ch_id}','{cid}','{name}·知识点{si}{ki}','{ch_title}阶段知识点',{3 if ki==1 else 4},'ACTIVE','{TENANT}',0,'{now}','{now}')"))
    # 事务执行
    cur.execute("SELECT COUNT(*) FROM courses"); before = cur.fetchone()[0]
    try:
        for s in sqls: cur.execute(s)
        cur.execute("SELECT COUNT(*) FROM courses"); after = cur.fetchone()[0]
        conn.commit()
        print(f"OK before={before} after={after} inserted_courses={n_courses} chapters={n_ch} kps={n_kp}")
    except Exception as e:
        conn.rollback()
        print('ROLLBACK:', e); raise
    cur.execute("SELECT grade_level, COUNT(*) c FROM courses WHERE code LIKE 'BK9%' OR code LIKE 'ZK9%' OR code LIKE 'ZB9%' GROUP BY grade_level")
    print('new courses by level:', cur.fetchall())
    conn.close()
    with open('/Users/joelgong/PycharmProjects/studyings/personalized-learning-platform-plus/report/course-refill-2026-10-23.sql', 'w') as f:
        f.write('-- 课程重填充留底(前 30 条样例, 完整共 %d 条)\n' % len(sqls))
        f.write('\n'.join(sqls[:30]))

if __name__ == '__main__':
    main()
