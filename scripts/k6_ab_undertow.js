// A/B 压测(ADR-006 Undertow vs Tomcat): 100 VU 固定并发, 默认 3 分钟
// 用法: k6 run -e TARGET=http://10.167.2.175:31430 -e MODE=course -e DURATION=3m scripts/k6_ab_undertow.js
// MODE=course: 读密集(课程列表, tenant 需 UUID); MODE=iam: 写密集(登录, 业务失败算 app_error 但仍产生服务器负载)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE = __ENV.TARGET || 'http://10.167.2.175:31430';
const MODE = __ENV.MODE || 'course';
const DURATION = __ENV.DURATION || '3m';
const TENANT = '11111111-1111-1111-1111-111111111111';
const srvErr = new Rate('server_errors');

export const options = {
  vus: 100,
  duration: DURATION,
  thresholds: { http_req_failed: ['rate<0.5'] },
  summaryTrendStats: ['avg','med','p(50)','p(95)','p(99)','max'],
};

export default function () {
  if (MODE === 'course') {
    const res = http.get(`${BASE}/api/v1/courses?page=0&size=20`, {
      headers: { 'X-Tenant-Id': TENANT }, tags: { name: 'read' },
    });
    srvErr.add(res.status >= 500);
    check(res, { 'read 200': (r) => r.status === 200 });
  } else {
    // 登录写密集: 真实到达 BCrypt/JWT 路径(凭据故意无效, 服务端仍执行完整校验+BCrypt 比对)
    const res = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({
      username: `abuser-${__VU}-${__ITER % 50}`, password: 'WrongPass@AbTest2026',
    }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } });
    srvErr.add(res.status >= 500);
    check(res, { 'login answered 4xx/200': (r) => (r.status >= 200 && r.status < 500) });
  }
  sleep(Math.random() * 0.5);
}
