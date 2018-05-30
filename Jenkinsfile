#!/usr/bin/env groovy

pipeline {
    agent any
    tools {
        maven 'maven-3.3.9'
    }

    environment {
        FASIT_ENV = 'q1'
        ZONE = 'fss'
        APPLICATION_NAMESPACE = 'default'
        APPLICATION_FASIT_NAME = 'bankkontonummerkanal'
    }

    stages {
        stage('setup') {
            steps {
                script {
                    commitHashShort = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    pom = readMavenPom file: 'pom.xml'
                    env.APPLICATION_VERSION = "${applicationVersion}"
                    if (applicationVersion.endsWith('-SNAPSHOT')) {
                        env.APPLICATION_VERSION = "${pom.version}.${env.BUILD_ID}-${commitHashShort}"
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
                script {
                    docker.withRegistry('https://repo.adeo.no:5443/') {
                        def image = docker.build("integrasjon/${applicationFullName}", "--build-arg GIT_COMMIT_ID=${commitHashShort} .")
                        image.push()
                    }
                }
            }
        }
        stage('validate & upload nais.yaml to nexus m2internal') {
            steps {
                nais 'validate'
                nais 'upload'
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
                deployApplication()
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
    def jiraIssueId = nais 'jiraDeploy'
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
