"""paramiko 远程执行/SFTP 复用工具"""
import paramiko, sys

PASS = "hwyd@0413"
HOSTS = ["10.167.2.175", "10.167.2.176"]

def conn(host):
    c = paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(host, username="root", password=PASS, timeout=15)
    return c

def run(host, cmd, timeout=300):
    c = conn(host)
    _, out, err = c.exec_command(cmd, timeout=timeout)
    o, e = out.read().decode(errors="replace"), err.read().decode(errors="replace")
    c.close()
    return o, e

def upload(host, local, remote):
    c = conn(host)
    sftp = c.open_sftp()
    sftp.put(local, remote)
    sftp.close(); c.close()

def upload_tree(host, local_dir, remote_dir):
    c = conn(host)
    sftp = c.open_sftp()
    import os
    def mkdirs(p):
        parts = p.split("/")
        cur = ""
        for part in parts:
            if not part: continue
            cur = (cur + "/" + part) if cur else part
            try: sftp.stat(cur)
            except IOError: sftp.mkdir(cur)
    mkdirs(remote_dir)
    for root, _, files in os.walk(local_dir):
        rel = os.path.relpath(root, local_dir).replace("\\", "/")
        rdir = remote_dir if rel == "." else f"{remote_dir}/{rel}"
        mkdirs(rdir)
        for f in files:
            sftp.put(os.path.join(root, f), f"{rdir}/{f}")
    sftp.close(); c.close()

if __name__ == "__main__":
    host, cmd = sys.argv[1], sys.argv[2]
    o, e = run(host, cmd, timeout=int(sys.argv[3]) if len(sys.argv) > 3 else 300)
    print(o[-3000:] if o else "", e[-1000:] if e else "")
