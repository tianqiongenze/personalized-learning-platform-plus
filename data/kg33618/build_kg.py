# -*- coding: utf-8 -*-
"""重庆 "33618" 现代制造业集群产业知识图谱 —— 种子数据生成器。

用途: 从结构化定义一次性生成 seed_33618.ngql(供 nebula-console 导入)与
      kg33618.json(供离线/评测加载), 保证两者实体/关系完全一致。

schema 约定与以下既有实现保持一致(见 k8s/base/nebula.yaml 的 nebula-init Job
与 agent-service/app/tools/edu_tools.py:query_knowledge_graph):
    SPACE kg33618 (partition_num=10, replica_factor=1, vid_type=FIXED_STRING(64))
    TAG   industry(name, cluster)
    TAG   post(name)
    TAG   knowledge_point(name, subject, difficulty)
    TAG   resource(res_id, type)
    EDGE  belongs_to() / requires() / prerequisite(weight) / relates_to(weight) / covers()

边方向约定:
    (post)  -[belongs_to]->  (industry)      岗位归属产业
    (post)  -[requires]->    (knowledge_point) 岗位要求掌握的知识点
    (kp)    -[prerequisite]->(kp)             先修关系: 学完起点才能学终点(基础→进阶)
    (kp)    -[relates_to]->  (kp)             跨岗位关联知识点(弱关联, weight∈(0,1])
运行: python build_kg.py
"""
from __future__ import annotations

import json
import os

# ---------------------------------------------------------------------------
# 1. 产业层: 重庆 "33618" 现代制造业集群体系
#    3 大万亿级主导 + 3 大五千亿级支柱 + 6 大千亿级特色优势 + 18 个"新星"集群
#    这里覆盖 3 大主导 + 3 大支柱(核心 6 条) 及若干代表性"新星"/特色集群。
# ---------------------------------------------------------------------------
INDUSTRIES = [
    # vid,          名称,                    集群层级,            对应专业领域(subject)
    ("ind_icv",    "智能网联新能源汽车",      "万亿级主导产业集群", "新能源汽车技术"),
    ("ind_ei",     "新一代电子信息制造业",    "万亿级主导产业集群", "电子信息工程技术"),
    ("ind_am",     "先进材料",                "万亿级主导产业集群", "材料工程技术"),
    ("ind_im",     "智能装备及智能制造",      "五千亿级支柱产业集群", "电气自动化技术"),
    ("ind_fp",     "食品及农产品加工",        "五千亿级支柱产业集群", "食品智能加工技术"),
    ("ind_si",     "软件信息服务",            "五千亿级支柱产业集群", "软件技术"),
    ("ind_moto",   "高端摩托车",              "千亿级特色优势产业集群", "摩托车制造与维修"),
    ("ind_nes",    "新能源及新型储能",        "千亿级特色优势产业集群", "新能源装备技术"),
    ("ind_rail",   "轨道交通装备",            "千亿级特色优势产业集群", "城市轨道交通车辆技术"),
    ("ind_bio",    "生物医药",                "千亿级特色优势产业集群", "药品生物技术"),
    ("ind_sat",    "卫星互联网",              "18个新星产业集群", "卫星通信与导航技术"),
    ("ind_lowalt", "低空经济(无人机)",        "18个新星产业集群", "无人机应用技术"),
    ("ind_sensor", "传感器及仪器仪表",        "18个新星产业集群", "智能传感器技术"),
    ("ind_power",  "功率半导体及集成电路",    "18个新星产业集群", "微电子技术"),
]

