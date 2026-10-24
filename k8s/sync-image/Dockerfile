FROM 10.167.2.175:30500/base/python:3.13-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
COPY sync_tidb_to_crdb.py /app/sync_tidb_to_crdb.py
ENTRYPOINT ["python", "/app/sync_tidb_to_crdb.py"]
