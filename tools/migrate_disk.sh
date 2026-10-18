#!/bin/bash
# ============================================================
# 磁盘平衡部署脚本 — 将根盘大目录迁移到数据盘(/home)
# 在两节点上分别执行; 执行前自动停服务, 执行后重启
# 用法: bash migrate_disk.sh <node-role>   # master | worker1
# ============================================================
set -e
ROLE=$1
TS=$(date +%Y%m%d_%H%M%S)
LOG=/root/migrate_${TS}.log
exec > >(tee -a $LOG) 2>&1

echo "=== [1] 当前根盘占用 ==="
df -h / | tail -1

echo "=== [2] 停止 k8s 与容器运行时 ==="
if [ "$ROLE" = "master" ]; then
  systemctl stop kubelet 2>/dev/null || k3s-killall.sh 2>/dev/null || true
else
  systemctl stop kubelet || true
fi
docker stop $(docker ps -q) 2>/dev/null || true
systemctl stop docker || true
systemctl stop containerd || true

echo "=== [3] 迁移 containerd 数据根 /var/lib/containerd -> /home/containerd-data ==="
mkdir -p /home/containerd-data
if [ ! -d /home/containerd-data/io.containerd.content.v1.content ]; then
  rsync -aHAX /var/lib/containerd/ /home/containerd-data/
fi
mv /var/lib/containerd /var/lib/containerd.root.bak_${TS}
ln -s /home/containerd-data /var/lib/containerd

echo "=== [4] 迁移 docker 数据根(若仍在根盘) ==="
if [ ! -L /var/lib/docker ] && [ -d /var/lib/docker ]; then
  mkdir -p /home/docker-data-root
  rsync -aHAX /var/lib/docker/ /home/docker-data-root/ || true
  mv /var/lib/docker /var/lib/docker.root.bak_${TS}
  ln -s /home/docker-data-root /var/lib/docker
fi

echo "=== [5] 迁移 registry 数据(仅存在该目录的节点) ==="
if [ -d /var/lib/registry ] && [ ! -L /var/lib/registry ]; then
  mkdir -p /home/registry-data
  rsync -aHAX /var/lib/registry/ /home/registry-data/
  mv /var/lib/registry /var/lib/registry.root.bak_${TS}
  ln -s /home/registry-data /var/lib/registry
fi
if [ -d /var/lib/registry-master ] && [ ! -L /var/lib/registry-master ]; then
  mkdir -p /home/registry-master-data
  rsync -aHAX /var/lib/registry-master/ /home/registry-master-data/
  mv /var/lib/registry-master /var/lib/registry-master.root.bak_${TS}
  ln -s /home/registry-master-data /var/lib/registry-master
fi

echo "=== [6] 迁移 etcd 数据(k3s/master) ==="
if [ -d /var/lib/etcd ] && [ ! -L /var/lib/etcd ]; then
  mkdir -p /home/etcd-data
  rsync -aHAX /var/lib/etcd/ /home/etcd-data/
  mv /var/lib/etcd /var/lib/etcd.root.bak_${TS}
  ln -s /home/etcd-data /var/lib/etcd
fi

echo "=== [7] 迁移 /opt 大目录 -> /home/opt-data ==="
mkdir -p /home/opt-data
for d in ollama-embed build ekb-src rancher; do
  if [ -d /opt/$d ] && [ ! -L /opt/$d ]; then
    mkdir -p /home/opt-data/$d
    rsync -aHAX /opt/$d/ /home/opt-data/$d/
    mv /opt/$d /opt/$d.root.bak_${TS}
    ln -s /home/opt-data/$d /opt/$d
  fi
done

echo "=== [8] kubelet 根目录迁移(空/镜像目录) ==="
if [ -d /var/lib/kubelet ] && [ ! -L /var/lib/kubelet ]; then
  mkdir -p /home/kubelet-data
  rsync -aHAX /var/lib/kubelet/ /home/kubelet-data/ || true
  # kubelet 不做符号链接(有安全限制), 改为 systemd drop-in 指定 --root-dir
  mkdir -p /etc/systemd/system/kubelet.service.d
  cat > /etc/systemd/system/kubelet.service.d/10-datadir.conf <<EOF
[Service]
Environment="KUBELET_ROOT_DIR=/home/kubelet-data"
ExecStart=
ExecStart=/usr/bin/kubelet --bootstrap-kubeconfig=/etc/kubernetes/bootstrap-kubelet.conf --kubeconfig=/etc/kubernetes/kubelet.conf --config=/var/lib/kubelet/config.yaml --container-runtime-endpoint=unix:///run/cri-dockerd.sock --pod-infra-container-image=registry.k8s.io/pause:3.9 --root-dir=/home/kubelet-data
EOF
fi

echo "=== [9] 重启服务 ==="
systemctl daemon-reload
systemctl start containerd
systemctl start docker
sleep 5
systemctl start kubelet || systemctl restart kubelet

echo "=== [10] 校验 ==="
sleep 10
df -h / | tail -1
df -h /home | tail -1
readlink -f /var/lib/containerd
docker info 2>/dev/null | grep -m1 'Docker Root Dir' || true
echo "=== 迁移完成, 日志: $LOG ==="
