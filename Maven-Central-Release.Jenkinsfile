@Library('gematik-jenkins-shared-library') _
// GitHub
def REPO_URL = 'https://github.com/gematik/app-Tiger.git'
def BRANCH = 'master'
def POM_PATH = 'pom.xml'

pipeline {

    options {
        disableConcurrentBuilds()
        skipDefaultCheckout()
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')
    }
    agent { label 'Docker-Publish' }

    tools {
        maven 'Default'
    }
    stages {

        stage('Checkout') {
            steps {
                git branch: BRANCH,
                        url: REPO_URL
            }
        }

        stage('Build') {
            steps {
                mavenBuild(POM_PATH)
                archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/*.jar,**/target/*.war', fingerprint: true
            }
        }

        stage('Unit Test') {
            steps {
                withCredentials([string(credentialsId: 'GITHUB.API.Token', variable: 'GITHUB_TOKEN')]) {
                    mavenTest(POM_PATH, "-Dwdm.gitHubToken=$GITHUB_TOKEN")
                }
            }
        }

        stage('Publish to MavenCentral') {
            steps {
                mavenDeploy(POM_PATH, "-Pexternal")
            }
        }
    }
}
