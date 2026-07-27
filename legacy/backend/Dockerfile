FROM python:3.11-slim

# tesseract enables OCR for scanned documents (FR-3)
RUN apt-get update && apt-get install -y --no-install-recommends tesseract-ocr curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY alembic ./alembic
COPY alembic.ini .

RUN useradd -m mindquest && mkdir -p /srv/data/uploads && chown -R mindquest:mindquest /srv
USER mindquest

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s CMD curl -sf http://localhost:8000/healthz || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
