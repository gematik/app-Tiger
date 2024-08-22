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

def TEAMS_URL = 'https://gematikde.webhook.office.com/webhookb2/9c4c4366-476c-465d-8188-940f661574c3@30092c62-4dbf-43bf-a33f-10d21b5b660a/JenkinsCI/f7fb184ffab6425cae8e254ea3818449/0151c74f-c7f1-4fe1-834a-6aa680ab023f'

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
        JAVA_TOOL_OPTIONS = '-Xmx16g  -Xms1g -Dgwt.extraJvmArgs="-Xmx16g -Xms1g"'
    }

    parameters {
        string(name: 'NEW_VERSION', defaultValue: '', description: 'Bitte die nächste Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
        choice(name: 'DRY_RUN', choices: ['NO', 'YES'], description: 'Execute the preparing steps but do not push anything.')
        choice(name: 'INTERNAL', choices: ['YES', 'NO'], description: 'Internes Release wird ausgeführt.')
        choice(name: 'GITHUB', choices: ['YES', 'NO'], description: 'GitHub-Release wird ausgeführt.')
        choice(name: 'MAVENCENTRAL', choices: ['YES', 'NO'], description: 'Maven-Central-Release wird ausgeführt.')
        choice(name: 'DOCKER_HUB', choices: ['YES', 'NO'], description: 'Publish Image from a GCR Repository to DockerHub.')
    }

    stages {
        stage('Initialise') {
            steps {
                setProperties([notifyTeams(TEAMS_URL)])
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
                                string(name: 'REMOTE_BRANCH', value: String.valueOf("master")),
                                string(name: 'AUTOMATIC_MERGE', value: String.valueOf("YES")),
                                string(name: 'GITHUB_TAG', value: String.valueOf("${RELEASE_VERSION}")),
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
            sendTeamsNotification(TEAMS_URL)
        }
    }
}
