FROM m.daocloud.io/docker.io/library/python:3.13
ENV PYTHONUNBUFFERED=1 OPENBLAS_CORETYPE=HASWELL NPY_BLAS_ORDER=generic NPY_LAPACK_ORDER=generic
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
COPY models/ ./models/
COPY serve/ ./serve/
COPY checkpoints/ ./checkpoints/
ENV KT_CKPT_DIR=/app/checkpoints
EXPOSE 8086
# Granian: Rust 实现的 ASGI 服务器(多 worker, 高吞吐)
CMD ["granian", "--interface", "asgi", "--host", "0.0.0.0", "--port", "8086", "--workers", "2", "serve.app:app"]
