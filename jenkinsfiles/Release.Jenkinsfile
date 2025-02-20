/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@Library('gematik-jenkins-shared-library') _

def CHANNEL_ID = "19:5e4a353b87974bfca7ac6ac55ad7358b@thread.tacv2"
def GROUP_ID = "9c4c4366-476c-465d-8188-940f661574c3"

pipeline {
    options {
        disableConcurrentBuilds()
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')
    }

    agent { label 'k8-backend-extra-large' }

    tools {
        maven 'Default'
    }
    environment {
        JAVA_TOOL_OPTIONS = '-Xmx16g  -Xms4g -Dgwt.extraJvmArgs="-Xmx16g -Xms4g"'
    }

    parameters {
        string(name: 'NEW_VERSION', defaultValue: '', description: 'Bitte die nächste Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
        choice(name: 'DRY_RUN', choices: ['NO', 'YES'], description: 'Execute the preparing steps but do not push anything.')
        choice(name: 'INTERNAL', choices: ['YES', 'NO'], description: 'Internes Release wird ausgeführt.')
        choice(name: 'GITHUB', choices: ['YES', 'NO'], description: 'GitHub-Release wird ausgeführt.')
        choice(name: 'GITHUB_DOCU', choices: ['YES', 'NO'], description: 'GitHub-Docu-Release wird ausgeführt.')
        choice(name: 'MAVENCENTRAL', choices: ['YES', 'NO'], description: 'Maven-Central-Release wird ausgeführt.')
        choice(name: 'DOCKER_HUB', choices: ['YES', 'NO'], description: 'Publish Image from a GCR Repository to DockerHub.')
    }

    stages {
        stage('Initialise') {
            steps {
                useJdk('OPENJDK17')
            }
        }

        stage('Internal-Release') {
            when {
                expression { params.INTERNAL == 'YES' }
            }
            steps {
                build job: 'Tiger-TIGER-Internal-Release',
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
                build job: 'Tiger-TIGER-GitHub-Release',
                        parameters: [
                                string(name: 'GITLAB_TAG', value: String.valueOf("R${RELEASE_VERSION}")),
                                text(name: 'COMMIT_MESSAGE', value: String.valueOf("Release ${RELEASE_VERSION}")),
                                text(name: 'REMOTE_BRANCH', value: String.valueOf("master")),
                                string(name: 'AUTOMATIC_MERGE', value: String.valueOf("YES")),
                                string(name: 'GITHUB_TAG', value: String.valueOf("${RELEASE_VERSION}")),
                        ]
            }
        }

        stage('GitHub-Docu-Release') {
            when {
                expression { params.GITHUB_DOCU == 'YES' }
            }
            steps {
                build job: 'Tiger-TIGER-GitHub-Docu-Release',
                        parameters: [
                                string(name: 'GITLAB_TAG', value: String.valueOf("docu/R${RELEASE_VERSION}")),
                                text(name: 'COMMIT_MESSAGE', value: String.valueOf("Release ${RELEASE_VERSION}")),
                                text(name: 'REMOTE_BRANCH', value: String.valueOf("gh-pages")),
                                string(name: 'AUTOMATIC_MERGE', value: String.valueOf("YES")),
                                string(name: 'GITHUB_TAG', value: String.valueOf("docu/R${RELEASE_VERSION}")),
                        ]
            }
        }

        stage('Publish Images to Docker-Hub') {
            when {
                expression { params.DOCKER_HUB == 'YES' }
            }
            steps {
                script {
                    def images = ['tiger-proxy-image', 'tiger-zion-image']

                    images.each { imageName ->
                        build job: "Tiger-TIGER-${imageName}-DockerHub-Release",
                                parameters: [
                                        string(name: 'PUBLISH_VERSION', value: String.valueOf("${RELEASE_VERSION}"))
                                ]
                    }
                }
            }
        }

        stage('Maven-Central-Release') {
            when {
                expression { params.MAVENCENTRAL == 'YES' }
            }
            steps {
                build job: 'Tiger-TIGER-Maven-Central-Release'
            }
        }
    }
    post {
        always {
            sendEMailNotification(getTigerEMailList())
        }
        success {
            teamsSendNotificationToChannel(CHANNEL_ID, GROUP_ID)
        }
    }
}
