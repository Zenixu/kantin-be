pipeline {
    agent any

    environment {
        REGISTRY      = "100.123.181.208:5000"
        IMAGE_NAME    = "${REGISTRY}/skoolia/kantin-be"
        IMAGE_TAG     = "${BUILD_NUMBER}"
        K8S_NAMESPACE = "skoolia"
        K8S_DEPLOY    = "kantin-be"
        CONTAINER     = "kantin-be"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "Branch: ${env.GIT_BRANCH} | Commit: ${env.GIT_COMMIT?.take(7)}"
            }
        }

        stage('Test') {
            // Gerbang mutu (issue #141, WORKFLOW.md §4): `verify` menjalankan
            // unit test (Surefire, *Test) + integration test (Failsafe, *IT)
            // berbasis Testcontainers. Agent Jenkins sudah punya Docker (dipakai
            // build image di stage berikut), jadi IT ledger/race-condition
            // benar-benar dijalankan. Pipeline GAGAL bila ada test merah.
            steps {
                sh './mvnw -B -ntp verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/*-reports/*.xml'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh """
                    docker build \
                        -t ${IMAGE_NAME}:${IMAGE_TAG} \
                        -t ${IMAGE_NAME}:latest \
                        -f Dockerfile .
                """
            }
        }

        stage('Push to Registry') {
            steps {
                sh """
                    docker push ${IMAGE_NAME}:${IMAGE_TAG}
                    docker push ${IMAGE_NAME}:latest
                """
            }
        }

        stage('Deploy to k3s') {
            steps {
                sh """
                    kubectl set image deployment/${K8S_DEPLOY} \
                        ${CONTAINER}=${IMAGE_NAME}:${IMAGE_TAG} \
                        -n ${K8S_NAMESPACE}

                    kubectl rollout status deployment/${K8S_DEPLOY} -n ${K8S_NAMESPACE} --timeout=600s
                """
            }
        }
    }

    post {
        success {
            echo "Deploy kantin-be:${IMAGE_TAG} berhasil."
        }
        failure {
            echo "Deploy gagal — rollback ke versi sebelumnya..."
            sh """
                kubectl rollout undo deployment/${K8S_DEPLOY} \
                    -n ${K8S_NAMESPACE} || true
            """
        }
        always {
            // Hapus image lokal untuk hemat disk Jenkins
            sh "docker rmi ${IMAGE_NAME}:${IMAGE_TAG} || true"
        }
    }
}
