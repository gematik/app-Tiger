@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def BRANCH = 'master'
def JIRA_PROJECT_ID = 'TGR'
def GITLAB_PROJECT_ID = '644'
def TAG_NAME = "ci/build"
def POM_PATH = 'pom.xml'
def POM_PATH_PRODUCT = 'tiger-standalone-proxy/pom.xml'


pipeline {
    options {
        disableConcurrentBuilds()
    }
    agent { label 'Docker-Maven' }

    tools {
        maven 'Default'
    }

    stages {

        stage('gitCreateBranch') {
            when { branch BRANCH }
            steps {
                gitCreateBranch()
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
                mavenVerify(POM_PATH)
            }
        }

        stage('OWASP') {
            when { branch BRANCH }
            steps {
                mavenOwaspScan(POM_PATH)
            }
        }

        stage('Sonar') {
            steps {
                mavenCheckWithSonarQube(POM_PATH, "", false)
            }
        }

        stage('deploy') {
            when { branch BRANCH }
            steps {
                mavenDeploy(POM_PATH)
            }
        }

        stage('Tag and Push CI-build') {
            when { branch BRANCH }
            steps {
                gitCreateAndPushTag(JIRA_PROJECT_ID)
            }
        }

        stage('GitLab-Update-Snapshot') {
            when { branch BRANCH }
            steps {
                gitLabUpdateMavenSnapshot(JIRA_PROJECT_ID, GITLAB_PROJECT_ID, POM_PATH)
            }
        }
    }
    post {
        always {
            sendEMailNotification(getTigerEMailList())
            showJUnitAsXUnitResult()
            archiveArtifacts allowEmptyArchive: true, artifacts: 'tiger-standalone-proxy/target/site/serenity/**/*', fingerprint: false
            publishHTML (target: [
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: 'tiger-standalone-proxy/target/site/serenity',
                reportFiles: 'index.html',
                reportName: "Proxy Standalone UI Report"
            ])
        }
    }
}
