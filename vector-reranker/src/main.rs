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
    /// 预留通道选择(当前召回统一委托 ai-service 单一向量库), 仅为协议兼容保留
    #[serde(default)]
    _collections: Vec<String>,
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
    /// 预留: 未来按 query 做条件重排; 当前重排仅依赖各通道命中与文本多样性
    #[serde(default)]
    _query: Option<String>,
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
static DELEGATE_OK: AtomicU64 = AtomicU64::new(0);
static DELEGATE_FAIL: AtomicU64 = AtomicU64::new(0);
static DELEGATE_EMPTY: AtomicU64 = AtomicU64::new(0);

/// 向量召回委托目标(ai-service, Spring AI VectorStore/Milvus 连接持有方)。
/// 部署注入, 缺省指向集群内 ai-service; 未配置或不可达时降级为空集并在 /metrics 暴露失败计数。
fn delegate_url() -> String {
    std::env::var("SEARCH_DELEGATE_URL")
        .unwrap_or_else(|_| "http://ai-service.platform:8085".into())
}

async fn fetch_candidates(query: &str, top_k: usize) -> Vec<Hit> {
    let url = format!("{}/internal/vector-search", delegate_url().trim_end_matches('/'));
    let client = reqwest::Client::builder()
        .connect_timeout(std::time::Duration::from_millis(2000))
        .timeout(std::time::Duration::from_secs(10))
        .build();
    let client = match client {
        Ok(c) => c,
        Err(_) => {
            DELEGATE_FAIL.fetch_add(1, Ordering::Relaxed);
            return vec![];
        }
    };
    let resp = client
        .post(&url)
        .json(&serde_json::json!({ "query": query, "topK": top_k }))
        .send()
        .await;
    match resp {
        Ok(r) if r.status().is_success() => match r.json::<Vec<Hit>>().await {
            Ok(hits) => {
                if hits.is_empty() {
                    DELEGATE_EMPTY.fetch_add(1, Ordering::Relaxed);
                } else {
                    DELEGATE_OK.fetch_add(1, Ordering::Relaxed);
                }
                hits
            }
            Err(_) => {
                DELEGATE_FAIL.fetch_add(1, Ordering::Relaxed);
                vec![]
            }
        },
        _ => {
            DELEGATE_FAIL.fetch_add(1, Ordering::Relaxed);
            vec![]
        }
    }
}

async fn metrics() -> String {
    format!(
        "reranker_requests_total {}\n\
         reranker_delegate_ok_total {}\n\
         reranker_delegate_empty_total {}\n\
         reranker_delegate_fail_total {}\n",
        REQ_COUNTER.load(Ordering::Relaxed),
        DELEGATE_OK.load(Ordering::Relaxed),
        DELEGATE_EMPTY.load(Ordering::Relaxed),
        DELEGATE_FAIL.load(Ordering::Relaxed),
    )
}

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

/// 兼容 agent-service 的内部检索入口: 真实召回委托 ai-service /internal/vector-search
/// (Spring AI VectorStore/Milvus 连接持有方), 本服务负责超时控制与失败降级(空集+计数)。
/// 空查询快路径不外发请求。
async fn internal_search(Json(q): Json<SearchQuery>) -> Json<SearchResult> {
    let t0 = std::time::Instant::now();
    REQ_COUNTER.fetch_add(1, Ordering::Relaxed);
    if q.query.trim().is_empty() {
        return Json(SearchResult { results: vec![], reranker: "passthrough".into(), elapsed_us: t0.elapsed().as_micros() as u64 });
    }
    let hits = fetch_candidates(&q.query, q.top_k.max(1)).await;
    Json(SearchResult { results: hits, reranker: "delegate-ai-service".into(), elapsed_us: t0.elapsed().as_micros() as u64 })
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
    let mut scored: Vec<(f64, Hit)> = rrf.into_values().collect();
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
