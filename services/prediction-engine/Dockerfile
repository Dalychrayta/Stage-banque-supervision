FROM python:3.12-slim
WORKDIR /app
# Met a jour les paquets systeme de l'image de base (perl-base, openssl...).
# Equivalent Debian du "apk upgrade" des images Alpine du projet : sans cette
# ligne on depend de la date a laquelle l'editeur de l'image republie la
# sienne, et un correctif deja publie peut rester absent pendant des semaines.
RUN apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .
COPY app ./app

EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
