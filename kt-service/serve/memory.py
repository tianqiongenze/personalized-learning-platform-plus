"""记忆金字塔(DeepTutor 借鉴) + SM-2/Ebbinghaus/ZPD/Bloom/Gap(LearnGraph 借鉴)
纯内存实现(推理层), 数据落 CRDB/Redis 由 Java 侧完成; 本模块聚焦算法。

金字塔:
  L1 原始学习事件(JSONL, 经 Pulsar 落盘) — append-only
  L2 事实摘要(会话级 Markdown 摘要)      — 滚动聚合
  L3 跨课程画像(JSON 画像 + Nebula student_graph)

学习科学模型:
  SM-2        间隔重复调度(interval/easeFactor)
  Ebbinghaus  遗忘曲线 R = exp(-t/S), S = F(ease, mastery)
  ZPD Bridge  前置掌握度>=0.7 且当前掌握度∈[0.3,0.7] 时进入最近发展区
  Bloom       认知分层(记忆/理解/应用/分析/评价/创造)加权掌握度
  Gap         4 类缺口: missing/partial/forgotten/misconception
"""
from __future__ import annotations

import math
import time
from dataclasses import dataclass, field

# ============ Bloom 认知分层(权重: 高阶认知对掌握度贡献更大) ============
BLOOM_LEVELS = ["remember", "understand", "apply", "analyze", "evaluate", "create"]
BLOOM_WEIGHTS = {"remember": 0.5, "understand": 0.7, "apply": 1.0,
                 "analyze": 1.2, "evaluate": 1.4, "create": 1.6}


# ============ L1: 学习事件 ============
@dataclass
class LearningEvent:
    student_id: str
    skill_id: str
    correct: bool
    bloom_level: str = "apply"
    ts: float = field(default_factory=lambda: time.time())
    latency_ms: int = 0
    course_id: str | None = None

    def validate(self) -> None:
        if not self.student_id or not self.skill_id:
            raise ValueError("studentId/skillId 必填")
        if self.bloom_level not in BLOOM_LEVELS:
            raise ValueError(f"bloomLevel 必须是 {BLOOM_LEVELS}")


