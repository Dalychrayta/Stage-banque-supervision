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

La plateforme ne surveille **aucune ressource simulée**. Les 4 ressources fabriquées d'origine ont été retirées en cours de projet (commit `478416a`) : leurs données inventées faussaient l'apprentissage du modèle, qui apprenait un comportement qui n'existe pas. Le modèle est depuis entraîné uniquement sur les vraies métriques de `srv-002`.

La seule ressource supervisée est donc réelle : `PlatformeBack`, un ancien projet perso (hors de ce repo) relancé comme vraie cible de test, dont les métriques sont lues sur son endpoint Actuator réel et sur laquelle l'auto-remédiation agit réellement. Si la cible n'est pas démarrée, la plateforme continue de tourner normalement : `srv-002` apparaît simplement `DOWN` sur le tableau de bord — ce qui est d'ailleurs le comportement attendu, c'est exactement ce que le collecteur sert à détecter.

La cible est **conteneurisée** (services `platformeback` et `platformeback-mysql`). Ce n'est pas un détail de confort : `auto-healing-service` tourne lui-même dans un conteneur, et un conteneur est isolé de la machine hôte par conception — il ne pourrait ni voir ni redémarrer un processus lancé à la main sur Windows. En mettant la cible dans le même monde, les deux actions réelles deviennent possibles :

| Action | Mécanisme réel |
|---|---|
| `RESTART_SERVICE` / `KILL_PROCESS` | `docker restart bct-platformeback`, via le socket Docker monté dans `auto-healing-service` |
| `FREE_DISK_SPACE` | suppression des logs archivés de plus de 7 jours dans le volume `platformeback-logs`, partagé avec la cible |

La portée est volontairement étroite : le code ne sait nommer **qu'un** conteneur et **qu'un** dossier (`REAL_TARGET_CONTAINER`, `REAL_TARGET_LOG_DIR`). Le fichier de log actif et tout fichier étranger sont systématiquement épargnés.

Seul prérequis : indiquer où se trouve le projet sur la machine, dans `infra/.env` :
```bash
PLATFORMEBACK_PATH=C:/chemin/vers/PlatformeBack
PLATFORMEBACK_DB_PASSWORD=<généré localement>
```
Puis, comme les autres services : `docker compose up -d platformeback` (la cible écoute sur le port 8085).

### Authentification et rôles (Keycloak)

L'identité est gérée par **Keycloak** (conteneur `bct-keycloak`, realm `bct`, console d'admin sur http://localhost:8180). L'API Gateway est un *resource server* OAuth2 : il ne fabrique aucun jeton, il vérifie ceux émis par Keycloak et lit les rôles depuis `realm_access.roles` (voir [SecurityConfig](services/api-gateway/src/main/java/com/bct/gateway/config/SecurityConfig.java)). Le frontend redirige vers l'écran de connexion Keycloak (flux OIDC *code* + PKCE).

**Trois rôles :**

| Rôle | Peut |
|---|---|
| `VIEWER` | consulter le tableau de bord et l'historique — aucune action |
| `OPERATOR` | + déclencher une réparation, résoudre un incident, corriger un diagnostic |
| `ADMIN` | + gérer les comptes et la configuration (dans la console Keycloak) |

**Comptes de démo** (mots de passe de développement, dans `infra/keycloak/realm-bct.json` — à changer pour tout usage réel) :

| Utilisateur | Mot de passe | Rôle |
|---|---|---|
| `rh` | `dali1234` | VIEWER |
| `operator` | `dali1234` | OPERATOR |
| `admin` | `dali1234` | ADMIN |

### Journal d'audit — qui a demandé quoi, et pourquoi

Chaque action de remédiation enregistre son **acteur** et son **motif**. Le champ n'est jamais vide :

| | Acteur | Motif |
|---|---|---|
| Action humaine | `utilisateur:operator` | la justification saisie par l'opérateur, obligatoire |
| Action automatique | `systeme:auto-healing` | la règle appliquée et l'incident d'origine, ex. `Regle DISK_FULL -> FREE_DISK_SPACE, incident #77` |

Pour un humain la question est « qui a décidé », pour la machine c'est « sur quelle base ». Figer la règle dans l'enregistrement permet d'expliquer une action passée même si la table cause → action change ensuite.

**Le nom n'est pas déclaré par le client, il est prouvé.** `auto-healing-service` est lui aussi un *resource server* OAuth2 ([SecurityConfig](services/auto-healing-service/src/main/java/com/bct/healing/config/SecurityConfig.java)) : il vérifie le jeton Keycloak et lit le nom dedans. C'est indispensable ici, parce que ce service exécute de vraies actions — si le nom arrivait dans un simple en-tête posé par la passerelle, quiconque peut joindre le service (port 8084) écrirait le nom de son choix, et le journal deviendrait falsifiable.

Vérifié en direct sur le port 8084, sans passer par la passerelle : sans jeton → `401`, avec `rh` → `403`, sans motif → `400`, avec `operator` et un motif → l'action s'exécute réellement et la trace porte son nom.

### Secrets

**Aucun mot de passe n'a de valeur par défaut dans le code.** `KEYCLOAK_ADMIN_PASSWORD`, `GRAFANA_ADMIN_PASSWORD`, `ORACLE_SYS_PASSWORD` et les mots de passe de base par service doivent être fournis via l'environnement.

1. Copier le modèle : `cp infra/.env.example infra/.env`
2. Remplacer les valeurs par de vraies valeurs (`openssl rand -base64 18`)
3. `infra/.env` est ignoré par git — ces secrets ne sont jamais committés

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
