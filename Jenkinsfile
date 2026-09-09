pipeline {
    agent none

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    stages {

        stage('Build — services Spring Boot') {
            parallel {
                stage('eureka-server') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2'; customWorkspace 'ws-eureka-server' } }
                    steps { dir('services/eureka-server') { sh 'mvn -B compile' } }
                }
                stage('api-gateway') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2'; customWorkspace 'ws-api-gateway' } }
                    steps { dir('services/api-gateway') { sh 'mvn -B compile' } }
                }
                stage('discovery-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2'; customWorkspace 'ws-discovery-service' } }
                    steps { dir('services/discovery-service') { sh 'mvn -B compile' } }
                }
                stage('collector-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2'; customWorkspace 'ws-collector-service' } }
                    steps { dir('services/collector-service') { sh 'mvn -B compile' } }
                }
                stage('rca-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2'; customWorkspace 'ws-rca-service' } }
                    steps { dir('services/rca-service') { sh 'mvn -B compile' } }
                }
                stage('auto-healing-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2'; customWorkspace 'ws-auto-healing-service' } }
                    steps { dir('services/auto-healing-service') { sh 'mvn -B compile' } }
                }
            }
        }

        stage('Test — services Spring Boot') {
            parallel {
                stage('discovery-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2'; customWorkspace 'ws-discovery-service' } }
                    steps { dir('services/discovery-service') { sh 'mvn -B test' } }
                    post {
                        always { junit testResults: 'services/discovery-service/target/surefire-reports/*.xml', allowEmptyResults: true }
                    }
                }
                stage('rca-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2'; customWorkspace 'ws-rca-service' } }
                    steps { dir('services/rca-service') { sh 'mvn -B test' } }
                    post {
                        always { junit testResults: 'services/rca-service/target/surefire-reports/*.xml', allowEmptyResults: true }
                    }
                }
                stage('collector-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2'; customWorkspace 'ws-collector-service' } }
                    steps { dir('services/collector-service') { sh 'mvn -B test' } }
                    post {
                        always { junit testResults: 'services/collector-service/target/surefire-reports/*.xml', allowEmptyResults: true }
                    }
                }
                stage('auto-healing-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2'; customWorkspace 'ws-auto-healing-service' } }
                    steps { dir('services/auto-healing-service') { sh 'mvn -B test' } }
                    post {
                        always { junit testResults: 'services/auto-healing-service/target/surefire-reports/*.xml', allowEmptyResults: true }
                    }
                }
                stage('api-gateway') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2'; customWorkspace 'ws-api-gateway' } }
                    steps { dir('services/api-gateway') { sh 'mvn -B test' } }
                    post {
                        always { junit testResults: 'services/api-gateway/target/surefire-reports/*.xml', allowEmptyResults: true }
                    }
                }
            }
        }

        stage('Test — Prediction Engine (Python)') {
            agent { docker { image 'python:3.12-slim'; customWorkspace 'ws-prediction-engine' } }
            steps {
                dir('services/prediction-engine') {
                    sh '''
                        pip install --no-cache-dir -r requirements.txt -r requirements-test.txt
                        pytest tests/ -v --junitxml=test-results.xml
                    '''
                }
            }
            post {
                always { junit testResults: 'services/prediction-engine/test-results.xml', allowEmptyResults: true }
            }
        }

        stage('Build — Frontend Angular') {
            agent { docker { image 'node:22-alpine'; customWorkspace 'ws-frontend' } }
            steps {
                dir('frontend/bct-dashboard') {
                    sh '''
                        npm install
                        npm run build
                    '''
                }
            }
        }

        // Ne s'exécute que si toutes les compilations et tous les tests ci-dessus
        // ont réussi — jamais construire (encore moins déployer) une image à
        // partir d'un code qui ne compile pas ou dont les tests échouent.
        // Tourne sur le nœud Jenkins lui-même (pas un agent docker) car c'est
        // là que le CLI docker + le socket de l'hôte sont disponibles.
        // Les images sont taguées directement au nom du registre GHCR : pas
        // besoin d'un nom local intermédiaire (bct/*) puis d'un retag.
        stage('Build — images Docker') {
            agent { node { label 'built-in'; customWorkspace 'ws-docker-images' } }
            environment {
                REGISTRY  = 'ghcr.io'
                NAMESPACE = 'dalychrayta'
            }
            steps {
                dir('bct-images') {
                    checkout scm
                    sh '''
                        # --memory borne chaque build pour éviter qu'une seule image
                        # (compilation Maven ou résolution pip) ne sature toute la VM
                        # Docker et ne fasse planter le moteur entier (vécu en pratique).
                        DOCKER_BUILD_LIMITS="--memory=2g --memory-swap=3g"
                        IMG=${REGISTRY}/${NAMESPACE}/bct
                        docker build $DOCKER_BUILD_LIMITS -t ${IMG}-eureka-server:${BUILD_NUMBER}        -t ${IMG}-eureka-server:latest        services/eureka-server
                        docker build $DOCKER_BUILD_LIMITS -t ${IMG}-api-gateway:${BUILD_NUMBER}         -t ${IMG}-api-gateway:latest          services/api-gateway
                        docker build $DOCKER_BUILD_LIMITS -t ${IMG}-discovery-service:${BUILD_NUMBER}   -t ${IMG}-discovery-service:latest    services/discovery-service
                        docker build $DOCKER_BUILD_LIMITS -t ${IMG}-collector-service:${BUILD_NUMBER}   -t ${IMG}-collector-service:latest    services/collector-service
                        docker build $DOCKER_BUILD_LIMITS -t ${IMG}-rca-service:${BUILD_NUMBER}         -t ${IMG}-rca-service:latest          services/rca-service
                        docker build $DOCKER_BUILD_LIMITS -t ${IMG}-auto-healing-service:${BUILD_NUMBER} -t ${IMG}-auto-healing-service:latest services/auto-healing-service
                        docker build $DOCKER_BUILD_LIMITS -t ${IMG}-prediction-engine:${BUILD_NUMBER}   -t ${IMG}-prediction-engine:latest    services/prediction-engine
                        docker build $DOCKER_BUILD_LIMITS -t ${IMG}-frontend:${BUILD_NUMBER}            -t ${IMG}-frontend:latest             frontend/bct-dashboard
                    '''
                }
            }
            post {
                success {
                    echo "8 images Docker construites et tagguées ${REGISTRY}/${NAMESPACE}/bct-*:${BUILD_NUMBER} et :latest."
                }
            }
        }

        // Pousse les 8 images vers GitHub Container Registry. N'importe quelle
        // machine (ou un vrai serveur) peut ensuite les récupérer avec un
        // simple `docker pull` — ce n'est plus piégé sur ce seul poste.
        stage('Push — registre GHCR') {
            agent { node { label 'built-in'; customWorkspace 'ws-docker-images' } }
            environment {
                REGISTRY  = 'ghcr.io'
                NAMESPACE = 'dalychrayta'
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'ghcr-credential', usernameVariable: 'GHCR_USER', passwordVariable: 'GHCR_TOKEN')]) {
                    sh '''
                        echo "$GHCR_TOKEN" | docker login ${REGISTRY} -u "$GHCR_USER" --password-stdin
                        IMG=${REGISTRY}/${NAMESPACE}/bct
                        for svc in eureka-server api-gateway discovery-service collector-service rca-service auto-healing-service prediction-engine frontend; do
                            docker push ${IMG}-${svc}:${BUILD_NUMBER}
                            docker push ${IMG}-${svc}:latest
                        done
                        docker logout ${REGISTRY}
                    '''
                }
            }
            post {
                success {
                    // Nettoyage disque : le tag numéroté du build reste dans le
                    // registre (historique complet), inutile de le garder en
                    // double en local — on ne conserve que :latest sur ce poste.
                    sh '''
                        IMG=${REGISTRY}/${NAMESPACE}/bct
                        for svc in eureka-server api-gateway discovery-service collector-service rca-service auto-healing-service prediction-engine frontend; do
                            docker rmi ${IMG}-${svc}:${BUILD_NUMBER} || true
                        done
                    '''
                }
            }
        }

        // Déploiement réel : récupère les images qu'on vient de pousser et
        // (re)démarre uniquement les services applicatifs dont l'image a
        // changé — docker compose laisse l'infra déjà saine (oracle, kafka,
        // prometheus...) intacte si sa configuration n'a pas bougé.
        stage('Deploy') {
            agent { node { label 'built-in'; customWorkspace 'ws-docker-images' } }
            options { timeout(time: 10, unit: 'MINUTES') }
            environment {
                REGISTRY = 'ghcr.io'
            }
            steps {
                // Les paquets GHCR sont privés par défaut, et le stage précédent
                // s'est déconnecté à la fin — il faut se reconnecter ici pour
                // que le `pull` soit autorisé.
                withCredentials([usernamePassword(credentialsId: 'ghcr-credential', usernameVariable: 'GHCR_USER', passwordVariable: 'GHCR_TOKEN')]) {
                    dir('bct-images/infra') {
                        sh '''
                            echo "$GHCR_TOKEN" | docker login ${REGISTRY} -u "$GHCR_USER" --password-stdin
                            SERVICES="eureka-server api-gateway discovery-service collector-service rca-service auto-healing-service prediction-engine frontend"
                            docker compose pull $SERVICES
                            docker compose up -d $SERVICES
                            docker logout ${REGISTRY}
                        '''
                    }
                }
            }
            post {
                success {
                    echo "Déploiement terminé — les 8 services applicatifs tournent avec les images ghcr.io/dalychrayta/bct-*:latest fraîchement poussées."
                }
            }
        }
    }

    post {
        success { echo 'Build + tests + images + déploiement OK sur tous les services.' }
        failure { echo 'Echec du pipeline — voir les logs des étapes ci-dessus.' }
    }
}