# ---------------------------------------------------------------------------
# 2. 岗位层(每产业 3-6 个) + 知识点层(每岗位 4-8 个, 顺序即先修链顺序)
#    结构: industry_vid -> [ (post_name, [kp_name, ...]), ... ]
#    subject 取所属产业的专业领域; difficulty 按先修链位置递进(1..5)
# ---------------------------------------------------------------------------
JOBS = {
    "ind_icv": [
        ("新能源汽车电控系统调试员", [
            "高压安全操作规程", "动力电池管理系统(BMS)原理", "电机控制器(MCU)参数标定",
            "整车控制器(VCU)信号交互", "CAN总线报文解析", "高压互锁回路(PIL)诊断",
            "绝缘电阻检测方法"]),
        ("动力电池装配与检测员", [
            "锂离子电芯分选与配组", "电池模组激光焊接工艺", "电池包气密性检测",
            "电池热管理系统装配", "电池包绝缘耐压测试", "BMS采集线束安装"]),
        ("整车总装线电气装配工", [
            "整车线束布置与固定", "接插件压接工艺", "总装下线电检(EOL)流程",
            "整车网络拓扑识读", "灯光系统装配与调试", "螺纹拧紧扭矩控制"]),
        ("智能网联汽车测试员", [
            "车载毫米波雷达标定", "摄像头标定与图像质量评估", "自动驾驶数据采集与标注",
            "车路协同(V2X)通信测试", "域控制器刷写与诊断", "功能安全(ISO 26262)基础"]),
        ("新能源汽车售后维修技师", [
            "高压系统下电与验电流程", "动力电池健康状态(SOH)评估", "充电系统故障诊断",
            "驱动电机异响诊断", "车载充电机(OBC)检修", "故障码(DTC)读取与清除"]),
    ],
    "ind_ei": [
        ("SMT贴片设备操作员", [
            "锡膏印刷工艺", "贴片机程序编制", "回流焊温度曲线设置",
            "钢网清洗与保养", "AOI光学检测判读", "元件供料器(Feeder)装调"]),
        ("电子产品测试技术员", [
            "ICT在线测试原理", "功能测试(FCT)夹具使用", "示波器信号测量",
            "电路板故障定位", "测试程序调试", "ESD静电防护规范"]),
        ("显示模组装配技术员", [
            "液晶模组贴合工艺", "背光模组装配", "显示缺陷(亮点/暗点)判定",
            "模组点灯测试", "洁净室操作规范"]),
        ("集成电路封装测试操作员", [
            "芯片引线键合(Wire Bonding)工艺", "塑封工艺原理", "引脚成型与切筋",
            "芯片测试分选", "洁净车间防静电操作", "X-Ray焊点检测"]),
        ("电子装联工艺员", [
            "波峰焊工艺参数设置", "通孔元件手工焊接", "IPC-A-610验收标准",
            "焊接缺陷(虚焊/连锡)分析", "三防漆涂覆工艺"]),
    ],
    "ind_am": [
        ("金属材料热处理操作工", [
            "钢的退火与正火工艺", "淬火与回火原理", "感应加热表面淬火",
            "金相试样制备", "硬度测试(布氏/洛氏)", "热处理炉温度控制"]),
        ("铝合金挤压成型技术员", [
            "铝合金挤压模具结构", "挤压温度与速度控制", "时效处理工艺",
            "型材表面缺陷分析", "挤压机液压系统操作"]),
        ("复合材料成型工", [
            "碳纤维预浸料铺层", "热压罐固化工艺", "树脂传递模塑(RTM)工艺",
            "复合材料无损检测", "孔隙率检测方法"]),
        ("材料性能检测员", [
            "金属拉伸试验操作", "冲击韧性试验", "扫描电镜(SEM)样品制备",
            "材料化学成分光谱分析", "疲劳试验机操作", "检测数据记录与报告编制"]),
    ],
    "ind_im": [
        ("工业机器人系统运维员", [
            "机器人示教编程", "机器人坐标系标定", "末端执行器(夹爪)装调",
            "机器人离线编程", "安全围栏与急停回路", "机器人故障诊断与维护"]),
        ("电气自动化设备安装调试员", [
            "电气原理图识读", "PLC梯形图编程", "变频器参数设置",
            "传感器选型与接线", "控制柜接线工艺", "伺服系统调试"]),
        ("工业互联网运维工程师", [
            "Modbus/Profinet通信配置", "工业网关数据采集", "OPC UA协议应用",
            "边缘计算网关部署", "设备上云与数据建模", "工业网络故障排查"]),
        ("数控机床操作与编程员", [
            "G代码编程基础", "数控机床对刀", "数控加工工艺编制",
            "CAD/CAM软件建模", "刀具磨损识别", "加工精度检测"]),
        ("智能制造产线集成技术员", [
            "产线数字孪生建模", "MES系统数据交互", "AGV调度与路径规划",
            "机器视觉引导定位", "产线节拍平衡分析", "工业现场总线组网"]),
    ],
    "ind_fp": [
        ("食品加工设备操作员", [
            "杀菌釜操作规程", "均质机操作与维护", "灌装机调试",
            "食品包装密封性检测", "CIP就地清洗工艺"]),
        ("食品质量检验员", [
            "微生物菌落总数检测", "食品理化指标检测", "高效液相色谱(HPLC)操作",
            "食品添加剂限量标准", "样品前处理技术", "检验报告编制规范"]),
        ("冷链仓储管理员", [
            "冷链温湿度监控", "冷库设备运行管理", "食品保质期管理",
            "冷链物流追溯系统", "仓储货位规划"]),
    ],
    "ind_si": [
        ("工业软件开发工程师", [
            "Java/Python编程基础", "关系数据库SQL", "RESTful API设计",
            "前后端分离架构", "Git版本控制", "单元测试与调试"]),
        ("大数据运维工程师", [
            "Linux系统运维", "Docker容器部署", "Hadoop/HDFS基础",
            "数据采集(Flume/Kafka)", "SQL数据查询与分析", "监控告警(Prometheus)"]),
        ("信息系统实施顾问", [
            "需求调研方法", "系统部署与配置", "用户培训与文档编写",
            "数据迁移与校验", "系统测试用例设计"]),
        ("信息安全运维员", [
            "网络安全基础", "防火墙策略配置", "漏洞扫描与加固",
            "日志分析与审计", "数据备份与恢复", "等保2.0合规基础"]),
    ],
    "ind_moto": [
        ("摩托车整车装配工", [
            "摩托车发动机总装工艺", "车架装配与校正", "整车电气线路装配",
            "制动系统调试", "整车下线检测"]),
        ("摩托车发动机测试员", [
            "发动机台架试验", "排放检测(国四标准)", "燃油供给系统调试",
            "点火系统故障诊断", "噪声与振动测试"]),
        ("摩托车维修技师", [
            "电喷系统检修", "离合器拆装与调整", "传动链条维护",
            "制动液更换与排气", "故障诊断思路"]),
    ],
    "ind_nes": [
        ("储能系统集成技术员", [
            "储能电池成组技术", "储能变流器(PCS)原理", "电池管理系统(BMS)通信",
            "储能电站电气一次/二次", "储能消防与热管理"]),
        ("光伏组件生产操作员", [
            "硅片制绒工艺", "电池片丝网印刷", "光伏组件层压工艺",
            "EL电致发光检测", "组件功率分档测试"]),
        ("充电桩安装调试员", [
            "交直流充电桩结构", "充电桩通信协议(OCPP)", "电气安全接地",
            "计量与计费模块", "充电桩故障排查"]),
    ],
    "ind_rail": [
        ("轨道交通车辆检修工", [
            "转向架结构与检修", "车辆空气制动系统", "受电弓维护",
            "牵引电机检修", "车门系统调试"]),
        ("轨道信号设备维护员", [
            "列车自动控制(ATC)系统基础", "转辙机维护", "轨道电路原理",
            "信号联锁系统", "通信传输设备维护"]),
        ("轨道交通供电技术员", [
            "牵引变电所运行", "接触网检修", "直流牵引供电系统",
            "高压开关柜操作", "继电保护基础"]),
    ],
    "ind_bio": [
        ("药品生产操作员", [
            "GMP规范基础", "固体制剂生产工艺", "压片机操作",
            "药品包装与批号管理", "洁净区更衣与操作规范"]),
        ("药物质量检验员", [
            "高效液相色谱(HPLC)操作", "药品含量测定", "微生物限度检查",
            "溶出度测定", "药品检验记录规范"]),
        ("医疗器械装配技术员", [
            "无菌医疗器械装配", "环氧乙烷灭菌工艺", "医疗器械生物相容性基础",
            "产品追溯系统", "装配工装使用"]),
    ],
    "ind_sat": [
        ("卫星终端设备调试员", [
            "卫星通信频段与链路", "天线对星与跟踪", "射频收发模块调试",
            "卫星终端信号测试", "基带信号处理基础"]),
        ("卫星总装测试技术员", [
            "卫星结构总装工艺", "卫星热控多层(MLI)安装", "卫星综合电测试",
            "洁净室防静电操作", "振动试验基础"]),
        ("地面站运维员", [
            "地面站天线系统", "测控通信链路", "站控软件操作",
            "电源与UPS维护", "数据接收与记录"]),
    ],
    "ind_lowalt": [
        ("无人机装配与调试员", [
            "多旋翼无人机结构", "飞控系统参数配置", "电机与电调匹配",
            "遥控链路调试", "无人机电池管理"]),
        ("无人机驾驶员(视距内)", [
            "无人机飞行原理", "起降与航线飞行操作", "空域与法规基础",
            "应急处置流程", "航拍数据采集"]),
        ("无人机维修员", [
            "飞控固件升级", "桨叶与机臂更换", "图传系统检修",
            "故障诊断与日志分析", "电池保养与检测"]),
    ],
    "ind_sensor": [
        ("传感器装配调试员", [
            "传感器敏感元件装配", "信号调理电路调试", "传感器标定与校准",
            "温度补偿技术", "传感器输出信号检测"]),
        ("智能仪表测试员", [
            "仪表电路原理", "仪表精度校验", "人机界面(HMI)配置",
            "通信接口调试(RS485)", "仪表故障排查"]),
        ("工业视觉系统集成员", [
            "工业相机选型与安装", "光源配置与打光", "图像处理算法基础",
            "视觉系统标定", "视觉检测系统调试"]),
    ],
    "ind_power": [
        ("半导体器件制造操作员", [
            "光刻工艺基础", "刻蚀工艺原理", "离子注入工艺",
            "薄膜沉积工艺", "洁净室操作规范", "晶圆缺陷检测"]),
        ("功率模块封装技术员", [
            "IGBT/SiC模块封装结构", "键合线工艺", "焊接空洞检测",
            "模块绝缘测试", "散热基板装配"]),
        ("芯片测试技术员", [
            "晶圆测试(CP)流程", "成品测试(FT)流程", "测试探针卡使用",
            "参数测试与分选", "测试数据分析"]),
    ],
}

