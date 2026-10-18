# 仅 CI 训练用(不在集群部署): torch CPU 版
FROM python:3.13-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu &&     pip install --no-cache-dir numpy -i https://pypi.tuna.tsinghua.edu.cn/simple
COPY . .
CMD ["python", "train_all.py", "--data", "/data"]
