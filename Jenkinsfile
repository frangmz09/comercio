// Pipeline equivalente al de GitHub Actions (.github/workflows/ci.yml).
//
// El CI real del proyecto es GitHub Actions: corre en cada push y deja badge y logs
// publicos. Este Jenkinsfile existe porque Jenkins sigue siendo el motor de CI mas
// comun en entornos corporativos, y las mismas etapas expresadas en los dos modelos
// muestran que el pipeline no depende de la herramienta.
//
// Para correrlo sin infraestructura propia:
//   docker compose -f docker-compose.jenkins.yml up
// y crear un job Pipeline "from SCM" apuntando a este archivo.

pipeline {
    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        // Testcontainers necesita hablar con el daemon de Docker del host.
        DOCKER_HOST = 'unix:///var/run/docker.sock'
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository'
    }

    stages {
        stage('Build') {
            steps {
                sh './mvnw -B -DskipTests clean package'
            }
        }

        stage('Tests unitarios') {
            steps {
                sh './mvnw -B surefire:test'
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/TEST-*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Tests de integracion') {
            // Levantan PostgreSQL y Kafka reales con Testcontainers, por eso el agente
            // necesita acceso al socket de Docker.
            steps {
                sh './mvnw -B verify'
            }
            post {
                always {
                    junit testResults: '**/target/failsafe-reports/TEST-*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Cobertura') {
            steps {
                // El reporte lo genera JaCoCo en la fase verify; aca solo se publica.
                recordCoverage(
                    tools: [[parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml']],
                    sourceCodeRetention: 'EVERY_BUILD'
                )
            }
        }

        stage('Analisis SonarQube') {
            when {
                expression { return env.SONAR_TOKEN?.trim() }
            }
            steps {
                // Marca el build como UNSTABLE en vez de FAILURE, igual que el
                // continue-on-error del workflow de Actions: el analisis es un informe y
                // no una compuerta, pero tiene que verse cuando no corre. Sin esto, una
                // caida de SonarCloud dejaria en rojo un build cuyos tests pasaron.
                catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                    sh './mvnw -B sonar:sonar'
                }
            }
        }

        stage('Imagen Docker') {
            when {
                branch 'main'
            }
            steps {
                sh 'docker build -f core/Dockerfile -t comercio-core:${BUILD_NUMBER} .'
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/target/*.jar', allowEmptyArchive: true, fingerprint: true
        }
        cleanup {
            cleanWs()
        }
    }
}
