# Manzil conversational agent, Cloud Run image (pilot).
# Build context is backend/:  docker build -f cloud/Dockerfile -t <image> .
# Copies only the three agent modules and the pinned requirements: no other app/ modules, no data,
# no .env, no venv, no credentials. ADC comes from the Cloud Run service account at runtime.
FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /srv

COPY cloud/requirements.txt /srv/requirements.txt
RUN pip install --no-cache-dir -r /srv/requirements.txt

COPY app/cloud_agent.py app/agent_turn.py app/gemini_agent.py /srv/app/

RUN useradd --system --uid 10001 --no-create-home manzil
USER 10001

EXPOSE 8080
# --factory: create_app() reads MANZIL_AGENT_ACCESS_TOKEN and refuses to start without it.
CMD ["uvicorn", "--factory", "app.cloud_agent:create_app", "--host", "0.0.0.0", "--port", "8080", "--no-server-header"]
