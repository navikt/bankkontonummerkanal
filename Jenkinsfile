#!/usr/bin/env groovy

pipeline {
    agent any
    tools {
        maven 'maven-3.3.9'
    }

    environment {
        APPLICATION_NAME = 'bankkontonummerkanal'
        APPLICATION_SERVICE = 'CMDB-240125'
        APPLICATION_COMPONENT = 'CMDB-190499'
        FASIT_ENVIRONMENT = 'q1'
        ZONE = 'fss'
        DOCKER_SLUG = 'integrasjon'
    }

    stages {
        stage('setup') {
            steps {
                init action: 'default'
                script {
                    pom = readMavenPom file: 'pom.xml'
                    env.APPLICATION_VERSION = "${pom.version}"
                    if (env.APPLICATION_VERSION.endsWith('-SNAPSHOT')) {
                        env.APPLICATION_VERSION = "${env.APPLICATION_VERSION}.${env.BUILD_ID}-${env.COMMIT_HASH_SHORT}"
                    } else {
                        env.DEPLOY_TO = 'production'
                    }
                }
                init action: 'updateStatus'
            }
        }
        stage('build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }
        stage('run unit and integration tests') {
            steps {
                sh 'mvn verify'
                slackStatus status: 'passed'
            }
        }
        stage('docker build') {
            steps {
                dockerUtils action: 'createPushImage'
            }
        }
        stage('validate & upload nais.yaml to nexus m2internal') {
            steps {
                nais action: 'validate'
                nais action: 'upload'
            }
        }

        stage('deploy to preprod') {
            steps {
                deploy action: 'jiraPreprod'
            }
        }
        stage('deploy to production') {
            when { environment name: 'DEPLOY_TO', value: 'production' }
            steps {
                deploy action: 'jiraProd'
            }
        }
    }

    post {
        always {
            postProcess action: 'always'
            junit 'target/surefire-reports/*.xml'
            archive 'target/bankkontonummer-kanal-*.jar'
        }
        success {
            postProcess action: 'success'
        }
        failure {
            postProcess action: 'failure'
        }
    }
}
