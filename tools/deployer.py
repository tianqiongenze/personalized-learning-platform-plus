# -*- coding: utf-8 -*-
"""Python k8s client 部署器: 逐文档 apply(存在则 patch), 等待就绪"""
import sys
import time
import yaml as pyyaml
from kubernetes import client, config

config.load_kube_config()
CORE = client.CoreV1Api()
APPS = client.AppsV1Api()
NET = client.NetworkingV1Api()
BAT = client.BatchV1Api()
AUTO = client.AutoscalingV2Api()


def apply_docs(path, wait=True):
    docs = [d for d in pyyaml.safe_load_all(open(path, encoding="utf-8")) if d]
    print(f"=== {path}: {len(docs)} docs ===")
    for d in docs:
        kind, name, ns = d["kind"], d["metadata"]["name"], d["metadata"].get("namespace", "default")
        try:
            if kind == "Namespace":
                CORE.create_namespace(client.V1Namespace(metadata=d["metadata"]))
            elif kind == "ConfigMap":
                try:
                    CORE.create_namespaced_config_map(ns, d)
                except client.exceptions.ApiException as e:
                    if e.status == 409:
                        CORE.replace_namespaced_config_map(name, ns, d)
                    else:
                        raise
            elif kind == "Secret":
                try:
                    CORE.create_namespaced_secret(ns, d)
                except client.exceptions.ApiException as e:
                    if e.status == 409:
                        CORE.replace_namespaced_secret(name, ns, d)
                    else:
                        raise
            elif kind == "Service":
                try:
                    CORE.create_namespaced_service(ns, d)
                except client.exceptions.ApiException as e:
                    if e.status == 409:
                        CORE.patch_namespaced_service(name, ns, d)
                    else:
                        raise
            elif kind == "ServiceAccount":
                try:
                    CORE.create_namespaced_service_account(ns, d)
                except client.exceptions.ApiException as e:
                    if e.status == 409:
                        CORE.replace_namespaced_service_account(name, ns, d)
                    else:
                        raise
            elif kind == "Deployment":
                try:
                    APPS.create_namespaced_deployment(ns, d)
                except client.exceptions.ApiException as e:
                    if e.status == 409:
                        APPS.replace_namespaced_deployment(name, ns, d)
                    else:
                        raise
            elif kind == "StatefulSet":
                try:
                    APPS.create_namespaced_stateful_set(ns, d)
                except client.exceptions.ApiException as e:
                    if e.status == 409:
                        APPS.replace_namespaced_stateful_set(name, ns, d)
                    else:
                        raise
            elif kind == "Ingress":
                try:
                    NET.create_namespaced_ingress(ns, d)
                except client.exceptions.ApiException as e:
                    if e.status == 409:
                        NET.replace_namespaced_ingress(name, ns, d)
                    else:
                        raise
            elif kind == "HorizontalPodAutoscaler":
                try:
                    AUTO.create_namespaced_horizontal_pod_autoscaler(ns, d)
                except client.exceptions.ApiException as e:
                    if e.status == 409:
                        AUTO.replace_namespaced_horizontal_pod_autoscaler(name, ns, d)
                    else:
                        raise
            elif kind == "Job":
                try:
                    BAT.create_namespaced_job(ns, d)
                except client.exceptions.ApiException as e:
                    if e.status == 409:
                        print(f"  skip existing Job {name}")
                    else:
                        raise
            elif kind == "PodDisruptionBudget":
                from kubernetes.client import PolicyV1Api
                pol = PolicyV1Api()
                try:
                    pol.create_namespaced_pod_disruption_budget(ns, d)
                except client.exceptions.ApiException as e:
                    if e.status == 409:
                        pol.replace_namespaced_pod_disruption_budget(name, ns, d)
                    else:
                        raise
            else:
                print(f"  SKIP unsupported kind {kind}")
                continue
            print(f"  applied {kind}/{ns}/{name}")
        except client.exceptions.ApiException as e:
            print(f"  FAIL {kind}/{ns}/{name}: {e.status} {e.reason} {str(e.body)[:120]}")


def wait_deployments(ns, names, timeout_s=600):
    print(f"--- waiting deployments in {ns}: {names} ---")
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        all_ready = True
        for n in names:
            try:
                d = APPS.read_namespaced_deployment_status(n, ns)
                want = d.spec.replicas or 1
                ready = d.status.ready_replicas or 0
                if ready < want:
                    all_ready = False
                    print(f"  {n}: {ready}/{want}")
            except client.exceptions.ApiException:
                all_ready = False
                print(f"  {n}: not created yet")
        if all_ready:
            print(f"  ALL READY in {ns}")
            return True
        time.sleep(15)
    print(f"  TIMEOUT waiting {ns}")
    return False


if __name__ == "__main__":
    phase = sys.argv[1] if len(sys.argv) > 1 else "all"
    B = "k8s/base/"
    if phase in ("base", "all"):
        apply_docs(B + "namespaces.yaml")
        apply_docs(B + "configmap-secret.yaml")
        apply_docs(B + "grafana-dashboards.yaml")
    if phase in ("infra", "all"):
        apply_docs(B + "pulsar.yaml")
        apply_docs(B + "milvus.yaml")
        apply_docs(B + "nebula.yaml")
    if phase in ("apps", "all"):
        apply_docs(B + "new-services.yaml")
        for f in ["tenant-service.yaml", "iam-service.yaml", "course-service.yaml", "teaching-service.yaml"]:
            apply_docs(B + f)
        apply_docs(B + "hpa.yaml")
        apply_docs(B + "hpa-new-services.yaml")
        apply_docs(B + "ingress-v2.yaml")
    if phase in ("wait", "all"):
        wait_deployments("qianrenqianmian", [
            "tenant-service", "iam-service", "course-service", "teaching-service",
            "ops-service", "ai-service", "agent-service", "realtime-gateway", "ops-web"], 900)
        wait_deployments("infra", ["vector-reranker", "kt-service"], 300)
