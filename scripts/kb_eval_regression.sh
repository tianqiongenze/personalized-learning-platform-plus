#!/usr/bin/env bash
# kb_eval_regression.sh — 在线 RAG 检索质量回归(DeepTutor kb eval 落地)
# 标注集: evals/rag_retrieval_eval.jsonl({query, relevant_ids} → {query, goldIds})
# 通道: 标注集 base64 经 kubectl exec 注入 agent-service pod, 由 pod 内 python 调
#       vector-reranker /internal/eval(真实 Milvus 向量召回, pod DNS 可达)
# 门禁: Recall@5>=0.8 且 MRR>=0.6 且 nDCG@5>=0.75(与 evals/run_eval.py 口径一致)
set -euo pipefail
cd "$(dirname "$0")/.."

SSH_HOST="${SSH_HOST:-10.167.2.175}"
SSH_OPTS="-o BatchMode=yes -o ConnectTimeout=8"
NS="${NS:-qianrenqianmian}"
RERANKER="${RERANKER:-http://vector-reranker.infra.svc.cluster.local:8090/internal/eval}"
TOPK="${TOPK:-5}"
OUT="${OUT:-report/kb-eval-online-$(date +%Y%m%d-%H%M%S).json}"

python3 - "$TOPK" <<'PY' > /tmp/kb_eval_payload.json
import json, sys
items = []
with open("evals/rag_retrieval_eval.jsonl", encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if line:
            it = json.loads(line)
            if it.get("relevant_ids"):
                items.append({"query": it["query"], "goldIds": it["relevant_ids"]})
print(json.dumps({"items": items, "topK": int(sys.argv[1])}, ensure_ascii=False))
PY

n=$(python3 -c 'import json;print(len(json.load(open("/tmp/kb_eval_payload.json"))["items"]))')
B64=$(base64 < /tmp/kb_eval_payload.json | tr -d '\n')
echo "[kb-eval] 在线评估 $n 条标注(topK=$TOPK) -> $RERANKER (via agent-service pod)"

ssh $SSH_OPTS "$SSH_HOST" "kubectl -n $NS exec deploy/agent-service -- python -c \"
import urllib.request, json, base64, sys
payload = base64.b64decode('$B64')
req = urllib.request.Request('$RERANKER', data=payload, headers={'Content-Type':'application/json'}, method='POST')
try:
    print(json.dumps(json.load(urllib.request.urlopen(req, timeout=300))))
except Exception as e:
    print(json.dumps({'error': str(e)})); sys.exit(0)
\"" 2>/dev/null > "$OUT"

python3 - "$OUT" <<'PY'
import json, sys
r = json.load(open(sys.argv[1]))
if "error" in r:
    print("FAIL: eval 端点错误:", r["error"]); sys.exit(1)
p = r["passThreshold"]
checks = [("recallAtK", r["recallAtK"], p["recallAtK"]),
          ("mrr", r["mrr"], p["mrr"]),
          ("ndcgAtK", r["ndcgAtK"], p["ndcgAtK"])]
ok = True
print(f"[kb-eval] n={r['n']} topK={r['topK']}")
for name, v, th in checks:
    passed = v >= th
    ok &= passed
    print(f"  {name}: {v} (阈值 {th}) {'PASS' if passed else 'FAIL'}")
print("[kb-eval]", "总体: PASS" if ok and r["passed"] else "总体: FAIL")
sys.exit(0 if ok and r["passed"] else 1)
PY
echo "[kb-eval] 明细: $OUT"
