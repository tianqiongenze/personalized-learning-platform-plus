#!/bin/sh
# tenant 列表端点受控并发测试(集群内直连 ClusterIP, 排除网络/DNS 变量)
# 用法: sh tenant_conc.sh <并发数> <总请求数>
TID=ca0da7c6-7cda-44ac-bfcd-da2696f9b851
URL="http://10.109.66.156:8081/api/v1/tenants?page=0&size=20"
CONC=${1:-50}
TOTAL=${2:-200}
rm -rf /tmp/tc_out; mkdir -p /tmp/tc_out
i=0
while [ $i -lt $TOTAL ]; do
  j=0
  while [ $j -lt $CONC ] && [ $i -lt $TOTAL ]; do
    ( curl -s -o /dev/null -w "%{http_code} %{time_total}\n" -m 30 -H "X-Tenant-Id: $TID" "$URL" >> /tmp/tc_out/r.txt ) &
    i=$((i+1)); j=$((j+1))
  done
  wait
done
echo "--- conc=$CONC total=$TOTAL ---"
echo -n "codes: "; awk '{print $1}' /tmp/tc_out/r.txt | sort | uniq -c | tr '\n' ' '; echo
awk '{print $2}' /tmp/tc_out/r.txt | sort -n > /tmp/tc_out/sorted.txt
N=$(wc -l < /tmp/tc_out/sorted.txt)
AVG=$(awk '{s+=$1} END {printf "%.3f", s/NR}' /tmp/tc_out/r.txt)
P50=$(sed -n "$((N/2))p" /tmp/tc_out/sorted.txt)
P90=$(sed -n "$((N*9/10))p" /tmp/tc_out/sorted.txt)
P99=$(sed -n "$((N*99/100))p" /tmp/tc_out/sorted.txt)
MAX=$(tail -1 /tmp/tc_out/sorted.txt)
echo "avg=${AVG}s p50=${P50}s p90=${P90}s p99=${P99}s max=${MAX}s"
