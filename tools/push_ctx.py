# -*- coding: utf-8 -*-
"""稳健上传: tar 本地打包 -> sftp 单文件 -> 节点解压(绕过逐文件 SFTP mkdir 问题)"""
import os, sys, time, tarfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from remote import upload, run

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def tar_dir(local_dir: str, out_tar: str):
    with tarfile.open(out_tar, "w:gz") as tf:
        tf.add(local_dir, arcname=".")


def push_dir(host: str, local_dir: str, remote_dir: str):
    tmp_tar = os.path.join(os.environ.get("TEMP", "/tmp"), "ctxpush.tgz")
    tar_dir(local_dir, tmp_tar)
    size_mb = os.path.getsize(tmp_tar) / 1e6
    t0 = time.time()
    run(host, f"mkdir -p {remote_dir}")
    upload(host, tmp_tar, "/tmp/ctxpush.tgz")
    o, e = run(host, f"cd {remote_dir} && tar xzf /tmp/ctxpush.tgz && rm /tmp/ctxpush.tgz && find . -type f | wc -l")
    print(f"{local_dir} -> {host}:{remote_dir}  {size_mb:.1f}MB {time.time()-t0:.1f}s  files={o.strip()} {e.strip()[:200]}")
