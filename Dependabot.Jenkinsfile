@Library('gematik-jenkins-shared-library') _

pipeline {

    options {
        disableConcurrentBuilds()
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')
    }

    agent { label 'k8-backend-small' }

    stages {
        stage('Run Dependabots') {
            parallel {
                stage('maven') {
                    steps {
                         dependabot('maven', 'git/Testtools/tiger/tiger')
                    }
                }
            }
        }
    }
}
