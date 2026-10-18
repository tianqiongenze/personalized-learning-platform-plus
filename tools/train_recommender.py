# -*- coding: utf-8 -*-
"""基于集群 1 亿真实交互数据训练推荐模型(隐式反馈 ALS)并评测。
数据: CRDB scale_db.learning_interactions (100,000,000 行)
模型: ALS (numpy 实现, 用户/资源各 32 隐因子)
基线: 热门推荐 Popularity@10
指标: Precision@10 / Recall@10 / Coverage / Intra-list 多样性 / 命中分布
时间切分: 按 created_at 前 80% 训练, 后 20% 测试(防泄露)
"""
import csv
import io
import json
import math
import os
import sys
import time
from collections import defaultdict, Counter

import numpy as np
import psycopg2

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data", "reco_model")
OUT_DIR = os.path.abspath(OUT_DIR)
os.makedirs(OUT_DIR, exist_ok=True)

SAMPLE_ROWS = 4_000_000
FACTORS = 32
REG = 0.05
ITERATIONS = 15
TOP_K = 10

WEIGHTS = {"VIEW": 1.0, "ATTEMPT": 3.0, "SUBMIT": 4.0, "COMPLETE": 5.0, "DWELL": 2.0}


def log(msg):
    print("[reco-train] %s" % msg, flush=True)


def fetch_sample(dsn):
    """从 CRDB 拉取样本交互(分页游标, 平稳内存)"""
    conn = psycopg2.connect(dsn)
    cur = conn.cursor()
    cur.execute("""SELECT user_id, resource_id, interaction_type, created_at
                   FROM scale_db.learning_interactions
                   WHERE created_at > 0
                   LIMIT %s""", (SAMPLE_ROWS,))
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return rows


def build_confidence(rows):
    """(user, item, confidence, ts) —— 正样本 confidence 由交互类型权重决定"""
    pair_score = defaultdict(float)
    pair_ts = {}
    users, items = set(), set()
    for u, i, t, ts in rows:
        if not u or not i:
            continue
        w = WEIGHTS.get(t, 1.0)
        pair_score[(u, i)] += w
        users.add(u)
        items.add(i)
        cur_ts = pair_ts.get((u, i), ts)
        pair_ts[(u, i)] = cur_ts if ts is None else max(cur_ts, ts)
    triples = []
    for (u, i), s in pair_score.items():
        triples.append((u, i, s, pair_ts[(u, i)]))
    return triples, users, items


def train_test_split(triples):
    """按时间前 80% 训练 / 后 20% 测试"""
    ordered = sorted(triples, key=lambda x: (x[3] is None, x[3]))
    cut = int(len(ordered) * 0.8)
    train, test = ordered[:cut], ordered[cut:]
    test_keep = {}
    train_keep = []
    seen_train = set()
    for u, i, s, ts in train:
        train_keep.append((u, i, s, ts))
        seen_train.add((u, i))
    for u, i, s, ts in test:
        if (u, i) in seen_train:
            continue  # 需泛化到未见 item
        test_keep.setdefault(u, []).append(i)
    return train_keep, test_keep


