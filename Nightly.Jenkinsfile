@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def JIRA_PROJECT_ID = 'TGR'
def POM_PATH = 'pom.xml'
def BRANCH = 'master'
def REPO_URL = createGitUrl('git/Testtools/tiger')

pipeline {
    options {
        disableConcurrentBuilds()
    }
    agent { label 'Docker-Maven' }

    tools {
        maven 'Default'
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: BRANCH,
                    credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                    url: REPO_URL
            }
        }

        stage('set Version') {
            steps {
                mavenSetVersionFromJiraProject(JIRA_PROJECT_ID, POM_PATH)
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
                    mavenVerify(POM_PATH, "-Dwdm.gitHubToken=$GITHUB_TOKEN -PWithUITests")
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
            showJUnitAsXUnitResult()
            archiveArtifacts allowEmptyArchive: true, artifacts: 'tiger-standalone-proxy/target/site/serenity/**/*,tiger-admin/target/site/serenity/**/*', fingerprint: false
            publishHTML (target: [
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: 'tiger-standalone-proxy/target/site/serenity',
                reportFiles: 'index.html',
                reportName: "Proxy Standalone UI Report"
            ])
            publishHTML (target: [
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: 'tiger-admin/target/site/serenity',
                reportFiles: 'index.html',
                reportName: "Admin UI Report"
            ])
        }
    }
}
