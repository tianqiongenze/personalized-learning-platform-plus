"""合成学习序列生成器(可复现的离线评测数据)。

原训练数据在别的机器上生成(train.log 路径 D:\\...)未入库; 本脚本保证
train/eval 链路自包含可复现。按简单 BKT 真值生成 synthetic learner:
学生按能力 p_learn 演化掌握度, 作答 = Bernoulli(掌握 ? p_slip_correct : p_guess)。
输出: train.json / val.json / kg_structure.json(题目->KP 映射)。
运行: python gen_synthetic.py --out ./synthetic_data --n-train 800 --n-val 200 --n-skills 50
"""
from __future__ import annotations

import argparse
import json
import os
import random


def gen(out_dir: str, n_train: int, n_val: int, n_skills: int,
        n_questions: int = 400, seq_len: tuple[int, int] = (8, 30), seed: int = 42):
    rng = random.Random(seed)
    os.makedirs(out_dir, exist_ok=True)

    # 题目 -> KP 映射(每题 1-2 个 KP)
    q2kp = {str(q): sorted(rng.sample(range(n_skills), rng.choice([1, 1, 2])))
            for q in range(n_questions)}
    json.dump({"question_to_kps": q2kp},
              open(os.path.join(out_dir, "kg_structure.json"), "w"))

    def gen_split(n: int, name: str):
        rows = []
        for sid in range(n):
            ability = rng.random()  # 学生能力
            mastery = [rng.random() * 0.3 for _ in range(n_skills)]
            L = rng.randint(*seq_len)
            questions, answers, difficulties = [], [], []
            for _ in range(L):
                q = rng.randrange(n_questions)
                kps = q2kp[str(q)]
                # 掌握度按能力渐进演化
                for kp in kps:
                    mastery[kp] = min(1.0, mastery[kp] + 0.05 * (0.3 + ability))
                p_correct = max(0.15, min(0.95,
                    max(mastery[kp] for kp in kps) * 0.7 + ability * 0.3))
                ans = 1 if rng.random() < p_correct else 0
                questions.append(q)
                answers.append(ans)
                difficulties.append(rng.randint(1, 5))
            rows.append({"student_id": f"s-{name}-{sid}", "questions": questions,
                         "answers": answers, "difficulty": difficulties})
        json.dump(rows, open(os.path.join(out_dir, f"{name}.json"), "w"))
        print(f"{name}: {len(rows)} sequences")

    gen_split(n_train, "train")
    gen_split(n_val, "val")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="./synthetic_data")
    ap.add_argument("--n-train", type=int, default=800)
    ap.add_argument("--n-val", type=int, default=200)
    ap.add_argument("--n-skills", type=int, default=50)
    args = ap.parse_args()
    gen(args.out, args.n_train, args.n_val, args.n_skills)