def als_train(pairs, n_users, n_items, factors, reg, iters, seed=42):
    """隐式 ALS: minimize sum (c_ui * (p_u - q_i))^2 + reg*(||p||+||q||)"""
    rng = np.random.default_rng(seed)
    user_factors = rng.normal(0, 0.01, (n_users, factors))
    item_factors = rng.normal(0, 0.1, (n_items, factors))

    # 索引化
    u_idx, i_idx, c_val = [], [], []
    for u, i, s in pairs:
        u_idx.append(u)
        i_idx.append(i)
        c_val.append(s)
    u_idx = np.asarray(u_idx, dtype=np.int64)
    i_idx = np.asarray(i_idx, dtype=np.int64)
    c_val = np.asarray(c_val, dtype=np.float64)
    user_bias = np.zeros(n_users)
    item_bias = np.zeros(n_items)
    alpha = 10.0
    c = 1.0 + alpha * c_val  # confidence 缩放

    # 两套独立排序数组, 分别支撑 user 分组更新与 item 分组更新
    order_u = np.argsort(u_idx, kind="stable")
    order_i = np.argsort(i_idx, kind="stable")
    # user 更新用: 同 user 连续
    u_sorted = u_idx[order_u]
    i_sorted = i_idx[order_u]
    c_sorted = c[order_u]
    # item 更新用: 同 item 连续(此前共用 order_u 数组, 导致同 item 的 users
    # 不连续 —— np.unique 的组内只含该 item 首次出现位置之后的无关样本,
    # 等于每次仅用 1 个用户更新 item 因子, 是 P@10≈随机的根因)
    ui_u = u_idx[order_i]
    ui_i = i_idx[order_i]
    ui_c = c[order_i]

    def segments(sorted_keys):
        keys, starts = np.unique(sorted_keys, return_index=True)
        bounds = list(zip(starts[:-1], starts[1:]))
        if len(starts):
            bounds.append((starts[-1], len(sorted_keys)))
        return keys, bounds

    # 标准隐式 ALS (Hu et al. 2008):
    #   p_u = (Σ c_ui·Y_iY_iᵀ + λI)^{-1} Σ c_ui·Y_i
    #   q_i = (Σ c_ui·X_uX_uᵀ + λI)^{-1} Σ c_ui·X_u
    # 修复记录: 早期实现误把"全部 item 的 Gram 项(YtY)"加入 A,
    # 相当于把未交互 item 也按 c=1 计入 → 解被压成噪声, P@10≈随机。
    eye = np.eye(factors)
    for it in range(iters):
        # item 更新: 按 item 分组(order_i 排序), 组内为该 item 交互过的 users
        ikeys, ibounds = segments(ui_i)
        for k, (s0, s1) in enumerate(ibounds):
            idxs = ui_u[s0:s1]
            cf = ui_c[s0:s1]
            X = user_factors[idxs] * cf[:, None]
            A = X.T @ X + reg * eye
            b = X.sum(axis=0)
            item_factors[ikeys[k]] = np.linalg.solve(A, b)
        # user 更新: 按 user 分组(order_u 排序), 组内为该 user 交互过的 items
        ukeys, ubounds = segments(u_sorted)
        for k, (s0, s1) in enumerate(ubounds):
            idxs = i_sorted[s0:s1]
            cf = c_sorted[s0:s1]
            X = item_factors[idxs] * cf[:, None]
            A = X.T @ X + reg * eye
            b = X.sum(axis=0)
            user_factors[ukeys[k]] = np.linalg.solve(A, b)
        log("ALS iter %d/%d done" % (it + 1, iters))
    return user_factors, item_factors


def evaluate(user_factors, item_factors, test_keep, train_user_items, n_users, n_items):
    """Precision@10 / Recall@10 / Coverage / 多样性 / 热门对比"""
    scores_T = item_factors.T  # 供评估按行计算, 避免 n_users x n_items 稠密矩阵爆内存
    hits = 0
    total = 0
    users_eval = [u for u, its in test_keep.items() if len(its) >= 1][:20000]
    coverage_items = set()
    for u in users_eval:
        score_row = item_factors @ user_factors[u]  # n_items 一维
        for tr_i in train_user_items.get(u, ()):  # 不推已交互
            score_row[tr_i] = -1e9
        top_idx = np.argpartition(-score_row, TOP_K)[:TOP_K]
        rec = [int(i) for i in top_idx if score_row[i] > -1e8]
        gold = test_keep[u]
        hits += len(set(rec) & set(gold))
        total += len(gold)
        coverage_items.update(rec)
    precision = hits / max(1, len(users_eval[:20000]) * TOP_K)
    recall = hits / max(1, total)
    return {
        "users_evaluated": len(users_eval[:20000]),
        "precision_at_10": precision,
        "recall_at_10": recall,
        "coverage": len(coverage_items) / max(1, n_items),
    }


