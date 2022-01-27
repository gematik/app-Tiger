@Library('gematik-jenkins-shared-library') _

pipeline {
    options {
        disableConcurrentBuilds()
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')
    }

    agent { label 'k8-maven' }

    tools {
        maven 'Default'
    }

    parameters {
        string(name: 'NEW_VERSION', defaultValue: '', description: 'Bitte die nächste Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
        choice(name: 'DRY_RUN', choices: ['NO', 'YES'], description: 'Execute the preparing steps but do not push anything.')
        choice(name: 'INTERNAL', choices: ['YES', 'NO'], description: 'Internes Release wird ausgeführt.')
        choice(name: 'GITHUB', choices: ['YES', 'NO'], description: 'GitHub-Release wird ausgeführt.')
        choice(name: 'MAVENCENTRAL', choices: ['YES', 'NO'], description: 'Maven-Central-Release wird ausgeführt.')
    }

    stages {
        stage('Internal-Release') {
            when {
                expression { params.INTERNAL == 'YES' }
            }
            steps {
                build job: 'Tiger-Internal-Release',
                parameters: [
                    string(name: 'NEW_VERSION', value: String.valueOf("${NEW_VERSION}")),
                    string(name: 'RELEASE_VERSION', value: String.valueOf("${RELEASE_VERSION}")),
                ]
            }
        }

        stage('GitHub-Release') {
            when {
                expression { params.GITHUB == 'YES' }
            }
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
            when {
                expression { params.MAVENCENTRAL == 'YES' }
            }
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

