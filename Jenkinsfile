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
        stage('Build — images Docker') {
            agent { node { label 'built-in'; customWorkspace 'ws-docker-images' } }
            steps {
                dir('bct-images') {
                    checkout scm
                    sh '''
                        # --memory borne chaque build pour éviter qu'une seule image
                        # (compilation Maven ou résolution pip) ne sature toute la VM
                        # Docker et ne fasse planter le moteur entier (vécu en pratique).
                        DOCKER_BUILD_LIMITS="--memory=2g --memory-swap=3g"
                        docker build $DOCKER_BUILD_LIMITS -t bct/eureka-server:${BUILD_NUMBER}       -t bct/eureka-server:latest       services/eureka-server
                        docker build $DOCKER_BUILD_LIMITS -t bct/api-gateway:${BUILD_NUMBER}        -t bct/api-gateway:latest        services/api-gateway
                        docker build $DOCKER_BUILD_LIMITS -t bct/discovery-service:${BUILD_NUMBER}  -t bct/discovery-service:latest  services/discovery-service
                        docker build $DOCKER_BUILD_LIMITS -t bct/collector-service:${BUILD_NUMBER}  -t bct/collector-service:latest  services/collector-service
                        docker build $DOCKER_BUILD_LIMITS -t bct/rca-service:${BUILD_NUMBER}        -t bct/rca-service:latest        services/rca-service
                        docker build $DOCKER_BUILD_LIMITS -t bct/auto-healing-service:${BUILD_NUMBER} -t bct/auto-healing-service:latest services/auto-healing-service
                        docker build $DOCKER_BUILD_LIMITS -t bct/prediction-engine:${BUILD_NUMBER}  -t bct/prediction-engine:latest  services/prediction-engine
                        docker build $DOCKER_BUILD_LIMITS -t bct/frontend:${BUILD_NUMBER}           -t bct/frontend:latest           frontend/bct-dashboard
                    '''
                }
            }
            post {
                success {
                    echo "8 images Docker construites et tagguées bct/*:${BUILD_NUMBER} — prêtes pour docker-compose ou un registre."
                }
            }
        }
    }

    post {
        success { echo 'Build + tests OK sur tous les services.' }
        failure { echo 'Echec du pipeline — voir les logs des étapes ci-dessus.' }
    }
}
