FROM 10.167.2.175:30500/base/python:3.13-slim
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
COPY app/ ./app/
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s CMD python -c "import urllib.request;urllib.request.urlopen('http://localhost:8090/health')"
# Granian: Rust ASGI 服务器, 多 worker 高吞吐
CMD ["granian", "--interface", "asgi", "--host", "0.0.0.0", "--port", "8090", "--workers", "2", "app.main:app"]
