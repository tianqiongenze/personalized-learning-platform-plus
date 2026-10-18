"""KT 训练流水线: 加载合成教育数据 -> 训练 BKT(EM) + DKT(LSTM) -> 产出 checkpoints
  checkpoints/
    bkt_params.json        # BKT 参数
    dkt_model.pt           # DKT 权重
    meta.json              # KP 数/映射/学生掌握度索引(为已知学生离线预计算, 在线毫秒响应)
    mastery_index.json     # student_id -> {kp: mastery}(BKT+DKT 融合)
    eval_metrics.json      # AUC/Accuracy 验证指标
运行: python train_all.py --data <path/to/backend/data> [--epochs-dkt 2]
"""
import argparse

import json
import os
import sys

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from models.bkt import BKT  # noqa: E402
from models.dkt import DKTModel, train as train_dkt, encode_sequence, predict_mastery  # noqa: E402


def load_sequences(data_dir: str, n_skills: int) -> tuple[list[dict], list[dict]]:
    """题 -> KP(多 KP 全部关联); KP 数上限裁剪(模运算保持确定性)"""
    def conv(path: str) -> list[dict]:
        raw = json.load(open(path, encoding="utf-8"))
        out = []
        for s in raw:
            kps = [[q % n_skills for q in qs] for qs in
                   (QUEST2KP.get(str(q), []) for q in s["questions"])]
            # 无映射的题: 用题目 id 派生 KP, 保证序列可用
            kps = [[(q % n_skills)] if not kk else kk for q, kk in zip(s["questions"], kps)]
            out.append({"kps": kps, "answers": s["answers"], "difficulty": s["difficulty"],
                        "student_id": s["student_id"]})
        return out

    global QUEST2KP
    kg = json.load(open(os.path.join(data_dir, "kg_structure.json"), encoding="utf-8"))
    QUEST2KP = {q: [k % n_skills for k in kps] for q, kps in kg["question_to_kps"].items()}
    return conv(os.path.join(data_dir, "train.json")), conv(os.path.join(data_dir, "val.json"))


