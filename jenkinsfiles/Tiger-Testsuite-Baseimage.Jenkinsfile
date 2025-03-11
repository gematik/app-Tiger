/*
 * Copyright 2025 gematik GmbH
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
 *
 */

@Library('gematik-jenkins-shared-library') _

def BRANCH = 'master'
def BASE_DIR = 'tiger-testsuite-baseimage'
def POM_PATH = BASE_DIR + '/pom.xml'
def JIRA_PROJECT_ID = 'TGR'
def IMAGE_NAME = "tiger/tiger-testsuite-baseimage"

pipeline {
    options {
        disableConcurrentBuilds()
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')
    }

    agent { label 'k8-backend' }

    tools {
        maven 'Default'
    }

    stages {
        stage('Initialise') {
            steps {
                gitSetIdentity()
            }
        }
        stage('Activate Cache') {
            steps {
                activateOptionalBuildCache()
            }
        }

        stage('Get Jira Version') {
            steps {
                script {
                    JIRA_VERSION = jiraGetMostRecentVersion(JIRA_PROJECT_ID, true)

                    echo 'Summary of Versions:'
                    echo "JIRA Version: ${JIRA_VERSION}"
                }
            }
        }

        stage('gitCreateBranch') {
            steps {
                gitCreateBranch()
            }
        }

        stage('Set Maven Version') {
            steps {
                script {
                    echo("POM Path Version to ${POM_PATH}")
                    mavenSetVersion(JIRA_VERSION, POM_PATH, "", false)
                }
            }
        }

        stage('Tiger Testsuite Base Docker Image') {

            environment {
                DOCKER_TARGET_REGISTRY = dockerGetGematikRegistry()
            }
            stages {
                stage('Build Docker Image') {
                    steps {
                        dockerBuild(IMAGE_NAME, JIRA_VERSION, JIRA_VERSION, '', "Dockerfile", dockerGetGematikRegistry(), BASE_DIR)
                    }
                }

                stage('Push Docker Image') {
                    steps {
                        dockerPushImage(IMAGE_NAME, JIRA_VERSION, 'tiger-gar-writer', DOCKER_TARGET_REGISTRY)
                    }
                }

                stage('Publish Image to Docker-Hub') {
                    steps {
                        script {
                            build job: "Tiger-TIGER-tiger-testsuite-baseimage-DockerHub-Release",
                                    parameters: [string(name: 'PUBLISH_VERSION', value: String.valueOf("${JIRA_VERSION}"))]
                        }
                    }
                }

                stage('Cleanup Docker Image') {
                    steps {
                        dockerRemoveLocalImage(IMAGE_NAME, JIRA_VERSION, DOCKER_TARGET_REGISTRY)
                    }
                }
            }
        }
    }
}