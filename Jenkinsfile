pipeline {
    agent none

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {

        stage('Build — services Spring Boot') {
            parallel {
                stage('eureka-server') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2' } }
                    steps { dir('services/eureka-server') { sh 'mvn -B compile' } }
                }
                stage('api-gateway') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2' } }
                    steps { dir('services/api-gateway') { sh 'mvn -B compile' } }
                }
                stage('discovery-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2' } }
                    steps { dir('services/discovery-service') { sh 'mvn -B compile' } }
                }
                stage('collector-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2' } }
                    steps { dir('services/collector-service') { sh 'mvn -B compile' } }
                }
                stage('rca-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2' } }
                    steps { dir('services/rca-service') { sh 'mvn -B compile' } }
                }
                stage('auto-healing-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2' } }
                    steps { dir('services/auto-healing-service') { sh 'mvn -B compile' } }
                }
            }
        }

        stage('Test — services Spring Boot') {
            parallel {
                stage('discovery-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2' } }
                    steps { dir('services/discovery-service') { sh 'mvn -B test' } }
                    post {
                        always { junit testResults: 'services/discovery-service/target/surefire-reports/*.xml', allowEmptyResults: true }
                    }
                }
                stage('rca-service') {
                    agent { docker { image 'maven:3.9-eclipse-temurin-21'; args '-v maven-repo:/root/.m2' } }
                    steps { dir('services/rca-service') { sh 'mvn -B test' } }
                    post {
                        always { junit testResults: 'services/rca-service/target/surefire-reports/*.xml', allowEmptyResults: true }
                    }
                }
            }
        }

        stage('Test — Prediction Engine (Python)') {
            agent { docker { image 'python:3.12-slim' } }
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
            agent { docker { image 'node:22-alpine' } }
            steps {
                dir('frontend/bct-dashboard') {
                    sh '''
                        npm install
                        npm run build
                    '''
                }
            }
        }
    }

    post {
        success { echo 'Build + tests OK sur tous les services.' }
        failure { echo 'Echec du pipeline — voir les logs des étapes ci-dessus.' }
    }
}
