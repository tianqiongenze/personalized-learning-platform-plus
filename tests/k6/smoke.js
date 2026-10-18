// K6 冒烟测试: 部署后 1 分钟健康确认(每服务 up + 关键接口 200)
import http from 'k6/http';
import { check, group } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: { http_req_failed: ['rate<0.05'] },
};

export default function () {
  group('health', () => {
    for (const p of ['/api/v1/ops/../actuator/health']) {
      // actuator 直接探活(集群内)
      const r = http.get(`${BASE}${p}`);
      check(r, { 'health 200': (res) => res.status === 200 });
    }
  });
  group('public api', () => {
    const r = http.get(`${BASE}/api/v1/courses?page=0&size=5`);
    check(r, { 'courses ok': (res) => res.status === 200 || res.status === 401 });
  });
}
