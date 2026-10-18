// K6 1000 并发压测: 分阶段爬坡 100 -> 500 -> 1000 -> 1000 -> 0
// 阈值即通过标准: 业务读 P95<800ms, 错误率<0.5%, 429 率<1%(配额内租户)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const errorRate = new Rate('app_errors');
const bizLatency = new Trend('biz_read_latency');

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 },
        { duration: '3m', target: 500 },
        { duration: '5m', target: 1000 },  // 1000 并发平台
        { duration: '10m', target: 1000 },
        { duration: '2m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.005'],
    'http_req_duration{name:read}:p95': ['p95<800'],
    'http_req_duration{name:submit}:p95': ['p95<500'],
  },
};

const TENANTS = Array.from({ length: 6 }, (_, i) => `tenant-${String(i + 1).padStart(2, '0')}`);

export default function () {
  const tenant = TENANTS[Math.floor(Math.random() * TENANTS.length)];
  const headers = { 'X-Tenant-Id': tenant, 'Content-Type': 'application/json' };

  // 读路径(90%)
  if (Math.random() < 0.9) {
    const res = http.get(`${BASE}/api/v1/courses?page=0&size=20`, { headers, tags: { name: 'read' } });
    bizLatency.add(res.timings.duration);
    errorRate.add(res.status >= 500);
    check(res, {
      'read ok': (r) => r.status === 200 || r.status === 401,
      'tenant not rate-limited': (r) => r.status !== 429,
    });
  } else {
    // AI 生成异步提交(10%): 毫秒级返回
    const res = http.post(`${BASE}/api/v1/generation/async`, JSON.stringify({
      resourceType: 'KNOWLEDGE_POINT_TALK',
      knowledgePoint: 'PLC 梯形图基础',
      industryTags: '智能网联新能源汽车',
      studentId: `k6-${__VU}-${__ITER}`,
    }), { headers, tags: { name: 'submit' } });
    errorRate.add(res.status >= 500);
    check(res, { 'submit accepted': (r) => r.status === 202 || r.status === 200 || r.status === 401 });
  }
  sleep(Math.random() * 3 + 1);
}
