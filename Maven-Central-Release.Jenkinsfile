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
//     change to 'k8-publish' after we have successfully migrated to gcp jenkins
//     agent { label 'k8-publish' }
    agent { label 'k8-publish-latest' }

    tools {
        maven 'Default'
    }
    stages {

//      change back after we have successfully migrated to gcp jenkins
//         stage('Checkout') {
//             steps {
//                 git branch: BRANCH,
//                         url: REPO_URL
//             }
//         }
        stage('Checkout') {
            steps {
                sh "git clone --branch ${BRANCH} ${REPO_URL} ."
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
                    dockerLogin()
                    mavenTest(POM_PATH, "-Dwdm.gitHubToken=$GITHUB_TOKEN -PNoLongrunner")
                    dockerLogout()
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
