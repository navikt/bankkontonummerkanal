#!/usr/bin/env groovy

pipeline {
    agent any
    tools {
        maven 'maven-3.3.9'
    }

    environment {
        FASIT_ENV = 't0'
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
                    applicationVersion = "${pom.version}.${env.BUILD_ID}-${commitHashShort}"
                    applicationFullName = "${env.APPLICATION_FASIT_NAME}:${applicationVersion}"
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
            }
        }
        stage('docker build') {
            steps {
                script {
                    docker.withRegistry('https://docker.adeo.no:5000/') {
                        def image = docker.build("integrasjon/${applicationFullName}", "--build-arg GIT_COMMIT_ID=${commitHashShort} .")
                        image.push()
                        image.push 'latest'
                    }
                }
            }
        }
        stage('validate & upload nais.yaml to nexus m2internal') {
            steps {
                script {
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'nexus-user', usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD']]) {
                        sh "nais validate"
                        sh "nais upload --app ${env.APPLICATION_FASIT_NAME} -v ${applicationVersion}"
                    }
                }
            }
        }
        stage('deploy to nais') {
            steps {
                script {
                    def postBody = [
                            fields: [
                                    project          : [key: "DEPLOY"],
                                    issuetype        : [id: "14302"],
                                    customfield_14811: [value: "${env.FASIT_ENV}"],
                                    customfield_14812: "${applicationFullName}",
                                    customfield_17410: "${env.BUILD_URL}input/Deploy/",
                                    customfield_19015: [id: "22707", value: "Yes"],
                                    customfield_19413: "${env.APPLICATION_NAMESPACE}",
                                    customfield_19610: [value: "${env.ZONE}"],
                                    summary          : "Automatisk deploy av ${applicationFullName} til ${env.FASIT_ENV}"
                            ]
                    ]

                    def jiraPayload = groovy.json.JsonOutput.toJson(postBody)

                    echo jiraPayload

                    def response = httpRequest([
                            url                   : "https://jira.adeo.no/rest/api/2/issue/",
                            authentication        : "nais-user",
                            consoleLogResponseBody: true,
                            contentType           : "APPLICATION_JSON",
                            httpMode              : "POST",
                            requestBody           : jiraPayload
                    ])

                    def jiraIssueId = readJSON([text: response.content])["key"]
                    currentBuild.description = "Waiting for <a href=\"https://jira.adeo.no/browse/$jiraIssueId\">${jiraIssueId}</a>"
                }
            }
        }
    }
    post {
        always {
            junit 'target/surefire-reports/*.xml'
            archive 'target/bankkontonummer-kanal-*.jar'
            deleteDir()
            script {
                sh "docker system prune -af"
            }
        }
    }
}
