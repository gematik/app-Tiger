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
    agent { label 'k8-publish' }

    tools {
        maven 'Default'
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
                        url: REPO_URL
            }
        }

//        stage('Build') {
//            steps {
//                mavenBuild(POM_PATH)
//                archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/*.jar,**/target/*.war', fingerprint: true
//            }
//        }

        stage('Publish to MavenCentral') {
            steps {
                mavenDeploy(POM_PATH, "-Pexternal")
            }
        }
    }
}
