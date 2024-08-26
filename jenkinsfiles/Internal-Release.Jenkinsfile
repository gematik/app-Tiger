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

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
def REPO_URL = createGitUrl('git/Testtools/tiger/tiger')
def BRANCH = 'master'
def JIRA_PROJECT_ID = 'TGR'
def GITLAB_PROJECT_ID = '644'
def TITLE_TEXT = 'Release'
def GROUP_ID_PATH = "de/gematik/test"
def GROUP_ID = "de.gematik.test"
def ARTIFACT_ID = 'tiger-testenv-mgr'
def ARTIFACT_IDs = 'tiger,tiger-rbel,tiger-maven-plugin,tiger-proxy,tiger-testenv-mgr,tiger-test-lib,tiger-integration-example'
def POM_PATH = 'pom.xml'
def PACKAGING = "jar"

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
        choice(name: 'DOCKER_HUB', choices: ['YES', 'NO'], description: 'Publish Image from a GCR Repository to DockerHub.')
    }

    stages {
        stage('Initialise') {
            steps {
                checkVersion(NEW_VERSION) // Eingabe erfolgt über Benutzerinteraktion beim Start des Jobs
                gitSetIdentity()
                useJdk('OPENJDK17')
            }
        }

        stage('Checkout') {
            steps {
                git branch: BRANCH, credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                        url: REPO_URL
            }
        }

        stage('Environment') {
            environment {
                LATEST = nexusGetLatestVersionByGAVR(RELEASE_VERSION, ARTIFACT_ID, GROUP_ID, PACKAGING).trim()
                TAG_NAME = 'Release/ReleaseBuild'
                DOCKER_TARGET_REGISTRY = dockerGetGematikRegistry('EUWEST3')
            }
            stages {
                stage('Create Release-Tag') {
                    steps {
                        gitCreateAndPushTag(JIRA_PROJECT_ID, "${TAG_NAME}-${LATEST}", BRANCH)
                    }
                }

                stage('Create GitLab Release') {
                    steps {
                        gitLabCreateRelease(JIRA_PROJECT_ID, GITLAB_PROJECT_ID, LATEST, ARTIFACT_ID, GROUP_ID_PATH, TITLE_TEXT, RELEASE_VERSION, "${TAG_NAME}-${LATEST}")
                    }
                }

                stage('Release Jira-Version') {
                    steps {
                        jiraReleaseVersion(JIRA_PROJECT_ID, RELEASE_VERSION)
                    }
                }
                stage('Create New Jira-Version') {
                    steps {
                        jiraCreateNewVersion(JIRA_PROJECT_ID, NEW_VERSION)
                    }
                }
                stage('Build') {
                    steps {
                        mavenBuild(POM_PATH)
                    }
                }
                stage('prepare external release') {
                    steps {
                        mavenSetVersion("${RELEASE_VERSION}")
                        gitCommitAndTag("TIGER: RELEASE R${RELEASE_VERSION}", "R${RELEASE_VERSION}", "", "", true, false)

                        sh 'git stash'
                        //GH Pages

                        dockerPull("swf/tools/gematik-asciidoc-converter", "latest", DOCKER_TARGET_REGISTRY)
                        sh '''
                          docker create --name tiger-gemdoc-''' + BUILD_NUMBER + ''' europe-west3-docker.pkg.dev/gematik-all-infra-prod/swf/tools/gematik-asciidoc-converter:latest /tmpdata/doc/user_manual/tiger_user_manual.adoc
                          docker cp ''' + pwd() + ''' tiger-gemdoc-''' + BUILD_NUMBER + ''':/tmpdata
                          docker start --attach tiger-gemdoc-''' + BUILD_NUMBER + '''
                          docker cp tiger-gemdoc-''' + BUILD_NUMBER + ''':/tmpdata/doc/user_manual/tiger_user_manual.pdf .
                          docker cp tiger-gemdoc-''' + BUILD_NUMBER + ''':/tmpdata/doc/user_manual/tiger_user_manual.html .
                          docker cp tiger-gemdoc-''' + BUILD_NUMBER + ''':/tmpdata/doc/user_manual/media .
                          docker cp tiger-gemdoc-''' + BUILD_NUMBER + ''':/tmpdata/doc/user_manual/screenshots .
                          docker rm tiger-gemdoc-''' + BUILD_NUMBER + '''
                        '''
                        // to test local:
                        // docker run --name tiger-gemdoc --rm -v $(pwd):/tmpdata europe-west3-docker.pkg.dev/gematik-all-infra-prod/swf/tools/gematik-asciidoc-converter:latest /tmpdata/doc/user_manual/tiger_user_manual.adoc
                        // or for windows users:
                        // docker run --name tiger-gemdoc --rm -v /$PWD://tmpdata europe-west3-docker.pkg.dev/gematik-all-infra-prod/swf/tools/gematik-asciidoc-converter:latest //tmpdata/doc/user_manual/tiger_user_manual.adoc
                        stash includes: 'tiger_user_manual.pdf,tiger_user_manual.html,media/**/*,screenshots/*.png', name: 'manual'

                        sh label: 'checkoutGhPages', script: """
                            git checkout gh-pages
                            git clean -df
                            """
                        unstash 'manual'
                        sh label: 'createManual', script: """
                            mv ./tiger_user_manual.pdf ./Tiger-User-Manual.pdf
                            mv ./tiger_user_manual.html ./Tiger-User-Manual.html
                            """

                        gitCommitAndTagDocu("TIGER: RELEASE R${RELEASE_VERSION}", "R${RELEASE_VERSION}", "", "", true, true)
                        sh "git checkout master"
                    }
                }
                stage('UpdateProject with new Version') {
                    steps {
                        mavenSetVersion("${NEW_VERSION}-SNAPSHOT")
                        gitPushVersionUpdate(JIRA_PROJECT_ID, "${NEW_VERSION}-SNAPSHOT", BRANCH)
                    }
                }
                stage('deleteOldArtifacts') {
                    steps {
                        catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                            nexusDeleteArtifacts(RELEASE_VERSION, ARTIFACT_IDs, GROUP_ID)
                        }
                    }
                }
            }
        }
        stage('Retag Docker Images') {

            matrix {
                axes {
                    axis {
                        name 'APP'
                        values 'tiger-proxy', 'tiger-zion'
                    }
                }
                environment {
                    IMAGE_NAME = "tiger/${APP}"
                    DOCKER_TARGET_REGISTRY = dockerGetGematikRegistry('EUWEST3')
                }
                stages {
                    stage('Retag Docker Image') {
                        steps {
                            dockerPull(IMAGE_NAME, "latest", DOCKER_TARGET_REGISTRY)
                            dockerReTagImage(IMAGE_NAME, RELEASE_VERSION, "latest", DOCKER_TARGET_REGISTRY, DOCKER_TARGET_REGISTRY)
                            dockerPushImage(IMAGE_NAME, RELEASE_VERSION, 'tiger-gar-writer', DOCKER_TARGET_REGISTRY)
                            dockerRemoveLocalImage(IMAGE_NAME, RELEASE_VERSION, DOCKER_TARGET_REGISTRY)
                        }
                    }
                }
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
    }
}
