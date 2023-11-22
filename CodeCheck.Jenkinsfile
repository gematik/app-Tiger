@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
def REPO_URL = createGitUrl('git/Testtools/tiger/tiger')
def BRANCH = 'master'
def JIRA_PROJECT_ID = 'TGR'
def GITLAB_PROJECT_ID = '644'

pipeline {
    options {
        disableConcurrentBuilds()
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')
    }
    agent { label 'k8-maven-small' }

    stages {
        stage('Initialize') {
            steps {
                useJdk('OPENJDK17')
                gitSetIdentity()
            }
        }

        stage('Code format check') {
            steps {
                script {
                    try {
                        sh """
                           mvn spotless:check
                           """
                    } catch (err) {
                        unstable(message: "${STAGE_NAME} is unstable")
                    }
                }
            }
        }
    }
}