# 跨岗位 relates_to 关联(体现知识点的跨产业迁移), 显式给出更贴近真实语义
EXTRA_RELATES = [
    ("kp_icv_01_05", "kp_im_05_06"),   # CAN总线报文解析 ↔ 工业现场总线组网
    ("kp_icv_04_01", "kp_sensor_03_04"),  # 毫米波雷达标定 ↔ 视觉系统标定
    ("kp_im_02_02", "kp_im_01_01"),    # PLC梯形图编程 ↔ 机器人示教编程
    ("kp_im_03_03", "kp_si_02_04"),    # OPC UA ↔ 数据采集
    ("kp_nes_03_02", "kp_icv_05_03"),  # 充电桩通信协议 ↔ 充电系统故障诊断
    ("kp_power_01_01", "kp_ei_04_01"),  # 光刻工艺 ↔ 引线键合
    ("kp_sensor_01_03", "kp_im_02_04"),  # 传感器标定 ↔ 传感器选型与接线
    ("kp_lowalt_01_03", "kp_moto_01_03"),  # 电机与电调匹配 ↔ 整车电气线路装配
    ("kp_sat_01_03", "kp_sensor_02_04"),  # 射频收发模块调试 ↔ 通信接口调试
    ("kp_rail_03_05", "kp_nes_01_04"),   # 继电保护基础 ↔ 储能电站电气
    ("kp_bio_02_01", "kp_fp_02_03"),     # HPLC操作(药品) ↔ HPLC操作(食品)
    ("kp_si_04_03", "kp_si_02_06"),      # 漏洞扫描与加固 ↔ 监控告警
]