class MemoryPyramid:
    """L1 事件流 -> L2 会话摘要 -> L3 画像(单进程内存版; 持久化由事件消费者完成)"""

    def __init__(self) -> None:
        self.l1_events: dict[str, list[LearningEvent]] = {}
        self.l2_summaries: dict[str, dict[str, dict]] = {}   # student -> skill -> summary
        self.l3_profiles: dict[str, dict] = {}               # student -> profile
        self.review_state: dict[str, dict[str, dict]] = {}   # student -> skill -> sm2 state
        self._version: dict[str, int] = {}

    # ---------- 写入路径 ----------
    def record(self, ev: LearningEvent) -> dict:
        ev.validate()
        self.l1_events.setdefault(ev.student_id, []).append(ev)
        self._recompute(ev.student_id, ev.skill_id)
        return self.snapshot(ev.student_id)

    # ---------- L2: 会话摘要 ----------
    def _recompute(self, sid: str, skill_id: str) -> None:
        events = [e for e in self.l1_events[sid] if e.skill_id == skill_id][-50:]
        if not events:
            return
        weighted = [float(e.correct) * BLOOM_WEIGHTS.get(e.bloom_level, 1.0)
                    for e in events]
        weights = [BLOOM_WEIGHTS.get(e.bloom_level, 1.0) for e in events]
        mastery = sum(weighted) / max(sum(weights), 1e-9)
        self.l2_summaries.setdefault(sid, {})[skill_id] = {
            "attempts": len(events),
            "mastery": round(min(max(mastery, 0.0), 1.0), 4),
            "lastBloom": events[-1].bloom_level,
            "lastTs": events[-1].ts,
            "correctRate": round(sum(e.correct for e in events) / len(events), 4),
        }
        self._version[sid] = self._version.get(sid, 0) + 1
        self._build_l3(sid)

    # ---------- L3: 跨课程画像 ----------
    def _build_l3(self, sid: str) -> None:
        summaries = self.l2_summaries.get(sid, {})
        if not summaries:
            return
        all_m = [s["mastery"] for s in summaries.values()]
        bloom_hist: dict[str, int] = {}
        for s in summaries.values():
            bloom_hist[s["lastBloom"]] = bloom_hist.get(s["lastBloom"], 0) + 1
        # 学习风格: 依 bloom 分布近似(测试型→reading, 高阶→visual, 实操型→kinesthetic)
        kinesthetic = bloom_hist.get("apply", 0) / max(len(summaries), 1)
        high_order = sum(bloom_hist.get(l, 0) for l in ("analyze", "evaluate", "create")) / max(len(summaries), 1)
        self.l3_profiles[sid] = {
            "version": self._version[sid],
            "nSkills": len(summaries),
            "overallMastery": round(sum(all_m) / len(all_m), 4),
            "bloomHistogram": bloom_hist,
            "learningStyle": {
                "visual": round(high_order, 3),
                "auditory": round(1 - kinesthetic - high_order, 3) if (kinesthetic + high_order) < 1 else 0.0,
                "kinesthetic": round(kinesthetic, 3),
                "reading": round(bloom_hist.get("remember", 0) / max(len(summaries), 1), 3),
            },
            "updatedAt": time.time(),
        }
        ls = self.l3_profiles[sid]["learningStyle"]
        ls["dominant"] = max(("visual", "auditory", "kinesthetic", "reading"), key=ls.get)

    # ---------- SM-2 间隔重复 ----------
    def review_schedule(self, sid: str, quality: int | None = None) -> list[dict]:
        """SM-2: quality 0-5; 产出每个技能的下次复习时间"""
        plan = []
        summaries = self.l2_summaries.get(sid, {})
        state_all = self.review_state.setdefault(sid, {})
        for skill_id, s in summaries.items():
            st = state_all.setdefault(skill_id, {"ef": 2.5, "interval": 0, "reps": 0, "last": s["lastTs"]})
            q = quality if quality is not None else int(s["mastery"] * 5)
            if q < 3:
                st["reps"], st["interval"] = 0, 1
            else:
                st["reps"] += 1
                if st["reps"] == 1:
                    st["interval"] = 1
                elif st["reps"] == 2:
                    st["interval"] = 6
                else:
                    st["interval"] = round(st["interval"] * st["ef"])
                st["ef"] = max(1.3, st["ef"] + 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
            st["last"] = time.time()
            interval_days = max(st["interval"], 1)
            retention = math.exp(- (time.time() - s["lastTs"]) / 86400 / max(interval_days * 1.5, 1))
            plan.append({
                "skillId": skill_id, "dueAt": time.strftime(
                    "%Y-%m-%dT%H:%M:%S", time.localtime(st["last"] + interval_days * 86400)),
                "intervalDays": interval_days, "easeFactor": round(st["ef"], 2),
                "retention": round(retention, 3), "mastery": s["mastery"],
            })
        plan.sort(key=lambda x: x["retention"])
        return plan

    # ---------- ZPD Bridge ----------
    def zpd_bridge(self, sid: str, prerequisites: dict[str, list[str]]) -> list[dict]:
        """返回处于最近发展区的技能: 前置全部 >=0.7 且自身 ∈[0.3,0.7]"""
        summaries = self.l2_summaries.get(sid, {})
        out = []
        for skill, pres in prerequisites.items():
            cur = summaries.get(skill, {}).get("mastery")
            if cur is None or not (0.3 <= cur <= 0.7):
                continue
            if all(summaries.get(p, {}).get("mastery", 0) >= 0.7 for p in pres):
                out.append({"skillId": skill, "mastery": cur, "zpdFit": True,
                            "prerequisites": pres})
        return out

    # ---------- Gap Detection(4 类) ----------
    def detect_gaps(self, sid: str, prerequisites: dict[str, list[str]],
                    threshold: float = 0.6, stale_days: int = 14) -> list[dict]:
        summaries = self.l2_summaries.get(sid, {})
        gaps = []
        for skill, s in summaries.items():
            m, last = s["mastery"], s["lastTs"]
            days = (time.time() - last) / 86400
            if m < 0.2:
                gaps.append({"skillId": skill, "gapType": "missing", "severity": "high",
                             "description": f"掌握度 {m:.2f} 过低, 建议从基础学起"})
            elif m < threshold:
                gaps.append({"skillId": skill, "gapType": "partial", "severity": "medium",
                             "description": f"掌握度 {m:.2f} 未达标(阈值 {threshold})"})
            elif days > stale_days and math.exp(-days / 30) < 0.7:
                gaps.append({"skillId": skill, "gapType": "forgotten", "severity": "medium",
                             "description": f"{days:.0f} 天未练习, 遗忘曲线估计保持率 {math.exp(-days/30):.2f}"})
        # misconception: 高频作答但正确率与掌握度显著背离
        for skill, s in summaries.items():
            if s["attempts"] >= 5 and s["correctRate"] > 0.6 and s["mastery"] < threshold:
                gaps.append({"skillId": skill, "gapType": "misconception", "severity": "high",
                             "description": f"答对率 {s['correctRate']:.2f} 但加权掌握度 {s['mastery']:.2f}, 疑似低阶认知代偿"})
        return gaps

    # ---------- 快照 ----------
    def snapshot(self, sid: str) -> dict:
        p = self.l3_profiles.get(sid, {})
        return {"studentId": sid,
                "l1EventCount": len(self.l1_events.get(sid, [])),
                "l2Summaries": len(self.l2_summaries.get(sid, {})),
                "l3ProfileVersion": p.get("version", 0),
                "profile": p}
