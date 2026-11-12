# Image du module Invités. Construire depuis ce dossier :  docker build -t invites-service .
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# Les dépendances d'abord : cette couche est réutilisée tant que requirements.txt ne change pas.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# Pas d'exécution en root.
RUN useradd --system --no-create-home appuser
USER appuser

EXPOSE 5003

# La configuration vient des variables d'environnement (DATABASE_URL, EVENEMENTS_SERVICE_URL, ...) :
# aucun fichier .env n'est copié dans l'image.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "5003"]
