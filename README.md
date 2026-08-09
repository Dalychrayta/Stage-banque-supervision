# BCT Supervision Platform

Plateforme intelligente de supervision prédictive et d'auto-remédiation des infrastructures et applications bancaires — Banque Centrale de Tunisie (PFA/PFE).

## Stack technique

- **Frontend** : Angular 18 (standalone components, SSR)
- **Backend** : Java 21, Spring Boot 3.4, Spring Cloud (Gateway, Eureka)
- **Sécurité** : Spring Security (HTTP Basic) sur l'API Gateway
- **Messagerie** : Apache Kafka
- **Observabilité** : Prometheus, Grafana, Loki
- **IA / ML** : Python, FastAPI, scikit-learn (Isolation Forest)
- **Base de données** : Oracle XE 21
- **DevOps** : Docker, Jenkins, Nginx

## Architecture — le pipeline

```
collector-service  →  Kafka (metrics-collected)  →  prediction-engine (détection ML)
                                                            │
                                                    Kafka (anomaly-detected)
                                                            ▼
                                                       rca-service (cause racine)
                                                            │
                                                     Kafka (rca-result)
                                                            ▼
                                                  auto-healing-service (remédiation)
                                                            │
                                          si action automatique réussie → résout l'incident RCA
```

Toutes les requêtes du frontend passent par **api-gateway** (seul point d'entrée exposé, avec authentification), qui route vers chaque micro-service via **eureka-server** (annuaire de services).

## Structure du projet

```
bct-supervision/
├── infra/                        # Docker Compose — Oracle, Kafka, Prometheus, Loki, Grafana
├── services/
│   ├── eureka-server/            # Annuaire de services (Spring Boot)      — port 8761
│   ├── api-gateway/               # Point d'entrée unique + auth           — port 8080
│   ├── discovery-service/        # Inventaire des ressources supervisées  — port 8081
│   ├── collector-service/        # Collecte métriques + logs              — port 8082
│   ├── rca-service/               # Root Cause Analysis                    — port 8083
│   ├── auto-healing-service/     # Actions correctives automatiques       — port 8084
│   └── prediction-engine/        # Détection d'anomalies ML (FastAPI)     — port 8000
├── frontend/bct-dashboard/       # Dashboard Angular                       — port 4200
├── Jenkinsfile
└── README.md
```

## Lancement en local (sans Docker)

Pré-requis : JDK 21, Maven, Node 22+, Python 3.12, Docker Desktop (pour l'infra légère uniquement).

**1. Infrastructure (Oracle, Kafka, Prometheus, Loki, Grafana)**
```bash
cd infra
docker compose up -d oracle zookeeper kafka prometheus loki grafana
```

**2. Services Spring Boot** (à lancer dans cet ordre — chacun dans son propre terminal)
```bash
cd services/eureka-server        && mvn spring-boot:run
cd services/api-gateway          && mvn spring-boot:run
cd services/discovery-service    && mvn spring-boot:run
cd services/collector-service    && mvn spring-boot:run
cd services/rca-service          && mvn spring-boot:run
cd services/auto-healing-service && mvn spring-boot:run
```

**3. Prediction Engine (Python)**
```bash
cd services/prediction-engine
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

**4. Frontend (Angular)**
```bash
cd frontend/bct-dashboard
npm install
ng serve
```

Puis ouvrir **http://localhost:4200**.

### Identifiants

L'API Gateway exige une authentification HTTP Basic. Identifiants par défaut (modifiables via les variables d'environnement `ADMIN_USERNAME` / `ADMIN_PASSWORD`) :

| Utilisateur | Mot de passe |
|-------------|--------------|
| `admin`     | `bct2026`    |

## Lancement complet en Docker

Les `Dockerfile` de chaque service et un `docker-compose.yml` étendu (`infra/docker-compose.yml`) sont prêts pour un déploiement 100% conteneurisé (`docker compose up -d`). **Non vérifié en conditions réelles** faute de RAM suffisante sur la machine de développement (8 Go) — à valider après montée en mémoire.

## Intégration continue

Un `Jenkinsfile` déclaratif (build + tests, agents Docker par étage) est présent à la racine. **Jamais exécuté en conditions réelles** pour la même raison de RAM.

## Tests

| Service | Tests |
|---|---|
| discovery-service | JUnit / Mockito |
| rca-service | JUnit / Mockito |
| prediction-engine | pytest |
| api-gateway, collector-service, auto-healing-service, eureka-server | à faire |
| frontend Angular | à faire |

## Observabilité

- **Prometheus** : http://localhost:9090
- **Grafana** : http://localhost:3000 (dashboard provisionné automatiquement)
- **Loki** : logs centralisés de tous les services Spring Boot, requêtables depuis Grafana

## Modules fonctionnels

| Module | Description | Priorité |
|--------|-------------|----------|
| eureka-server | Annuaire de services (Service Registry) | P1 |
| api-gateway | Point d'entrée unique + authentification | P1 |
| discovery-service | Inventaire automatique des ressources | P1 |
| collector-service | Collecte métriques (CPU, RAM...) et logs | P1 |
| prediction-engine | Détection anomalies + prédiction pannes (ML, Isolation Forest) | P1 — CENTRAL |
| rca-service | Analyse cause racine d'une anomalie | P1 |
| auto-healing-service | Actions correctives automatiques | P1 |
| frontend | Dashboard temps réel Angular | P1 |
| Authentification fine (rôles, SSO) | Au-delà du Basic Auth actuel | P2 |
