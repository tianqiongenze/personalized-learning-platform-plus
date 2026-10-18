# 真实集群部署顺序(deploy-rancher.sh deploy 使用)
1. namespaces.yaml + configmap-secret.yaml + grafana-dashboards.yaml(sidecar 注入现有 Grafana)
2. infra: pulsar.yaml(4.0.5) -> milvus.yaml(2.6) -> nebula.yaml(3.8.3) -> new-services.yaml(reranker/kt)
3. qianrenqianmian: 四业务服务 -> ops/ai/agent/realtime -> ops-web
4. hpa.yaml + hpa-new-services.yaml + ingress-v2.yaml
5. 复用不部署: CRDB/redis/监控栈/Loki/litellm/ingress-nginx/local-path
