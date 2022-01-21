@Library('gematik-jenkins-shared-library') _

pipeline {
    options {
        disableConcurrentBuilds()
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')
    }

    agent { label 'Docker-Maven' }

    tools {
        maven 'Default'
    }

    parameters {
        string(name: 'NEW_VERSION', defaultValue: '', description: 'Bitte die nächste Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
        choice(name: 'DRY_RUN', choices: ['NO', 'YES'], description: 'Execute the preparing steps but do not push anything.')
    }

    stages {
        stage('Internal-Release') {
            steps {
                build job: 'Tiger-Internal-Release',
                parameters: [
                    string(name: 'NEW_VERSION', value: String.valueOf("${NEW_VERSION}")),
                    string(name: 'RELEASE_VERSION', value: String.valueOf("${RELEASE_VERSION}")),
                ]
            }
        }

        stage('GitHub-Release') {
            steps {
               build job: 'Tiger-GitHub-Release',
               parameters: [
                    string(name: 'TAGNAME', value: String.valueOf("R${RELEASE_VERSION}")),
                    string(name: 'RELEASE_VERSION', value: String.valueOf("${RELEASE_VERSION}")),
                    text(name: 'COMMIT_MESSAGE', value: String.valueOf("Release ${RELEASE_VERSION}")),
                    string(name: 'DRY_RUN', value: String.valueOf(params.DRY_RUN)),
               ]
            }
        }

        stage('Maven-Central-Release') {
            steps {
                build job: 'Tiger-Maven-Central-Release'
            }
        }
    }

    post {
        always {
            sendEMailNotification(getTigerEMailList())
        }
    }
}

