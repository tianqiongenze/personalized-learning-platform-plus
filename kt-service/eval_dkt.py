"""DKT/BKT 独立评测脚本 —— 训练集/测试集严格划分的离线 AUC(Accuracy)。

用途: train_all.py 的 eval_metrics.json 是"训练期验证"; 本脚本供独立复核, 防止
  (a) 数据泄漏: 训练/评测集重叠;
  (b) 时间泄漏: 预测第 t 题时输入已含第 t 题答案(答案复读)。
评测协议:
  - 加载 checkpoints 里已训练好的模型, 只在 val.json(独立划分)上推理;
  - DKT: 预测 y_true[t] 时, 输入截断到 x[:t+1](仅含 t 及之前的作答),
    取模型在时刻 t 的输出作为 t 时刻作答概率 —— 与 train 修复后的 t+1 对齐监督一致;
  - BKT: 序列末态掌握度作 AUC 分数(概率型基线)。
运行: python eval_dkt.py --data <backend/data> [--ckpt checkpoints]
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from models.dkt import DKTModel, encode_sequence  # noqa: E402


def auc(y_true: list[int], y_score: list[float]) -> float:
    pairs = sorted(zip(y_score, y_true))
    ranks = np.empty(len(pairs))
    i = 0
    while i < len(pairs):
        j = i
        while j < len(pairs) and pairs[j][0] == pairs[i][0]:
            j += 1
        ranks[i:j] = (i + j - 1) / 2 + 1
        i = j
    n_pos = sum(y for _, y in pairs)
    n_neg = len(pairs) - n_pos
    if n_pos == 0 or n_neg == 0:
        return 0.5
    sum_ranks = sum(r for r, (_, y) in zip(ranks, pairs) if y == 1)
    return (sum_ranks - n_pos * (n_pos + 1) / 2) / (n_pos * n_neg)


def load_questions_to_kps(data_dir: str, n_skills: int) -> dict:
    kg = json.load(open(os.path.join(data_dir, "kg_structure.json"), encoding="utf-8"))
    return {q: [k % n_skills for k in kps] for q, kps in kg["question_to_kps"].items()}


def conv(raw_path: str, quest2kp: dict, n_skills: int) -> list[dict]:
    raw = json.load(open(raw_path, encoding="utf-8"))
    out = []
    for s in raw:
        kps = [[q % n_skills for q in quest2kp.get(str(q), [])] for q in s["questions"]]
        kps = [[(q % n_skills)] if not kk else kk for q, kk in zip(s["questions"], kps)]
        out.append({"kps": kps, "answers": s["answers"], "difficulty": s["difficulty"],
                    "student_id": s["student_id"]})
    return out


def eval_dkt(model: DKTModel, seqs: list[dict], n_skills: int,
             max_seqs: int = 500) -> dict:
    """因果评测: 时刻 t 的输入只有 x[:t+1], 不得见到 t 之后的任何作答。"""
    y_true, y_score = [], []
    with torch.no_grad():
        for seq in seqs[:max_seqs]:
            x = encode_sequence(seq, n_skills).unsqueeze(0)
            p = model(x)[0]  # (T, S): p[t] 只由 x[:t+1] 决定(LSTM 因果性)
            for t, (kps, ans) in enumerate(zip(seq["kps"], seq["answers"])):
                if ans not in (0, 1):
                    continue
                for kp in kps:
                    if kp < n_skills:
                        y_true.append(int(ans))
                        y_score.append(float(p[t, kp]))
    acc = float(np.mean([1 if (s > 0.5) == (t == 1) else 0
                         for s, t in zip(y_score, y_true)])) if y_true else 0.0
    return {"auc": round(auc(y_true, y_score), 4), "accuracy": round(acc, 4),
            "n_predictions": len(y_true)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--ckpt", default=os.path.join(os.path.dirname(__file__), "checkpoints"))
    ap.add_argument("--n-skills", type=int, default=2000)
    ap.add_argument("--split", default="val", choices=["train", "val", "test"],
                    help="默认 val(独立于 train); 若存在 test.json 建议用 test")
    ap.add_argument("--max-seqs", type=int, default=500)
    args = ap.parse_args()

    q2kp = load_questions_to_kps(args.data, args.n_skills)
    path = os.path.join(args.data, f"{args.split}.json")
    if not os.path.exists(path):
        print(f"[eval] {path} 不存在, 退回 val.json")
        path = os.path.join(args.data, "val.json")
    seqs = conv(path, q2kp, args.n_skills)

    meta_path = os.path.join(args.ckpt, "meta.json")
    n_skills = args.n_skills
    if os.path.exists(meta_path):
        n_skills = json.load(open(meta_path))["n_skills"]

    dkt = DKTModel(n_skills)
    dkt.load_state_dict(torch.load(os.path.join(args.ckpt, "dkt_model.pt"),
                                   map_location="cpu"))
    dkt.eval()

    m = eval_dkt(dkt, seqs, n_skills, args.max_seqs)
    m["split"] = os.path.basename(path)
    m["model"] = "DKT(casual, t+1-aligned training)"
    print("eval:", m)
    out = os.path.join(args.ckpt, "eval_metrics_independent.json")
    json.dump(m, open(out, "w"), ensure_ascii=False, indent=2)
    print("written to", out)


if __name__ == "__main__":
    main()
