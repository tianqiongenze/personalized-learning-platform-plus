// k6-v12.js — 集群内分布式压测(经 nginx-gateway ClusterIP, 真实链路 auth_request→服务)
// 8 pod 副本 anti-affinity 分散节点, 限流按源 IP 每源 100r/s, 登录/token 每迭代共享(scenario setup)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const GW = __ENV.BASE_URL || 'http://10.107.142.193:80';
const TENANT = __ENV.TENANT_ID || 'da98138f-486f-42db-944c-219a5a44bf52';
const USER = __ENV.K6_USER || 'admin';
const PASS = __ENV.K6_PASS || 'Admin@12345';

const bizRead = new Trend('biz_read_latency');
const bizWrite = new Trend('biz_write_latency');
const appErrors = new Rate('app_errors');

const PROFILES = {
  smoke: [{ duration: '1m', target: 10 }],
  ladder: [
    { duration: '30s', target: 100 }, { duration: '2m', target: 100 },
    { duration: '30s', target: 300 }, { duration: '2m', target: 300 },
    { duration: '30s', target: 500 }, { duration: '2m', target: 500 },
    { duration: '15s', target: 0 },
  ],
};
const profile = __ENV.PROFILE || 'smoke';
export const options = {
  scenarios: { main: { executor: 'ramping-vus', startVUs: 0, stages: PROFILES[profile] } },
  thresholds: {
    'http_req_duration{name:read}': ['p(95)<800'],
    'http_req_duration{name:write}': ['p(95)<800'],
  },
  tags: { profile },
};

let TOKEN = '';
export function setup() {
  const r = http.post(`${GW}/api/v1/auth/login`, JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json', 'X-Tenant-Id': TENANT, Host: 'platform.local' } });
  const j = r.json();
  check(r, { 'login 200': (x) => x.status === 200 });
  return { token: (j && j.data && j.data.accessToken) || '' };
}

export default function (data) {
  TOKEN = data.token;
  const H = { 'X-Tenant-Id': TENANT, 'Content-Type': 'application/json', Authorization: `Bearer ${TOKEN}`, Host: 'platform.local' };
  const r = Math.random();
  let res, ok;
  if (r < 0.30) {
    res = http.get(`${GW}/api/v1/kt/mastery?studentId=00000000-0000-0000-0000-000000000001`, { headers: H, tags: { name: 'read' } });
    bizRead.add(res.timings.duration); ok = res.status === 200;
  } else if (r < 0.45) {
    res = http.get(`${GW}/api/v1/ops/actuator/health`, { headers: H, tags: { name: 'read' } });
    bizRead.add(res.timings.duration); ok = res.status === 200;
  } else if (r < 0.60) {
    res = http.get(`${GW}/api/v1/resources?page=0&size=20&courseId=10000000-0000-0000-0000-000000000001`, { headers: H, tags: { name: 'read' } });
    bizRead.add(res.timings.duration); ok = res.status === 200;
  } else if (r < 0.70) {
    res = http.get(`${GW}/api/v1/learning-paths?page=0&size=20`, { headers: H, tags: { name: 'read' } });
    bizRead.add(res.timings.duration); ok = res.status === 200;
  } else if (r < 0.97) {
    res = http.post(`${GW}/api/v1/kt/predict`, JSON.stringify({
      studentId: `00000000-0000-0000-0000-00000000000${1 + (__ITER % 9)}`,
      knowledgePointId: '00000000-0000-0000-0000-000000000001',
      correct: Math.random() > 0.4,
    }), { headers: H, tags: { name: 'write' } });
    bizWrite.add(res.timings.duration); ok = res.status === 200;
  } else {
    res = http.post(`${GW}/api/v1/learning-paths`, JSON.stringify({
      tenantId: TENANT, courseId: '10000000-0000-0000-0000-000000000001',
      title: `lt-${__VU}-${__ITER}-${Date.now()}`, totalSteps: 2,
    }), { headers: H, tags: { name: 'write' } });
    bizWrite.add(res.timings.duration); ok = res.status === 201 || res.status === 200;
  }
  appErrors.add(!ok);
  sleep(0.1);
}
