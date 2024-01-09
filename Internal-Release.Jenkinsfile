@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
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
                stage('generate resources') {
                    steps {
                        sh "cd doc/user_manual && ./extractCommentsFromRbelValidatorGlue.sh"
                    }
                }
                stage('prepare external release') {
                    steps {
                        dockerLoginGematikRegistry()
                        mavenSetVersion("${RELEASE_VERSION}")
                        gitCommitAndTag("TIGER: RELEASE R${RELEASE_VERSION}", "R${RELEASE_VERSION}", "", "", true, false)

                        sh 'git stash'
                        //GH Pages

                        sh '''
                          docker pull eu.gcr.io/gematik-all-infra-prod/shared/gematik-asciidoc-converter:latest
                          docker create --name tiger-gemdoc-'''+BUILD_NUMBER+''' eu.gcr.io/gematik-all-infra-prod/shared/gematik-asciidoc-converter:latest /tmpdata/doc/user_manual/tiger_user_manual.adoc
                          docker cp '''+pwd()+''' tiger-gemdoc-'''+BUILD_NUMBER+''':/tmpdata
                          docker start --attach tiger-gemdoc-'''+BUILD_NUMBER+'''
                          docker cp tiger-gemdoc-'''+BUILD_NUMBER+''':/tmpdata/doc/user_manual/tiger_user_manual.pdf .
                          docker cp tiger-gemdoc-'''+BUILD_NUMBER+''':/tmpdata/doc/user_manual/tiger_user_manual.html .
                          docker cp tiger-gemdoc-'''+BUILD_NUMBER+''':/tmpdata/doc/user_manual/media .
                          docker cp tiger-gemdoc-'''+BUILD_NUMBER+''':/tmpdata/doc/user_manual/screenshots .
                          docker rm tiger-gemdoc-'''+BUILD_NUMBER+'''
                        '''
                        // to test local:
                        // docker run --name tiger-gemdoc --rm -v $(pwd):/tmpdata eu.gcr.io/gematik-all-infra-prod/shared/gematik-asciidoc-converter:latest /tmpdata/doc/user_manual/tiger_user_manual.adoc
                        // or for windows users:
                        // docker run --name tiger-gemdoc --rm -v %cd%:/tmpdata eu.gcr.io/gematik-all-infra-prod/shared/gematik-asciidoc-converter:latest /tmpdata/doc/user_manual/tiger_user_manual.adoc
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

                        gitCommitAndTagDocu("TIGER: RELEASE R${RELEASE_VERSION}", "R${RELEASE_VERSION}", "", "", true, false)
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
    }
}