def build():
    vertices = {"industry": [], "post": [], "knowledge_point": []}
    edges = []  # {"type","from","to","props"}
    kp_index = {}  # vid -> (name, subject, difficulty)

    for ind_vid, ind_name, cluster, subject in INDUSTRIES:
        vertices["industry"].append({"vid": ind_vid, "name": ind_name, "cluster": cluster})

    for ind_vid, ind_name, cluster, subject in INDUSTRIES:
        jobs = JOBS[ind_vid]
        for pi, (post_name, kp_names) in enumerate(jobs, 1):
            post_vid = f"post_{ind_vid[4:]}_{pi:02d}"
            vertices["post"].append({"vid": post_vid, "name": post_name,
                                     "industry": ind_vid, "subject": subject})
            edges.append({"type": "belongs_to", "from": post_vid, "to": ind_vid, "props": {}})

            kp_vids = []
            for ki, kp_name in enumerate(kp_names, 1):
                kp_vid = f"kp_{ind_vid[4:]}_{pi:02d}_{ki:02d}"
                difficulty = min(5, ki)  # 沿先修链由浅入深 1..5
                kp_index[kp_vid] = (kp_name, subject, difficulty)
                vertices["knowledge_point"].append({
                    "vid": kp_vid, "name": kp_name, "subject": subject,
                    "difficulty": difficulty, "industry": ind_vid, "post": post_vid})
                edges.append({"type": "requires", "from": post_vid, "to": kp_vid, "props": {}})
                kp_vids.append(kp_vid)

            # 先修链: kp1 -> kp2 -> ...  (基础指向进阶)
            for a, b in zip(kp_vids, kp_vids[1:]):
                edges.append({"type": "prerequisite", "from": a, "to": b,
                              "props": {"weight": 1.0}})

            # 同产业相邻岗位首知识点弱关联(relates_to), 形成跨岗位知识迁移路径
            if pi > 1:
                prev_post_vid = f"post_{ind_vid[4:]}_{pi - 1:02d}"
                prev_first = f"kp_{ind_vid[4:]}_{pi - 1:02d}_01"
                edges.append({"type": "relates_to", "from": prev_first, "to": kp_vids[0],
                              "props": {"weight": 0.6}})

    for a, b in EXTRA_RELATES:
        if a in kp_index and b in kp_index:
            edges.append({"type": "relates_to", "from": a, "to": b,
                          "props": {"weight": 0.8}})

    return vertices, edges


