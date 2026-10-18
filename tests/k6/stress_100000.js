// K6 10 万并发压测(多阶段爬坡, 面向"多地区 × 多高校"目标容量)
// 容量模型见 docs/CAPACITY_100K.md: 10 万并发在线 ≈ 峰值 30,000 RPS
// 说明: k6 单机一般 3k-6k VU 上限, 10 万需多机并行(k6-operator / 分布式 executor),
//       本脚本按"单机分片"设计 —— 用 VU_SHARD / VU_SHARDS 环境变量把总量切分到 N 台执行机。
// 运行示例(20 台 × 5000 VU = 100000):
//   k6 run -e BASE_URL=http://<ingress> -e VU_SHARDS=20 -e VU_SHARD=0 tests/k6/stress_100000.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL_VUS = parseInt(__ENV.TOTAL_VUS || '100000', 10);
const SHARDS = parseInt(__ENV.VU_SHARDS || '1', 10);
const SHARD = parseInt(__ENV.VU_SHARD || '0', 10);
const PEAK = Math.ceil(TOTAL_VUS / SHARDS); // 本分片承担的峰值 VU

const errors = new Rate('app_errors');
const bizLatency = new Trend('biz_read_latency');
const submitLatency = new Trend('ai_submit_latency');
const throttled = new Counter('rate_limited_429');

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5m', target: Math.ceil(PEAK * 0.1) },
        { duration: '10m', target: Math.ceil(PEAK * 0.3) },
        { duration: '10m', target: Math.ceil(PEAK * 0.6) },
        { duration: '15m', target: PEAK },   // 峰值平台
        { duration: '10m', target: PEAK },
        { duration: '3m', target: 0 },
      ],
      gracefulRampDown: '2m',
    },
  },
  thresholds: {
    // 业务读: P95 < 800ms, P99 < 1500ms
    'biz_read_latency': ['p(95)<800', 'p(99)<1500'],
    // AI 异步提交(削峰后): P95 < 500ms
    'ai_submit_latency': ['p(95)<500'],
    // 错误率 < 0.5%, 429 率 < 1%(配额内租户, 超配额限流属预期)
    'app_errors': ['rate<0.005'],
    'http_req_failed': ['rate<0.005'],
    'rate_limited_429': ['count<1'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// 多租户(多高校)分布: 每 VU 绑定一个租户, 模拟真实多高校混跑
const TENANT_COUNT = parseInt(__ENV.TENANT_COUNT || '20', 10);
function tenantOf(vu) {
  return `tenant-${String((vu % TENANT_COUNT) + 1).padStart(2, '0')}`;
}

export default function () {
  const tenant = tenantOf(__VU);
  const params = {
    headers: {
      'X-Tenant-Id': tenant,
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    timeout: '30s',
  };

  // ---- 1) 读: 课程列表(命中 L1/L2 缓存) ----
  let r = http.get(`${BASE}/api/v1/courses?page=0&size=20`, params);
  bizLatency.add(r.timings.duration);
  let ok = check(r, { 'courses 200/304': (res) => res.status === 200 || res.status === 304 });
  if (r.status === 429) throttled.add(1);
  errors.add(!ok && r.status !== 429);

  sleep(Math.random() * 2 + 1); // 思考时间 1-3s, 贴近真实学习行为

  // ---- 2) 读: 学习路径(teaching) ----
  r = http.get(`${BASE}/api/v1/paths?status=IN_PROGRESS`, params);
  bizLatency.add(r.timings.duration);
  ok = check(r, { 'paths 200': (res) => res.status === 200 });
  if (r.status === 429) throttled.add(1);
  errors.add(!ok && r.status !== 429);

  // ---- 3) 读: 知识点图谱(新补 REST 出口) ----
  r = http.get(`${BASE}/api/v1/knowledge/graph?entity=伺服电机控制&hops=1`, params);
  bizLatency.add(r.timings.duration);
  ok = check(r, { 'kg graph 200': (res) => res.status === 200 });
  if (r.status === 429) throttled.add(1);
  errors.add(!ok && r.status !== 429);

  sleep(Math.random() * 2 + 1);

  // ---- 4) 写: AI 生成异步提交(削峰, 毫秒返回) ----
  // 权重控制: 仅 10% VU 触发生成, 避免 LLM 侧被打满(容量见 CAPACITY_100K.md)
  if (__VU % 10 === 0) {
    const payload = JSON.stringify({
      resourceType: 'EXERCISE_SET',
      knowledgePoint: '伺服电机控制',
      industryTags: '智能制造产线',
      requirements: '面向高职二年级, 覆盖脉冲与编码器',
    });
    r = http.post(`${BASE}/api/v1/generation/async`, payload, params);
    submitLatency.add(r.timings.duration);
    ok = check(r, { 'gen async 202/200': (res) => res.status === 202 || res.status === 200 });
    if (r.status === 429) throttled.add(1);
    errors.add(!ok && r.status !== 429);
  }

  sleep(Math.random() * 3 + 2);
}

// 多机并行时的分片信息输出, 便于汇总各机结果
export function setup() {
  console.log(`[shard ${SHARD + 1}/${SHARDS}] totalVUs=${TOTAL_VUS} peakThisShard=${PEAK} base=${BASE}`);
  return { shard: SHARD, shards: SHARDS, peak: PEAK };
}
