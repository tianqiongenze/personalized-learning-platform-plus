"""BKT(贝叶斯知识追踪) —— 经典 HMM 参数学习(EM/_bw 网格), 纯 numpy, 无 GPU 依赖。
每个知识节点(KP)一组参数: P(L0)初始掌握, P(T)学习转移, P(G)猜对, P(S)失误。
多 KP 题目: 作答同时更新所涉及 KP(条件独立假设, 教育领域标准做法)。
"""
from __future__ import annotations

import json
import math
from collections import defaultdict
from math import inf

import numpy as np


class BKT:
    def __init__(self, n_skills: int):
        self.n_skills = n_skills
        # 参数空间用网格 EM 近似(稳、快、可解释), 初始值取文献常用区间
        self.params = {
            "pL0": np.full(n_skills, 0.4),
            "pT": np.full(n_skills, 0.15),
            "pG": np.full(n_skills, 0.2),
            "pS": np.full(n_skills, 0.1),
        }

    # ---------- 单 KP 前向更新 ----------
    @staticmethod
    def _step(p_known: float, correct: int, pL0: float, pT: float, pG: float, pS: float) -> tuple[float, float]:
        # 观测更新(贝叶斯)
        if correct:
            num = p_known * (1 - pS)
            den = num + (1 - p_known) * pG
        else:
            num = p_known * pS
            den = num + (1 - p_known) * (1 - pG)
        post = num / den if den > 0 else p_known
        # 学习转移
        post = post + (1 - post) * pT
        return min(max(post, 1e-6), 1 - 1e-6), (post if den > 0 else p_known)

    def fit(self, sequences: list[dict], epochs: int = 5, lr: float = 0.05):
        """序列化 EM: 条件独立假设下, 每 KP 的对数似然可独立计算 —— 只扫含该 KP 的步骤。
        预建索引 skill -> [(ans, ...)], 使网格坐标上升从 O(S·G·全量) 降为 O(S·G·每技能步数)。
        """
        # 预建索引: skill -> list of per-student step-answers
        idx: dict[int, list[list[int]]] = defaultdict(list)
        for seq in sequences:
            per_skill: dict[int, list[int]] = defaultdict(list)
            for kps, ans in zip(seq["kps"], seq["answers"]):
                for kp in kps:
                    per_skill[kp].append(int(ans))
            for kp, answers in per_skill.items():
                idx[kp].append(answers)

        grid = np.arange(0.05, 0.95, 0.1)
        for epoch in range(epochs):
            for key in ("pL0", "pT", "pG", "pS"):
                for i in range(self.n_skills):
                    student_seqs = idx.get(i)
                    if not student_seqs:
                        continue
                    best_v, best_ll = self.params[key][i], -inf
                    for v in grid:
                        old = self.params[key][i]
                        self.params[key][i] = v
                        ll = self._skill_ll(student_seqs, i)
                        if ll > best_ll:
                            best_v, best_ll = v, ll
                        self.params[key][i] = old
                    self.params[key][i] = best_v
            print(f"[BKT] epoch {epoch + 1}/{epochs} done")
        return self

    def _skill_ll(self, student_seqs: list[list[int]], i: int) -> float:
        pL0, pT = self.params["pL0"][i], self.params["pT"][i]
        pG, pS = self.params["pG"][i], self.params["pS"][i]
        ll = 0.0
        for answers in student_seqs:
            cur = pL0
            for ans in answers:
                if ans:
                    den = cur * (1 - pS) + (1 - cur) * pG
                    obs = cur * (1 - pS) + (1 - cur) * pG
                    post = (cur * (1 - pS)) / den if den > 0 else cur
                else:
                    den = cur * pS + (1 - cur) * (1 - pG)
                    obs = cur * pS + (1 - cur) * (1 - pG)
                    post = (cur * pS) / den if den > 0 else cur
                if den > 0:
                    ll += math.log(max(obs, 1e-12))
                cur = post + (1 - post) * pT
        return ll

    def _loglik(self, sequences: list[dict], skill_filter: int | None = None) -> float:
        ll = 0.0
        for seq in sequences:
            p_known: dict[int, float] = {}
            for kps, ans in zip(seq["kps"], seq["answers"]):
                for kp in kps:
                    if skill_filter is not None and kp != skill_filter:
                        continue
                    i = kp
                    pL0, pT = self.params["pL0"][i], self.params["pT"][i]
                    pG, pS = self.params["pG"][i], self.params["pS"][i]
                    cur = p_known.get(i, pL0)
                    if ans:
                        den = cur * (1 - pS) + (1 - cur) * pG
                        post = (cur * (1 - pS)) / den if den > 0 else cur
                    else:
                        den = cur * pS + (1 - cur) * (1 - pG)
                        post = (cur * pS) / den if den > 0 else cur
                    p_known[i] = post + (1 - post) * pT
                    if den > 0:
                        obs_p = (cur * (1 - pS) + (1 - cur) * pG) if ans else (cur * pS + (1 - cur) * (1 - pG))
                        ll += math.log(max(obs_p, 1e-12))
        return ll

    # ---------- 推理 ----------
    def mastery(self, seq: dict) -> dict[int, float]:
        """返回每 KP 的最终掌握度"""
        p_known: dict[int, float] = {}
        for kps, ans in zip(seq["kps"], seq["answers"]):
            for kp in kps:
                cur = p_known.get(kp, self.params["pL0"][kp])
                p_known[kp], _ = self._step(
                    cur, ans,
                    self.params["pL0"][kp], self.params["pT"][kp],
                    self.params["pG"][kp], self.params["pS"][kp])
        return p_known

    def predict_correct(self, seq: dict, next_kps: list[int]) -> float:
        """预测下一题答对概率(含未接触 KP 的 P(L0) 先验)"""
        m = self.mastery(seq)
        probs = []
        for kp in next_kps:
            i = min(kp, self.n_skills - 1)
            p_known = m.get(i, self.params["pL0"][i])
            probs.append(p_known * (1 - self.params["pS"][i]) + (1 - p_known) * self.params["pG"][i])
        return float(np.mean(probs)) if probs else 0.5

    def save(self, path: str):
        json.dump({"n_skills": self.n_skills,
                   "params": {k: v.tolist() for k, v in self.params.items()}},
                  open(path, "w"))

    @classmethod
    def load(cls, path: str) -> "BKT":
        d = json.load(open(path))
        b = cls(d["n_skills"])
        b.params = {k: np.array(v) for k, v in d["params"].items()}
        return b
