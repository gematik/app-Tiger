@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
def JIRA_PROJECT_ID = 'TGR'
def POM_PATH = 'pom.xml'
def POM_PATH_SET_VERSION = 'tiger-bom/pom.xml'
def REPO_URL = createGitUrl('git/Testtools/tiger/tiger')
def TEST_RESULTS = "❌ FAILED"
def CHANNEL_ID = "19:5e4a353b87974bfca7ac6ac55ad7358b@thread.tacv2"
def GROUP_ID = "9c4c4366-476c-465d-8188-940f661574c3"

pipeline {
    options {
        disableConcurrentBuilds()
    }
    agent { label 'k8-backend-extra-large' }

    tools {
        maven 'Default'
    }
    environment {
        JAVA_TOOL_OPTIONS = '-Xmx16g  -Xms1g -Dgwt.extraJvmArgs="-Xmx16g -Xms1g"'
    }

    parameters {
        string(name: 'BRANCH', defaultValue: "master", description: 'Branch gegen den die UI-Tests ausgeführt werden sollen. Default: master')
    }

    stages {
        stage('Initialize') {
            steps {
                useJdk('OPENJDK17')
            }
        }
        stage('Checkout') {
            steps {
                git branch: BRANCH,
                        credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                        url: REPO_URL
            }
        }

        stage('set Version') {
            steps {
                mavenSetVersionFromJiraProject(JIRA_PROJECT_ID, POM_PATH_SET_VERSION, true, "", false)
            }
        }

        stage('Build') {
            steps {
                mavenBuild(POM_PATH)
            }
        }

        stage('Test') {
            steps {
                withCredentials([string(credentialsId: 'GITHUB.API.Token', variable: 'GITHUB_TOKEN')]) {
                    mavenVerify(POM_PATH, "-Dwdm.gitHubToken=$GITHUB_TOKEN -P=WithLongrunner,dev")
                }
            }
        }

        stage('OWASP') {
            steps {
                mavenOwaspScan(POM_PATH)
            }
        }

        stage('Sonar') {
            environment {
                BRANCH_NAME = "${BRANCH}"
            }
            steps {
                mavenCheckWithSonarQube(POM_PATH, "", false)
            }
        }
    }
    post {
        success {
             script {
                TEST_RESULTS = "✅ SUCCESS"
             }
        }
        always {
            sendEMailNotification(getTigerEMailList())
            sh """
                echo "removing obsolete junit xml results from admin ui tests that cause xunit plugin to fail"
                rm -rf target/junit-reports-nightly
            """
            showJUnitAsXUnitResult("**/target/*-reports*/TEST-*.xml")
            teamsSendNotificationToChannel(CHANNEL_ID, GROUP_ID, ["facts": ["Result": "${TEST_RESULTS}"]] )
        }
    }
}