def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"')


def gen_ngql(vertices, edges) -> str:
    out = []
    out.append("-- ============================================================================")
    out.append("-- 重庆 \"33618\" 现代制造业集群产业知识图谱 —— 种子数据(seed)")
    out.append("-- 由 build_kg.py 生成, 请勿手工编辑; 导入前先执行 schema.ngql。")
    out.append("-- 导入: nebula-console -addr <graphd> -port 9669 -u root -p nebula \\")
    out.append("--         -f seed_33618.ngql")
    out.append("-- ============================================================================")
    out.append("USE kg33618;")
    out.append("")

    def batch_insert(header, lines, chunk=60):
        for i in range(0, len(lines), chunk):
            out.append(header + " " + ",\n    ".join(lines[i:i + chunk]) + ";")

    out.append("-- ---------- 产业(industry): %d ----------" % len(vertices["industry"]))
    batch_insert("INSERT VERTEX industry(name, cluster) VALUES",
                 ['"%s":("%s", "%s")' % (v["vid"], esc(v["name"]), esc(v["cluster"]))
                  for v in vertices["industry"]])
    out.append("")

    out.append("-- ---------- 岗位(post): %d ----------" % len(vertices["post"]))
    batch_insert("INSERT VERTEX post(name) VALUES",
                 ['"%s":("%s")' % (v["vid"], esc(v["name"])) for v in vertices["post"]])
    out.append("")

    out.append("-- ---------- 知识点(knowledge_point): %d ----------" % len(vertices["knowledge_point"]))
    batch_insert("INSERT VERTEX knowledge_point(name, subject, difficulty) VALUES",
                 ['"%s":("%s", "%s", %d)' % (v["vid"], esc(v["name"]), esc(v["subject"]), v["difficulty"])
                  for v in vertices["knowledge_point"]])
    out.append("")

    by_type = {}
    for e in edges:
        by_type.setdefault(e["type"], []).append(e)

    order = ["belongs_to", "requires", "prerequisite", "relates_to"]
    for t in order:
        items = by_type.get(t, [])
        out.append("-- ---------- 边 %s: %d ----------" % (t, len(items)))
        if t in ("prerequisite", "relates_to"):
            header = "INSERT EDGE %s(weight) VALUES" % t
            lines = ['"%s"->"%s":(%s)' % (e["from"], e["to"], e["props"].get("weight", 1.0))
                     for e in items]
        else:
            header = "INSERT EDGE %s() VALUES" % t
            lines = ['"%s"->"%s":()' % (e["from"], e["to"]) for e in items]
        batch_insert(header, lines)
        out.append("")

    out.append("-- 索引重建(数据入库后执行, 供 LOOKUP/全文检索使用)")
    out.append("REBUILD TAG INDEX idx_industry_name, idx_post_name, idx_kp_name, idx_kp_subject;")
    out.append("SUBMIT JOB STATS;")
    return "\n".join(out) + "\n"


