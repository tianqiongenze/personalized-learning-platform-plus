#!/bin/bash
cid=$(crictl -r unix:///run/cri-dockerd.sock ps --name bk-cookie-fix -q | head -1)
echo cid=$cid
pid=$(docker inspect ${cid:0:12} --format '{{.State.Pid}}')
echo pid=$pid
[ -n "$pid" ] && [ "$pid" != "0" ] && kill -9 $pid
sleep 5
kubectl -n infra get pods | grep bookie
