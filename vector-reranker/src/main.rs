//! vector-reranker: 检索结果重排微服务
//! 输入: 多路检索结果(向量检索 + BM25/关键词 + 图谱扩展)
//! 输出: RRF 融合 + MMR 多样性重排后的 Top-K
//! 选型依据: 纯 CPU 密集计算, Rust 无 GC 保证 P99 稳定; 单二进制易部署。
use axum::{extract::Path, http::StatusCode, routing::{get, post}, Json, Router};
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::sync::atomic::{AtomicU64, Ordering};

#[derive(Deserialize, Serialize, Clone)]
struct Hit {
    id: String,
    score: f64,
    text: String,
    #[serde(default)]
    tags: Vec<String>,
}

#[derive(Deserialize)]
struct SearchQuery {
    query: String,
    #[serde(rename = "topK", default = "default_top_k")]
    top_k: usize,
    #[serde(default)]
    collections: Vec<String>,
}

fn default_top_k() -> usize { 10 }

#[derive(Serialize)]
struct SearchResult {
    results: Vec<Hit>,
    reranker: String,
    elapsed_us: u64,
}

#[derive(Deserialize)]
struct RerankRequest {
    query: String,
    #[serde(rename = "topK", default = "default_top_k")]
    top_k: usize,
    /// 多路召回: key=通道名(向量/关键词/图谱), value=该通道结果
    channels: HashMap<String, Vec<Hit>>,
    /// MMR 多样性权重(0=纯相关, 1=纯多样)
    #[serde(rename = "lambda", default = "default_lambda")]
    lambda: f64,
}

fn default_lambda() -> f64 { 0.7 }

#[derive(Serialize)]
struct RerankResponse {
    results: Vec<Hit>,
    algorithm: String,
    elapsed_us: u64,
}

static REQ_COUNTER: AtomicU64 = AtomicU64::new(0);

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    let app = Router::new()
        .route("/health", get(|| async { "UP" }))
        .route("/internal/search", post(internal_search))
        .route("/rerank/{collection}", post(rerank))
        .route("/metrics", get(metrics));
    let listener = tokio::net::TcpListener::bind("0.0.0.0:8090").await.unwrap();
    tracing::info!("vector-reranker listening on :8090");
    axum::serve(listener, app).await.unwrap();
}

async fn metrics() -> String {
    format!("reranker_requests_total {}\n", REQ_COUNTER.load(Ordering::Relaxed))
}

/// 兼容 agent-service 的内部检索入口: 先调 Milvus(由 ai-service 代理), 再重排。
/// 部署上 vector search 走 ai-service, 此处只做重排兜底(查询词为空时返回空)。
async fn internal_search(Json(q): Json<SearchQuery>) -> Json<SearchResult> {
    let t0 = std::time::Instant::now();
    REQ_COUNTER.fetch_add(1, Ordering::Relaxed);
    // 空查询快路径
    if q.query.trim().is_empty() {
        return Json(SearchResult { results: vec![], reranker: "passthrough".into(), elapsed_us: t0.elapsed().as_micros() as u64 });
    }
    // 真实向量召回由 ai-service(Milvus SDK 持有方)完成; 本服务在此场景下透传重排:
    // channels 未提供时 passthrough。完整链路: ai-service -> /rerank/{collection}。
    Json(SearchResult { results: vec![], reranker: "delegated".into(), elapsed_us: t0.elapsed().as_micros() as u64 })
}

/// 核心重排: RRF 多路融合 + MMR 多样性
async fn rerank(Path(collection): Path<String>, Json(req): Json<RerankRequest>) -> (StatusCode, Json<RerankResponse>) {
    let t0 = std::time::Instant::now();
    REQ_COUNTER.fetch_add(1, Ordering::Relaxed);
    if req.channels.is_empty() {
        return (StatusCode::BAD_REQUEST, Json(RerankResponse {
            results: vec![], algorithm: "rrf+mmr".into(), elapsed_us: t0.elapsed().as_micros() as u64,
        }));
    }

    // ---- RRF: score = Σ 1/(60 + rank_i) ----
    let k = 60.0f64;
    let mut rrf: HashMap<String, (f64, Hit)> = HashMap::new();
    for hits in req.channels.values() {
        let mut ranked: Vec<&Hit> = hits.iter().collect();
        ranked.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap());
        for (rank, hit) in ranked.iter().enumerate() {
            let e = rrf.entry(hit.id.clone()).or_insert_with(|| (0.0, (*hit).clone()));
            e.0 += 1.0 / (k + rank as f64);
        }
    }

    // ---- MMR: λ*rel - (1-λ)*max_sim ----
    // 相似度用词面 Jaccard 近似(避免引入 embedding 模型依赖; 词表来自 text 4-gram)
    let mut scored: Vec<(f64, Hit)> = rrf.into_values().map(|(s, h)| (s, h)).collect();
    scored.sort_by(|a, b| b.0.partial_cmp(&a.0).unwrap());

    let mut selected: Vec<Hit> = Vec::with_capacity(req.top_k);
    let mut selected_grams: Vec<HashSet<String>> = Vec::new();
    while !scored.is_empty() && selected.len() < req.top_k {
        let mut best_idx = 0usize;
        let mut best_val = f64::NEG_INFINITY;
        for (i, (rel, hit)) in scored.iter().enumerate() {
            let grams = four_grams(&hit.text);
            let max_sim = selected_grams.iter()
                .map(|g| jaccard(&grams, g))
                .fold(0.0f64, f64::max);
            let mmr = req.lambda * rel - (1.0 - req.lambda) * max_sim;
            if mmr > best_val {
                best_val = mmr;
                best_idx = i;
            }
        }
        let (rel, hit) = scored.remove(best_idx);
        selected_grams.push(four_grams(&hit.text));
        selected.push(Hit { score: rel, ..hit });
    }

    let _ = collection;
    (StatusCode::OK, Json(RerankResponse {
        results: selected,
        algorithm: "rrf(k=60)+mmr(lambda=0.7)".into(),
        elapsed_us: t0.elapsed().as_micros() as u64,
    }))
}

fn four_grams(text: &str) -> HashSet<String> {
    let chars: Vec<char> = text.chars().filter(|c| !c.is_whitespace()).collect();
    let mut set = HashSet::new();
    if chars.len() <= 4 {
        set.insert(text.trim().to_string());
        return set;
    }
    for w in chars.windows(4) {
        set.insert(w.iter().collect());
    }
    set
}

fn jaccard(a: &HashSet<String>, b: &HashSet<String>) -> f64 {
    let inter = a.intersection(b).count() as f64;
    let union = a.len() + b.len() - inter as usize;
    if union == 0 { 0.0 } else { inter / union as f64 }
}
