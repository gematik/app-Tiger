/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */
@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
def BRANCH = 'master'

pipeline {
    options {
        disableConcurrentBuilds()
    }
    agent { label 'k8-backend-large' }

    tools {
        maven 'Default'
    }

    stages {
        stage('Initialize') {
            steps {
                useJdk('OPENJDK17')
            }
        }

        stage('gitCreateBranch') {
            when { branch BRANCH }
            steps {
                gitCreateBranch()
            }
        }

        stage('Build') {
            steps {
                sh """
                  mvn install -DskipTests
                """
            }
        }

        stage('Run custom fail message test') {
            steps {
                script {
                    sh """
                    mvn -B verify -pl tiger-test-lib -Dtest=de.gematik.test.tiger.lib.integrationtest.TestCustomFailMessage 2>&1 | tee mvn.log
                    """

                    def expectedInLog1 = 'de.gematik.test.tiger.exceptions.CustomAssertionError: Hello, this is a custom message\nCaused by: java.lang.AssertionError: '
                    def expectedInLog2 = '[ERROR]   Hello, this is a custom message'
                    def logContent = readFile('mvn.log')

                    if (logContent.contains(expectedInLog1)) {
                        echo 'First expected string found in logs.'
                    } else {
                        error "First expected string not found in logs: ${expectedInLog1}.\n Failing the build."
                    }

                    if (logContent.contains(expectedInLog2)) {
                        echo 'Second expected string found in logs.'
                    } else {
                        error "Second expected string not found in logs: ${expectedInLog2}.\n Failing the build."
                    }
                }
            }
        }

    }
}