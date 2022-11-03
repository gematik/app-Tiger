@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def BRANCH = 'master'
def JIRA_PROJECT_ID = 'TGR'
def GITLAB_PROJECT_ID = '644'
def TAG_NAME = "ci/build"
def POM_PATH = 'pom.xml'
def POM_PATH_PRODUCT = 'tiger-testenv-mgr/pom.xml'

pipeline {
    options {
        disableConcurrentBuilds()
    }
    agent { label 'k8-maven-large' }

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
                    mavenVerify(POM_PATH, "-Dwdm.gitHubToken=$GITHUB_TOKEN -P=NoLongrunner,dev")
                }
            }
        }

        stage('Sonar') {
            when {
                not {
                    branch BRANCH
                }
            }
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
                gitLabUpdateMavenSnapshot(JIRA_PROJECT_ID, GITLAB_PROJECT_ID, POM_PATH_PRODUCT)
            }
        }
    }
    post {
        always {
            sendEMailNotification(getTigerEMailList())
            showJUnitAsXUnitResult("**/target/*-reports/TEST-*.xml")
        }
    }
}
