#!/usr/bin/env groovy

pipeline {
    agent any
    tools {
        maven 'maven-3.3.9'
    }

    environment {
        APPLICATION_NAME = 'bankkontonummerkanal'
        FASIT_ENV = 'q1'
        ZONE = 'fss'
        NAMESPACE = 'default'
        COMMIT_HASH_SHORT = gitVars 'commitHashShort'
        COMMIT_HASH = gitVars 'commitHash'
        APPLICATION_SERVICE = 'CMDB-240125'
        APPLICATION_COMPONENT = 'CMDB-190499'
    }

    stages {
        stage('setup') {
            steps {
                ciSkip 'check'
                script {
                    pom = readMavenPom file: 'pom.xml'
                    env.APPLICATION_VERSION = "${pom.version}"
                    if (env.APPLICATION_VERSION.endsWith('-SNAPSHOT')) {
                        env.APPLICATION_VERSION = "${env.APPLICATION_VERSION}.${env.BUILD_ID}-${env.COMMIT_HASH_SHORT}"
                    } else {
                        env.DEPLOY_TO = 'production'
                    }
                    changeLog = utils.gitVars(env.APPLICATION_NAME).changeLog.toString()
                    githubStatus 'pending'
                    slackStatus status: 'started', changeLog: "${changeLog}"
                }
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
                dockerUtils 'createPushImage'
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
                deployApplication()
            }
        }
        stage('deploy to production') {
            when { environment name: 'DEPLOY_TO', value: 'production' }
            environment { FASIT_ENV = 'p' }
            steps {
                script {
                    def jiraIssueId = nais action: 'jiraDeploy'
                    slackStatus status: 'deploying', jiraIssueId: "${jiraIssueId}"
                    def jiraProdIssueId = nais action: 'jiraDeployProd', jiraIssueId: jiraIssueId
                    slackStatus status: 'deploying', jiraIssueId: "${jiraProdIssueId}"
                    try {
                        timeout(time: 1, unit: 'HOURS') {
                            input id: "deploy", message: "Waiting for remote Jenkins server to deploy the application..."
                        }
                    } catch (Exception exception) {
                        currentBuild.description = "Deploy failed, see " + currentBuild.description
                        throw exception
                    }
                }
            }
        }

    }

    post {
        always {
            ciSkip 'postProcess'
            dockerUtils 'pruneBuilds'
            script {
                if (currentBuild.result == 'ABORTED') {
                    slackStatus status: 'aborted'
                }
            }
            junit 'target/surefire-reports/*.xml'
            archive 'target/bankkontonummer-kanal-*.jar'
            deleteDir()
        }
        success {
            githubStatus 'success'
            slackStatus status: 'success'
        }
        failure {
            githubStatus 'failure'
            slackStatus status: 'failure'
        }
    }
}

void deployApplication() {
    def jiraIssueId = nais action: 'jiraDeploy'
    slackStatus status: 'deploying', jiraIssueId: "${jiraIssueId}"
    try {
        timeout(time: 1, unit: 'HOURS') {
            input id: "deploy", message: "Waiting for remote Jenkins server to deploy the application..."
        }
    } catch (Exception exception) {
        currentBuild.description = "Deploy failed, see " + currentBuild.description
        throw exception
    }
}
