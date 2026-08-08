# BCT Supervision Platform

Plateforme intelligente de supervision prédictive et d'auto-remédiation des infrastructures et applications bancaires — Banque Centrale de Tunisie.

## Stack Technique

- **Frontend** : Angular
- **Backend** : Java 17, Spring Boot 3, Spring Cloud (Gateway, Eureka)
- **Messagerie** : Apache Kafka
- **Observabilité** : Prometheus, Grafana, Loki
- **IA / ML** : Python, FastAPI, scikit-learn (Isolation Forest)
- **Base de données** : Oracle
- **DevOps** : Docker, Jenkins, Nginx

## Structure du projet

```
bct-supervision/
├── infra/                        # Docker Compose — Oracle, Kafka, Prometheus, Loki, Grafana
├── services/
│   ├── eureka-server/            # Service Registry (Spring Boot)
│   ├── api-gateway/              # API Gateway (Spring Cloud Gateway)
│   ├── discovery-service/        # Discovery des ressources supervisées
│   ├── collector-service/        # Collecte métriques + logs
│   ├── prediction-engine/        # Détection d'anomalies ML (Python FastAPI)
│   ├── rca-service/              # Root Cause Analysis
│   └── auto-healing-service/     # Auto-Healing Engine
├── frontend/                     # Dashboard Angular
└── README.md
```

## Modules fonctionnels

| Module | Description | Priorité |
|--------|-------------|----------|
| eureka-server | Annuaire de services (Service Registry) | P1 |
| api-gateway | Point d'entrée unique vers les micro-services | P1 |
| discovery-service | Inventaire automatique des ressources | P1 |
| collector-service | Collecte métriques (CPU, RAM...) et logs | P1 |
| prediction-engine | Détection anomalies + prédiction pannes (ML) | P1 — CENTRAL |
| rca-service | Analyse cause racine d'une anomalie | P1 |
| auto-healing-service | Actions correctives automatiques | P1 |
| frontend | Dashboard temps réel Angular | P1 |

## Lancement (à compléter au fil du projet)

### Infra (Docker)
```bash
cd infra
docker-compose up -d
```

### Services Spring Boot
```bash
cd services/eureka-server
./mvnw spring-boot:run
```

### Prediction Engine (Python)
```bash
cd services/prediction-engine
uvicorn main:app --reload
```

### Frontend (Angular)
```bash
cd frontend
ng serve
```
