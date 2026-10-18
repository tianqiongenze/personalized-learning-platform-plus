"""DKT(深度知识追踪) —— LSTM, 特征 = KP one-hot × 作答对错 + 难度嵌入。
KP 数远小于题数(数千级), 比 50000 维题目 one-hot 更适合 CPU 训练与迁移。
"""
from __future__ import annotations

import numpy as np
import torch
import torch.nn as nn


class DKTModel(nn.Module):
    def __init__(self, n_skills: int, hidden: int = 128, difficulty_levels: int = 5):
        super().__init__()
        self.n_skills = n_skills
        # 输入: [对错 one-hot(n_skills*2) + 难度 one-hot(difficulty_levels)] -> LSTM -> 每 KP 答对概率
        self.lstm = nn.LSTM(n_skills * 2 + difficulty_levels, hidden, batch_first=True)
        self.out = nn.Linear(hidden, n_skills)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        h, _ = self.lstm(x)
        return torch.sigmoid(self.out(h))  # (B, T, n_skills)


def encode_sequence(seq: dict, n_skills: int, difficulty_levels: int = 5) -> torch.Tensor:
    T = len(seq["answers"])
    x = np.zeros((T, n_skills * 2 + difficulty_levels), dtype=np.float32)
    for t, (kps, ans, diff) in enumerate(zip(seq["kps"], seq["answers"], seq["difficulty"])):
        d = min(max(diff, 1), difficulty_levels) - 1
        x[t, n_skills * 2 + d] = 1.0
        for kp in kps:
            if kp < n_skills:
                x[t, kp + (n_skills if ans else 0)] = 1.0
    return torch.from_numpy(x)


def target_matrix(seq: dict, n_skills: int) -> torch.Tensor:
    """t+1 时刻各 KP 的真实对错(供下一时刻预测监督)"""
    T = len(seq["answers"])
    y = np.full((T, n_skills), -1.0, dtype=np.float32)
    for t, (kps, ans) in enumerate(zip(seq["kps"], seq["answers"])):
        for kp in kps:
            if kp < n_skills:
                y[t, kp] = float(ans)
    return torch.from_numpy(y)


def train(sequences: list[dict], n_skills: int, epochs: int = 3, batch: int = 64, lr: float = 1e-3) -> DKTModel:
    model = DKTModel(n_skills)
    opt = torch.optim.Adam(model.parameters(), lr=lr)
    crit = nn.BCELoss()
    idx = np.random.permutation(len(sequences))
    for epoch in range(epochs):
        total, n = 0.0, 0
        for b in range(0, len(idx), batch):
            batch_seqs = [sequences[i] for i in idx[b: b + batch]]
            # 同批长度对齐: 截断到批内最短(快速 CPU 版)
            tmin = min(len(s["answers"]) for s in batch_seqs)
            if tmin < 2:
                continue
            xs = torch.stack([encode_sequence(s, n_skills)[:tmin] for s in batch_seqs])
            ys = torch.stack([target_matrix(s, n_skills)[:tmin] for s in batch_seqs])
            preds = model(xs)
            mask = ys >= 0
            if mask.sum() == 0:
                continue
            loss = crit(preds[mask], ys[mask].clamp(0, 1))
            opt.zero_grad()
            loss.backward()
            opt.step()
            total += float(loss) * len(batch_seqs)
            n += len(batch_seqs)
        print(f"[DKT] epoch {epoch + 1}/{epochs} loss={total / max(n, 1):.4f}")
    return model


def predict_mastery(model: DKTModel, seq: dict, n_skills: int) -> dict[int, float]:
    with torch.no_grad():
        x = encode_sequence(seq, n_skills).unsqueeze(0)
        p = model(x)[0, -1]  # 最后时刻各 KP 掌握度
        return {i: float(p[i]) for i in range(n_skills)}