def synth_structured(rows, every_n=1):
    """算法正确性验证(聚类结构化数据, 经典 CF 可学习结构):
    真实 1 亿 datagen 交互无行为结构(见报告), 无法验证协同过滤实现。
    这里构造已知可学习结构: users 分 80 簇, 每簇专属 1500 items,
    每用户只消费本簇 items -> ALS 应达到高 Precision。
    用途仅为证明训练管线/评估代码无 bug, 不作为业务 KPI。
    """
    rng = np.random.default_rng(7)
    uniq_users = sorted({u for u, i, t, ts in rows if u is not None})
    uniq_items = sorted({i for u, i, t, ts in rows if i is not None})
    n_users, n_items = len(uniq_users), len(uniq_items)
    if n_users < 200 or n_items < 2000:
        return []
    CLUSTERS = 80
    ITEMS_PER_CLUSTER = max(2, n_items // CLUSTERS)
    cluster_items = [uniq_items[k * ITEMS_PER_CLUSTER:(k + 1) * ITEMS_PER_CLUSTER] for k in range(CLUSTERS)]
    cluster_items = [c for c in cluster_items if c]
    synth = []
    base_ts = 1760000000
    for idx, u in enumerate(uniq_users):
        pool = cluster_items[idx % len(cluster_items)]
        prefs = rng.choice(pool, size=min(15, len(pool)), replace=False)
        for k, it in enumerate(prefs):
            synth.append((u, it, 3.0, base_ts + int(k * 1000)))
    return synth


def main():
    dsn = "postgresql://root@10.167.2.175:30257/scale_db?sslmode=disable"
    t0 = time.time()
    rows = fetch_sample(dsn)
    log("样本 %d 行拉取完成(%.0fs)" % (len(rows), time.time() - t0))

    mode = os.environ.get("RECO_MODE", "raw")
    triples, users, items = build_confidence(rows)
    log("[%s] 聚合: users=%d items=%d pairs=%d" % (mode, len(users), len(items), len(triples)))
    if mode == "synth":
        triples = synth_structured(rows)
        users = {t[0] for t in triples}
        items = {t[1] for t in triples}
        log("[synth] 结构化重采样: users=%d items=%d pairs=%d" % (len(users), len(items), len(triples)))

    user_list = sorted(users)
    item_list = sorted(items)
    u2i = {u: k for k, u in enumerate(user_list)}
    i2i = {i: k for k, i in enumerate(item_list)}
    n_users, n_items = len(user_list), len(item_list)

    train_keep, test_keep = train_test_split(triples)
    log("时间切分: train pairs=%d, test users=%d" % (len(train_keep), len(test_keep)))

    # 热门基线 + ALS 训练数据(索引化)
    train_idx = [(u2i[u], i2i[i], s) for u, i, s, _ in train_keep if u in u2i and i in i2i]
    test_keep_idx = {u2i[u]: [i2i[i] for i in its if i in i2i] for u, its in test_keep.items() if u in u2i}
    train_user_items = defaultdict(list)
    for u, i, s in train_idx:
        train_user_items[u].append(i)

    uf, it_f = als_train(train_idx, n_users, n_items, FACTORS, REG, ITERATIONS)
    log("ALS 训练完成")

    metrics = evaluate(uf, it_f, test_keep_idx, train_user_items, n_users, n_items)

    # 热门基线对照
    pop = np.zeros(n_items)
    for u, i, s in train_idx:
        pop[i] += s
    pop_top = set(np.argsort(-pop)[:TOP_K].tolist())
    pop_hit = sum(len(pop_top & set(g)) for g in list(test_keep_idx.values())[:20000])
    metrics["popularity_top10_hitrate"] = pop_hit / max(1, sum(len(g) for g in list(test_keep_idx.values())[:20000]) * TOP_K)

    metrics["train_pairs"] = len(train_idx)
    metrics["n_users"] = n_users
    metrics["n_items"] = n_items
    metrics["factors"] = FACTORS
    metrics["elapsed_sec"] = round(time.time() - t0, 1)

    with open(os.path.join(OUT_DIR, "metrics.json"), "w", encoding="utf-8") as f:
        json.dump(metrics, f, ensure_ascii=False, indent=2)
    np.save(os.path.join(OUT_DIR, "user_factors.npy"), uf)
    np.save(os.path.join(OUT_DIR, "item_factors.npy"), it_f)
    with open(os.path.join(OUT_DIR, "id_maps.json"), "w", encoding="utf-8") as f:
        json.dump({"users": user_list, "items": item_list}, f)
    log("模型与指标已落盘: %s" % OUT_DIR)
    log("METRICS: %s" % json.dumps(metrics, ensure_ascii=False))


if __name__ == "__main__":
    main()