def gen_json(vertices, edges) -> str:
    by_type = {}
    for e in edges:
        by_type[e["type"]] = by_type.get(e["type"], 0) + 1
    doc = {
        "space": "kg33618",
        "description": "重庆 33618 现代制造业集群产业知识图谱(种子数据)",
        "generated_by": "data/kg33618/build_kg.py",
        "schema": {
            "tags": {
                "industry": ["name", "cluster"],
                "post": ["name"],
                "knowledge_point": ["name", "subject", "difficulty"],
                "resource": ["res_id", "type"],
            },
            "edges": {
                "belongs_to": [], "requires": [],
                "prerequisite": ["weight"], "relates_to": ["weight"], "covers": [],
            },
            "edge_semantics": {
                "belongs_to": "(post)->(industry) 岗位归属产业",
                "requires": "(post)->(knowledge_point) 岗位要求知识点",
                "prerequisite": "(kp)->(kp) 先修: 学完起点才能学终点",
                "relates_to": "(kp)->(kp) 跨岗位弱关联",
            },
        },
        "stats": {
            "industry": len(vertices["industry"]),
            "post": len(vertices["post"]),
            "knowledge_point": len(vertices["knowledge_point"]),
            "edges_total": len(edges),
            "edges_by_type": by_type,
        },
        "vertices": vertices,
        "edges": edges,
    }
    return json.dumps(doc, ensure_ascii=False, indent=2)


def main():
    vertices, edges = build()
    here = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(here, "seed_33618.ngql"), "w", encoding="utf-8") as f:
        f.write(gen_ngql(vertices, edges))
    with open(os.path.join(here, "kg33618.json"), "w", encoding="utf-8") as f:
        f.write(gen_json(vertices, edges))

    by_type = {}
    for e in edges:
        by_type[e["type"]] = by_type.get(e["type"], 0) + 1
    print("industries :", len(vertices["industry"]))
    print("posts      :", len(vertices["post"]))
    print("kps        :", len(vertices["knowledge_point"]))
    print("edges      :", len(edges), by_type)


if __name__ == "__main__":
    main()