def auc(y_true: list[int], y_score: list[float]) -> float:
    """秩 AUC(O(n log n))"""
    pairs = sorted(zip(y_score, y_true))
    ranks = np.empty(len(pairs))
    i = 0
    while i < len(pairs):
        j = i
        while j < len(pairs) and pairs[j][0] == pairs[i][0]:
            j += 1
        ranks[i:j] = (i + j - 1) / 2 + 1
        i = j
    pos = sum(y for _, y in pairs)
    n_pos, n_neg = pos, len(pairs) - pos
    if n_pos == 0 or n_neg == 0:
        return 0.5
    sum_ranks = sum(r for r, (_, y) in zip(ranks, pairs) if y == 1)
    return (sum_ranks - n_pos * (n_pos + 1) / 2) / (n_pos * n_neg)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "checkpoints"))
    ap.add_argument("--n-skills", type=int, default=2000)
    ap.add_argument("--epochs-bkt", type=int, default=3)
    ap.add_argument("--epochs-dkt", type=int, default=2)
    ap.add_argument("--sample", type=int, default=2000, help="BKT 训练子样本(EM 加速)")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    train_seqs, val_seqs = load_sequences(args.data, args.n_skills)
    print(f"train={len(train_seqs)} val={len(val_seqs)} n_skills={args.n_skills}")

    # ---------- BKT ----------
    bkt = BKT(args.n_skills)
    sub = random_subset(train_seqs, args.sample)
    bkt.fit(sub, epochs=args.epochs_bkt)
    bkt.save(os.path.join(args.out, "bkt_params.json"))

    # ---------- DKT ----------
    dkt = train_dkt(train_seqs, args.n_skills, epochs=args.epochs_dkt)
    dkt.eval()
    torch.save(dkt.state_dict(), os.path.join(args.out, "dkt_model.pt"))

    # ---------- 验证指标(防泄漏评测) ----------
    # 口径(与 C2 修复后的训练目标一致): 训练监督为 y[t] = P(y_{t+1} | x_{1..t}),
    # 即时刻 t 的输入 x[:t+1] 不含 ans[t+1], 模型在 t 时刻的输出 p[t] 预测的是
    # 第 t+1 题的对错。因此评测必须用 p[t] 配对 ans[t+1]; 禁止用 p[t+1](其输入
    # x[:t+2] 已含 ans[t])配对 ans[t] —— 那是答案泄漏, AUC 虚高。
    dkt.eval()
    y_true, y_score = [], []
    with torch.no_grad():
        for seq in val_seqs[:500]:
            x = encode_sequence(seq, args.n_skills).unsqueeze(0)
            p = dkt(x)[0]  # (T, S)
            for t in range(len(seq["answers"]) - 1):
                nxt_ans = seq["answers"][t + 1]
                if nxt_ans not in (0, 1):
                    continue
                nxt = p[t]  # 输入仅 x[:t+1], 预测 t+1 题
                for kp in seq["kps"][t + 1]:
                    if kp < args.n_skills:
                        y_true.append(int(nxt_ans))
                        y_score.append(float(nxt[kp]))
    metrics = {
        "auc": round(auc(y_true, y_score), 4),
        "accuracy": round(float(np.mean(
            [1 if (s > 0.5) == (t == 1) else 0 for s, t in zip(y_score, y_true)])), 4),
        "n_predictions": len(y_true),
    }
    print("eval DKT:", metrics)

    # ---------- BKT 同口径防泄漏评测 ----------
    # BKT: 对每个 t, 用 x[:t+1](前 t+1 步作答)递推出的后验掌握度, 预测第 t+1 题
    # P(correct) = p_known*(1-pS) + (1-p_known)*pG(与 predict_correct 同式)。
    # 递增递推与"截断序列调 mastery"等价, 但 O(T) 而非 O(T^2)。
    bkt_true, bkt_score = [], []
    for seq in val_seqs[:500]:
        answers, kps_list = seq["answers"], seq["kps"]
        p_known: dict[int, float] = {}
        for t in range(len(answers) - 1):
            for kp in kps_list[t]:
                cur = p_known.get(kp, float(bkt.params["pL0"][kp]))
                p_known[kp], _ = bkt._step(
                    cur, int(answers[t]),
                    float(bkt.params["pL0"][kp]), float(bkt.params["pT"][kp]),
                    float(bkt.params["pG"][kp]), float(bkt.params["pS"][kp]))
            nxt_ans = answers[t + 1]
            if nxt_ans not in (0, 1):
                continue
            probs = []
            for kp in kps_list[t + 1]:
                if kp >= args.n_skills:
                    continue
                m = p_known.get(kp, float(bkt.params["pL0"][kp]))
                probs.append(m * (1 - float(bkt.params["pS"][kp]))
                             + (1 - m) * float(bkt.params["pG"][kp]))
            if probs:
                bkt_true.append(int(nxt_ans))
                bkt_score.append(float(np.mean(probs)))
    bkt_metrics = {
        "auc": round(auc(bkt_true, bkt_score), 4),
        "accuracy": round(float(np.mean(
            [1 if (s > 0.5) == (t == 1) else 0 for s, t in zip(bkt_score, bkt_true)])), 4),
        "n_predictions": len(bkt_true),
    }
    print("eval BKT:", bkt_metrics)
    metrics = {
        "dkt": metrics,
        "bkt": bkt_metrics,
        "protocol": "leakage-free: p[t] (input x[:t+1]) paired with ans[t+1], "
                    "matches training target P(y_{t+1} | x_{1..t})",
    }
    json.dump(metrics, open(os.path.join(args.out, "eval_metrics.json"), "w"), ensure_ascii=False)

    # ---------- 学生掌握度索引(在线毫秒响应, 融合 BKT+DKT) ----------
    mastery_index = {}
    for seq in train_seqs:
        b_m = bkt.mastery(seq)
        d_m = predict_mastery(dkt, seq, args.n_skills)
        fused = {kp: round(0.5 * b_m.get(kp, 0.4) + 0.5 * d_m.get(kp, 0.4), 4)
                 for kp in set(b_m) | set(d_m)}
        mastery_index[str(seq["student_id"])] = fused
    json.dump(mastery_index, open(os.path.join(args.out, "mastery_index.json"), "w"))
    json.dump({"n_skills": args.n_skills, "students": len(mastery_index)},
              open(os.path.join(args.out, "meta.json"), "w"))
    print("checkpoints written to", args.out)


def random_subset(seqs, n):
    if len(seqs) <= n:
        return seqs
    idx = np.random.choice(len(seqs), n, replace=False)
    return [seqs[i] for i in idx]


if __name__ == "__main__":
    main()
