FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_DISABLE_PIP_VERSION_CHECK=1
RUN groupadd -g 10001 agent && useradd -u 10001 -g agent -M agent
WORKDIR /workspace


# 预装 Python 包
COPY requirement.txt /tmp/requirement.txt

RUN pip install --no-cache-dir \
    -r /tmp/requirement.txt
    
USER 10001:10001
CMD ["sleep", "infinity"]
