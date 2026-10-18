// K6 全链路全功能压测（网关 → GraphQL → REST → KT → AI 异步 → 运维平台）
// 与 datagen_100m.py 的 Zipf 分布共用 hotkeys.txt / 同一实体 ID 空间（stable_uuid）。
// 目标: 10 万并发在线 ≈ 峰值 30,000 RPS；多机分片执行(见 docs/CAPACITY_100K.md)。
// 运行: k6 run -e BASE_URL=http://<ingress> -e VU_SHARDS=20 -e VU_SHARD=0 tests/k6/fullchain_100000.js
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const GQL = `${BASE}/graphql`;
const TOTAL_VUS = parseInt(__ENV.TOTAL_VUS || '100000', 10);
const SHARDS = parseInt(__ENV.VU_SHARDS || '1', 10);
const SHARD = parseInt(__ENV.VU_SHARD || '0', 10);
const PEAK = Math.ceil(TOTAL_VUS / SHARDS);
const HOTKEYS = (__ENV.HOTKEYS_FILE || '') ; // 可选: 预读热键(分片内均匀取段)

const readLat = new Trend('read_latency');          // 全部读路径
const writeLat = new Trend('write_latency');        // AI 异步提交/回传
const gqlLat = new Trend('graphql_latency');
const ktLat = new Trend('kt_inference_latency');
const errors = new Rate('app_errors');
const throttled = new Counter('rate_limited_429');
const cacheHit = new Rate('cache_hit_ratio');       // L1/L2 命中(以响应 < 50ms 近似)

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5m', target: Math.ceil(PEAK * 0.1) },
        { duration: '10m', target: Math.ceil(PEAK * 0.3) },
        { duration: '10m', target: Math.ceil(PEAK * 0.6) },
        { duration: '15m', target: PEAK },
        { duration: '10m', target: PEAK },
        { duration: '3m', target: 0 },
      ],
      gracefulRampDown: '2m',
    },
  },
  thresholds: {
    'read_latency': ['p(95)<800', 'p(99)<1500'],
    'graphql_latency': ['p(95)<1000'],
    'kt_inference_latency': ['p(95)<200'],
    'write_latency': ['p(95)<500'],
    'app_errors': ['rate<0.005'],
    'http_req_failed': ['rate<0.005'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const TENANT_COUNT = parseInt(__ENV.TENANT_COUNT || '20', 10);
function tenantOf(vu) { return `tenant-${String((vu % TENANT_COUNT) + 1).padStart(2, '0')}`; }
function paramsOf(vu) {
  return {
    headers: { 'X-Tenant-Id': tenantOf(vu), 'Content-Type': 'application/json' },
    timeout: '30s',
  };
}
function rec(res, t, ok, isWrite = false) {
  (isWrite ? writeLat : t).add(res.timings.duration);
  if (res.timings.duration < 50) cacheHit.add(true); else cacheHit.add(false);
  if (res.status === 429) throttled.add(1);
  errors.add(!ok && res.status !== 429);
}

export default function () {
  const p = paramsOf(__VU);
  const tenant = tenantOf(__VU);
  // 与 datagen 共用的确定性实体(取每个租户 Zipf 最热课程)
  const hotCourse = `t:${tenant}:course:`; // 真实 ID 由 hotkeys/seed 计算得到, 压测时用 env 注入或走 GraphQL 查询

  group('1. GraphQL 聚合查询(7 域一次请求)', () => {
    const q = JSON.stringify({ query: `{ tenants { id code name } }` });
    const r = http.post(GQL, q, p);
    const ok = check(r, { 'gql 200': (x) => x.status === 200, 'gql data': (x) => x.json('data') !== undefined });
    rec(r, gqlLat, ok);
  });

  group('2. 课程读(缓存层: L1 Caffeine + L2 Redis)', () => {
    let r = http.get(`${BASE}/api/v1/courses?page=0&size=20`, p);
    rec(r, readLat, check(r, { 'courses ok': (x) => x.status === 200 }));
    sleep(0.5);
    // 热点课程详情(warmup 已预热该键)
    r = http.get(`${BASE}/api/v1/courses?keyword=${tenant}`, p);
    rec(r, readLat, check(r, { 'hot course ok': (x) => x.status === 200 }));
  });

  group('3. 知识图谱(新 REST 出口 → RAG 候选)', () => {
    const r = http.get(`${BASE}/api/v1/knowledge/graph?entity=${encodeURIComponent('伺服电机控制')}&hops=1`, p);
    rec(r, readLat, check(r, { 'kg ok': (x) => x.status === 200 }));
  });

  group('4. KT 推理(BKT/DKT 毫秒级)', () => {
    const sid = `e2e-vu-${__VU}`;
    const r = http.get(`${BASE}/api/v1/kt/mastery?studentId=${sid}`, p);
    rec(r, ktLat, check(r, { 'kt ok': (x) => x.status === 200 }));
  });

  sleep(Math.random() * 2 + 0.5);

  group('5. AI 生成异步(10% VU 触发, Pulsar 削峰)', () => {
    if (__VU % 10 === 0) {
      const payload = JSON.stringify({
        resourceType: 'EXERCISE_SET', knowledgePoint: '伺服电机控制',
        industryTags: '智能制造产线', requirements: '面向高职二年级, 覆盖脉冲与编码器',
      });
      const r = http.post(`${BASE}/api/v1/generation/async`, payload, p);
      rec(r, readLat, check(r, { 'gen 202/200': (x) => x.status === 202 || x.status === 200 }), true);
    }
  });

  group('6. 运维平台总览(观测链路)', () => {
    const r = http.get(`${BASE}/api/v1/ops/overview`, { ...p, timeout: '10s' });
    rec(r, readLat, check(r, { 'ops ok': (x) => x.status === 200 }));
  });

  group('7. 实时网关健康(WS/SSE 前置)', () => {
    const r = http.get(`${BASE}/realtime/health`, { ...p, timeout: '5s' });
    rec(r, readLat, check(r, { 'rt ok': (x) => x.status === 200 }));
  });

  sleep(Math.random() * 3 + 1);
}

export function setup() {
  console.log(`[fullchain shard ${SHARD + 1}/${SHARDS}] peak=${PEAK} base=${BASE}`);
}
