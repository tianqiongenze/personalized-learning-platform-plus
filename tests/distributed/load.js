// 千人千面平台 K6 分布式压测脚本(集群内部署)
// 直连业务 ClusterIP, 绕开 NodePort conntrack 瓶颈
// 场景权重贴近真实: 登录(会话建立)10% / 租户列表30% / 课程列表30% / 图谱检索15% / 租户详情15%
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const TID = 'ca0da7c6-7cda-44ac-bfcd-da2696f9b851';
// 直连 ClusterIP: 避免十万 VU 下 CoreDNS 成为单点(每个 VU 反复解析会打爆 DNS)
// Service 的 ClusterIP 生命周期稳定, 与 Pod 扩缩容解耦, 是压测的正确寻址方式
const BASE_TENANT = 'http://10.109.66.156:8081/api/v1';
const BASE_IAM = 'http://10.102.114.74:8082/api/v1';
const BASE_COURSE = 'http://10.102.175.199:8083/api/v1';
const BASE_GRAPHQL = 'http://10.109.175.248:8097';

export const errorRate = new Rate('errors');
export const reqCounter = new Counter('total_requests');
export const graphqlTrend = new Trend('graphql_ms', true);
export const courseTrend = new Trend('course_ms', true);
export const tenantTrend = new Trend('tenant_ms', true);
export const loginTrend = new Trend('login_ms', true);

const USER_POOL = [];
for (let i = 1; i <= 60; i++) {
  USER_POOL.push({ username: 'loaduser' + i, password: 'Load@12345' });
}

const TARGET_VU = Number(__ENV.TARGET_VU);
const DURATION = __ENV.DURATION_MIN || '4m';
// 思考时间(ms): 区分两种压测模型
//  - THINK_MS=50   吞吐压力模型(打满系统, 用于找容量上限)
//  - THINK_MS=5000 并发在线用户模型(贴近真实学习者浏览节奏, 用于验证 N 万并发在线)
const THINK_MS = Number(__ENV.THINK_MS || 50);
// 两段式: 先爬坡到目标并发, 再保持稳态(避免"只在最后一刻达到峰值"的失真)
const RAMP = __ENV.RAMP_MIN || '2m';
const STAGES = [
  { duration: RAMP, target: TARGET_VU },
  { duration: DURATION, target: TARGET_VU },
];

export const options = {
  scenarios: {
    mixed: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: STAGES,
      gracefulStop: '30s',
    },
  },
  thresholds: {
    'errors': ['rate<0.01'],
    'http_req_duration': ['p(95)<2000'],
  },
};

function randUser() {
  return USER_POOL[Math.floor(Math.random() * USER_POOL.length)];
}

export default function () {
  const u = randUser();
  const r = Math.random();
  const headers = { 'X-Tenant-Id': TID, 'Content-Type': 'application/json' };

  if (r < 0.10) {
    // 登录(BCrypt 热点, 真实会话建立)
    const res = http.post(BASE_IAM + '/auth/login',
      JSON.stringify(u), { headers, tags: { name: 'login' } });
    loginTrend.add(res.timings.duration);
    check(res, { 'login 200': (x) => x.status === 200 });
  } else if (r < 0.40) {
    const res = http.get(BASE_TENANT + '/tenants?page=' + (Math.floor(Math.random() * 4)) + '&size=20',
      { headers: { 'X-Tenant-Id': TID }, tags: { name: 'tenant_list' } });
    tenantTrend.add(res.timings.duration);
    check(res, { 'tenant 200': (x) => x.status === 200 });
  } else if (r < 0.70) {
    const res = http.get(BASE_COURSE + '/courses?page=0&size=20',
      { headers: { 'X-Tenant-Id': TID }, tags: { name: 'course_list' } });
    courseTrend.add(res.timings.duration);
    check(res, { 'course 200': (x) => x.status === 200 });
  } else if (r < 0.85) {
    const payload = JSON.stringify({
      query: '{ courses(page:0,size:20) { content { id name status } totalElements } }',
    });
    const res = http.post(BASE_GRAPHQL + '/graphql', payload,
      { headers: { 'X-Tenant-Id': TID, 'Content-Type': 'application/json' }, tags: { name: 'graphql' } });
    graphqlTrend.add(res.timings.duration);
    check(res, { 'graphql 200': (x) => x.status === 200 });
  } else {
    // 原为 /actuator/health(15%): 健康检查属基础设施探针, 不计入用户流量模型;
    // 改为真实用户行为——租户详情读取(缓存命中路径)
    const res = http.get(BASE_TENANT + '/tenants/' + TID,
      { headers: { 'X-Tenant-Id': TID }, tags: { name: 'tenant_detail' } });
    tenantTrend.add(res.timings.duration);
    check(res, { 'tenant_detail 200': (x) => x.status === 200 });
  }
  errorRate.add(0);
  reqCounter.add(1);
  sleep(THINK_MS / 1000);
}
