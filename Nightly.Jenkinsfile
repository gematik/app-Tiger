@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def JIRA_PROJECT_ID = 'TGR'
def POM_PATH = 'pom.xml'
def REPO_URL = createGitUrl('git/Testtools/tiger/tiger')

pipeline {
    options {
        disableConcurrentBuilds()
    }
    agent { label 'k8-backend-extra-large' }

    tools {
        maven 'Default'
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
                mavenSetVersionFromJiraProject(JIRA_PROJECT_ID, POM_PATH, true, "", false)
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
        always {
            sendEMailNotification(getTigerEMailList())
            sh """
                echo "removing obsolete junit xml results from admin ui tests that cause xunit plugin to fail"
                rm -rf target/junit-reports-nightly
            """
            showJUnitAsXUnitResult("**/target/*-reports*/TEST-*.xml")
        }
    }
}
