# GitHub 同步说明

## 当前状态

本地仓库**已初始化并完成提交**，随时可推送：

| 项 | 值 |
|---|---|
| 仓库路径 | `D:/studyings/personalized-learning-platform-plus` |
| 当前提交 | `f585c36` — feat: 十万并发零失败达成 + 7 个真实缺陷修复 + TiDB 数据层切换 |
| 文件数 | 1,280 |
| 仓库体积 | 39.7 MB（bundle 压缩后 12 MB） |
| 主分支 | `main` |
| 离线包 | `D:/studyings/platform-plus-full.bundle`（含完整历史，可校验） |

## 为什么没有直接推上去

当前执行环境的网络策略**拦截了 GitHub**：

| 目标 | 结果 |
|---|---|
| `github.com` | 代理返回 `502 CONNECT tunnel failed` |
| `api.github.com` | 被代理重定向回 `github.com`（同样被拦） |
| `ssh.github.com:443` | 不可达 |
| 本机代理端口（7890/7897/10809/1080） | 均未开放 |

因此推送需在你的本机终端完成，或先提供一个可用的代理。

## 方式一：本机直接推送（推荐）

在 `D:/studyings/personalized-learning-platform-plus` 目录下执行：

```bash
# 1) 在 GitHub 上新建一个空仓库（不要勾选 README/.gitignore/LICENSE）
# 2) 执行一键脚本
bash tools/push_to_github.sh https://github.com/<你的用户名>/<仓库名>.git
```

或手动执行：

```bash
git remote add origin https://github.com/<用户名>/<仓库名>.git
git branch -M main
git push -u origin main
```

> 若本机走了代理（如 Clash 7890），可临时设置：
> `git config --global http.proxy http://127.0.0.1:7890`

## 方式二：用离线 bundle 迁移

`platform-plus-full.bundle` 是完整的 Git 仓库打包（含全部历史），可在任意机器还原：

```bash
git clone platform-plus-full.bundle platform-plus
cd platform-plus
git remote set-url origin https://github.com/<用户名>/<仓库名>.git
git push -u origin main
```

校验 bundle 完整性：

```bash
git bundle verify platform-plus-full.bundle
# 期望输出: The bundle records a complete history.  HEAD = f585c36
```

## 仓库内容说明

### 已纳入版本管理

- 全部业务代码：`tenant-service` / `iam-service` / `course-service` / `teaching-service` / `ops-service` / `graphql-gateway` / `ai-service` / `kt-service` / `realtime-gateway` / `vector-reranker` / `platform-common`
- 前端：`ops-web` / `web-student` / `web-teacher`
- 部署：`k8s/`（含 `k8s/loadtest/hpa.yaml`、`k6-deployment.yaml`）
- 压测：`tests/distributed/`（`load.js`、`locustfile_cluster.py`、`tenant_conc.sh`、`probe_latency.sh`）
- 工具：`tools/`（`switch_datasource.py`、`deploy_loadtest.py`、`push_to_github.sh`）
- 资料：`docs/`（含本轮新增 `100K_ZERO_FAILURE_TEST_REPORT.md`）
- 知识图谱种子：`data/kg33618/`

### 已排除（见 `.gitignore`）

| 排除项 | 原因 |
|---|---|
| `*/target/`、`*/build/`、`node_modules/`、`dist/` | 构建产物与依赖，可由 `mvn package` / `npm ci` 重建 |
| `data/scale100m/`（296 MB） | 1 亿级交互合成数据，由 `tools/datagen.py` 生成 |
| `data/reco_model/`（81 MB） | ALS 训练产物，由 `tools/train_recommender.py` 生成 |
| `realtime-gateway/realtime-gateway-linux` | 平台相关编译二进制 |
| `locust/reports/**/*.html` | 压测临时报告 |

## ⚠️ 安全提示

`k8s/base/configmap-secret.yaml` 中含明文占位凭据：

| 键 | 值 |
|---|---|
| `REDIS_PASSWORD` | `difyai123456` |
| `GRAFANA_PASSWORD` | `admin123` |
| `JWT_SECRET` | `your-super-secret-jwt-key-change-in-production-min-32-chars` |
| `POSTGRES_PASSWORD` | `postgres` |

这些是**测试环境的占位值**（`JWT_SECRET` 本身即写明 `change-in-production`），但若仓库设为 **Public**，建议先改为环境变量注入或使用 SealedSecret，再推送。

## 推送后建议

1. 仓库设为 **Private**（含部署清单与内部资料）
2. 开启分支保护：`main` 需 PR 合并
3. 在 CI 中增加「目标方言建空库」冒烟（避免 `order` 保留字这类 DDL 问题复发）
