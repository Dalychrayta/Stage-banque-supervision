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
docker compose up -d oracle kafka prometheus loki grafana
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

**5. Cible réelle — optionnel (`srv-002` / `auth-server-01`)**

Une des 5 ressources surveillées (`srv-002`) n'est pas simulée : c'est un ancien projet perso (`PlatformeBack`, hors de ce repo) relancé comme vraie cible de test, monitoré via son endpoint Actuator réel et réellement redémarré par l'auto-healing en cas d'incident. Sans cette cible, tout continue de fonctionner normalement — les 4 autres ressources restent simulées comme d'habitude, et `srv-002` apparaît juste `DOWN` sur le tableau de bord.

Pour l'activer, deux prérequis, **en dehors** de `infra/docker-compose.yml` (dépendance propre à cet ancien projet, pas au reste de la plateforme) :
```bash
# Sa base de données (démarrée une seule fois, puis conteneur réutilisé)
docker run -d --name platformeback-mysql -p 3306:3306 -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=platforme mysql:8.0
# ou, si déjà créé : docker start platformeback-mysql

cd /chemin/vers/PlatformeBack && mvn spring-boot:run   # écoute sur le port 8085
```

### Identifiants et secrets

L'API Gateway exige une authentification par jeton JWT (`POST /api/auth/login` avec `{username, password}`, renvoie un jeton à joindre en `Authorization: Bearer <token>` sur toutes les autres requêtes — voir [AuthController](services/api-gateway/src/main/java/com/bct/gateway/controller/AuthController.java) / [SecurityConfig](services/api-gateway/src/main/java/com/bct/gateway/config/SecurityConfig.java)).

**Aucun mot de passe n'a de valeur par défaut dans le code** — `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `JWT_SECRET` et `GRAFANA_ADMIN_PASSWORD` doivent être fournis via l'environnement, sinon les services concernés refusent de démarrer.

1. Copier le modèle : `cp infra/.env.example infra/.env`
2. Remplacer les valeurs par de vraies valeurs générées localement, par exemple :
   ```bash
   openssl rand -base64 48   # pour JWT_SECRET
   openssl rand -base64 18   # pour un mot de passe
   ```
3. `infra/.env` est ignoré par git (voir `.gitignore`) — ces secrets ne sont jamais committés.

Pour un lancement sans Docker (§ ci-dessus), exporter ces mêmes variables dans le terminal avant `mvn spring-boot:run` sur `api-gateway`.

## Lancement complet en Docker

Les `Dockerfile` de chaque service et un `docker-compose.yml` étendu (`infra/docker-compose.yml`) sont prêts pour un déploiement 100% conteneurisé (`docker compose up -d`). **Non vérifié en conditions réelles** faute de RAM suffisante sur la machine de développement (8 Go) — à valider après montée en mémoire.

## Intégration continue

Un `Jenkinsfile` déclaratif (build + tests, agents Docker par étage) est présent à la racine. **Jamais exécuté en conditions réelles** pour la même raison de RAM.

## Tests

| Service | Tests |
|---|---|
| discovery-service | JUnit / Mockito |
| rca-service | JUnit / Mockito |
| auto-healing-service | JUnit / Mockito |
| collector-service | JUnit / Mockito |
| api-gateway | JUnit — intégration sécurité (WebTestClient) |
| prediction-engine | pytest |
| frontend Angular | Jasmine/Karma — auth (service, guard, intercepteur) |
| eureka-server | aucun code applicatif à tester (`@EnableEurekaServer` seul) |

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
