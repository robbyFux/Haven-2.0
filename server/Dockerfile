# Multi-stage build: builder installs dependencies, runtime is the lean image.

# --- Builder stage ---
FROM python:3.12-slim AS builder

WORKDIR /build

# Install build dependencies for native extensions (asyncpg, bcrypt, argon2-cffi, cryptography)
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libffi-dev \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

# Upgrade pip and install all project dependencies into /venv
COPY pyproject.toml .
RUN python -m venv /venv \
    && /venv/bin/pip install --upgrade pip \
    && /venv/bin/pip install .

# --- Runtime stage ---
FROM python:3.12-slim AS runtime

WORKDIR /app

# Copy virtual environment from builder
COPY --from=builder /venv /venv

# Copy application source
COPY app/ ./app/
COPY alembic/ ./alembic/
COPY alembic.ini .

# Create media directory
RUN mkdir -p /app/media

# Add venv to PATH
ENV PATH="/venv/bin:$PATH"

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
