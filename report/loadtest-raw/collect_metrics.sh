#!/bin/bash
# collect_metrics.sh <phase> — 采集 175/176 服务器指标到 /tmp/load-metrics-<phase>.txt
PHASE=${1:?phase}
F=/tmp/load-metrics-$PHASE.txt
: > $F
for i in 1 2 3; do
  for H in 10.167.2.175 10.167.2.176; do
    echo "=== sample$i $H $(date +%H:%M:%S) ===" >> $F
    ssh -o ConnectTimeout=5 root@$H '
      echo "-- top --"; top -bn1 | head -5
      echo "-- free --"; free -m | head -3
      echo "-- net --"; cat /proc/net/dev | grep -E "eth0|ens" 
    ' 2>/dev/null >> $F
  done
  echo "=== sample$i kubectl ===" >> $F
  ssh -o ConnectTimeout=5 root@10.167.2.175 '
    kubectl top nodes 2>/dev/null
    kubectl top pod -n qianrenqianmian --sort-by=cpu 2>/dev/null | head -20
  ' 2>/dev/null >> $F
  [ $i -lt 3 ] && sleep 5
done
echo done >> $F
