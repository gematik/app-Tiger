@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'																   
def REPO_URL = createGitUrl('git/Testtools/tiger')
def BRANCH = 'master'
def JIRA_PROJECT_ID = 'TGR'
def GITLAB_PROJECT_ID = '644'
def TITLE_TEXT = 'Release'
def GROUP_ID_PATH = "de/gematik/test"
def GROUP_ID = "de.gematik.test"
def ARTIFACT_ID = 'tiger-standalone-proxy'
def ARTIFACT_IDs = 'tiger,tiger-admin,tiger-aforeporter-plugin,tiger-bdd-driver-generator-maven-plugin,tiger-standalone-proxy,tiger-proxy,tiger-testenv-mgr,tiger-test-lib'
def POM_PATH = 'pom.xml'
def PACKAGING = "jar"

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
    }

    stages {
        stage('Initialise') {
            steps {
                checkVersion(NEW_VERSION) // Eingabe erfolgt über Benutzerinteraktion beim Start des Jobs
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
                stage('prepare external release') {
                    steps {
                        mavenSetVersion("${RELEASE_VERSION}")
                        gitCommitAndTag("IDP-SERVER: RELEASE R${RELEASE_VERSION}", "R${RELEASE_VERSION}", "", "", true, false)
                    }
                }
                stage('UpdateProject with new Version') {
                    steps {
                        mavenSetVersion("${NEW_VERSION}-SNAPSHOT")
                        gitPushVersionUpdate(JIRA_PROJECT_ID, "${NEW_VERSION}-SNAPSHOT", BRANCH)
                    }
                }
                stage('deleteOldArtifacts') {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        steps {
                            script {
                                nexusDeleteArtifacts(RELEASE_VERSION, ARTIFACT_ID, GROUP_ID)
                            }
                        }
                    }
                }
            }
        }
    }
}

