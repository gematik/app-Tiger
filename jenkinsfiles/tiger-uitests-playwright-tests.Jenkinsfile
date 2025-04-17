@Library('gematik-jenkins-shared-library') _
// details about this integration test and how it is composed / working can be found in the java file
// tiger-maven-plugin/src/test/java/de/gematik/test/tiger/maven/adapter/mojos/EnvironmentMojoIT.java

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
def JIRA_PROJECT_ID = 'TGR'
def GITLAB_PROJECT_ID = '644'
def TAG_NAME = "ci/build"
def POM_PATH = 'pom.xml'
def POM_PATH_SET_VERSION = 'tiger-bom/pom.xml'
def POM_PATH_PRODUCT = 'tiger-testenv-mgr/pom.xml'
def REPO_URL = createGitUrl('git/Testtools/tiger/tiger')
def CHANNEL_ID = "19:5e4a353b87974bfca7ac6ac55ad7358b@thread.tacv2"
def GROUP_ID = "9c4c4366-476c-465d-8188-940f661574c3"

pipeline {
    options {
        disableConcurrentBuilds()
    }
    agent { label 'k8-backend-large' }

    tools {
        maven 'Default'
    }

    parameters {
        string(name: 'BRANCH', defaultValue: "master", description: 'Branch gegen den die UI-Tests ausgeführt werden sollen. Default: master')
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

        stage('Checkout') {
            steps {
                git branch: BRANCH,
                        credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                        url: REPO_URL
            }
        }

        stage('set Version') {
            steps {
                mavenSetVersionFromJiraProject(JIRA_PROJECT_ID, POM_PATH_SET_VERSION, true, "", false)
            }
        }

        stage('Build') {
            steps {
                sh """
                  mvn -ntp install -DskipTests
                """
            }
        }

        // start test env via mvn

        // run test

        stage('Build main tests') {
            steps {
                script {
                    sh """
                            mvn -ntp test-compile -P start-tiger-dummy
                          """
                }
            }
        }

        stage('Integration Test') {
            environment {
                TGR_TESTENV_CFG_CHECK_MODE = 'myEnv'
                TGR_TESTENV_CFG_DELETE_MODE = 'deleteEnv'
                TGR_TESTENV_CFG_EDIT_MODE = 'editEnv'
                TGR_TESTENV_CFG_MULTILINE_CHECK_MODE = 'Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. ...'
            }

            parallel {
                stage('Start Tiger and Dummy Featurefile') {
                    steps {
                        script {
                            def mvnProperties = "-DtgrTestPropCfgCheckMode=myProp -DtgrTestPropCfgEditMode=editProp -DtgrTestPropCfgDeleteMode=deleteProp"

                            sh """
                        cd tiger-uitests
                        rm -f mvn-playwright-log.txt
                        mvn --no-transfer-progress ${mvnProperties} -P start-tiger-dummy failsafe:integration-test | tee mvn-playwright-log.txt
                       """
                        }
                    }
                }
                stage('Run playwright test') {
                    steps {
                        sh """
                    cd tiger-uitests
                    mvn --no-transfer-progress -P run-playwright-test failsafe:integration-test failsafe:verify
                """
                    }
                    post {
                        always {
                            // clean up mvn-playwright-log.txt and shutdown testenv as soon as tests have ended to minimize time container is running
                            sh """
                      cd tiger-uitests
                      rm -f mvn-playwright-log.txt
                          export ENV_PID=\"`ps -ef | grep java | grep start-tiger-dummy | awk -F\\   \'{ print \$2; }\'`\"
                          if [ \"\$ENV_PID\" ]; then
                            kill \$ENV_PID
                          fi
                    """
                        }
                    }
                }
            }
        }

        stage('Build replay tests') {
            steps {
                script {
                    sh """
                            mvn -ntp test-compile -P start-tiger-dummy-for-unstarted-tests
                          """
                }
            }
        }

        stage('Integration Test None starting Tests') {
            parallel {
                stage('Start Tiger and Dummy Featurefile for none starting Tests') {
                    steps {
                        script {
                            sh """
                                cd tiger-uitests
                                rm -f mvn-playwright-log.txt
                                mvn --no-transfer-progress  -P start-tiger-dummy-for-unstarted-tests failsafe:integration-test | tee mvn-playwright-log.txt
                               """
                        }
                    }
                }
                stage('Run playwright test for none starting tests') {
                    steps {
                        sh """
                            cd tiger-uitests
                            mvn --no-transfer-progress -P run-playwright-test-for-unstarted-tests failsafe:integration-test failsafe:verify
                        """
                    }
                    post {
                        always {
                            // clean up mvn-playwright-log.txt and shutdown testenv as soon as tests have ended to minimize time container is running
                            sh """
                              cd tiger-uitests
                              rm -f mvn-playwright-log.txt
                              export ENV_PID=\"`ps -ef | grep java | grep start-tiger-dummy-for-unstarted-tests | awk -F\\   \'{ print \$2; }\'`\"
                              if [ \"\$ENV_PID\" ]; then
                                kill \$ENV_PID
                              fi
                            """
                        }
                    }
                }
            }
        }

        stage('Build sequencediagram tests') {
            steps {
                script {
                    sh """
                            mvn -ntp test-compile -P start-tiger-dummy-for-sequencediagram-tests
                          """
                }
            }
        }

        stage('Integration Test for Sequencediagram Tests') {
            parallel {
                stage('Start Tiger and Dummy Featurefile for sequencediagram Tests') {
                    steps {
                        script {
                            sh """
                                cd tiger-uitests
                                rm -f mvn-playwright-log.txt
                                mvn --no-transfer-progress  -P start-tiger-dummy-for-sequencediagram-tests failsafe:integration-test | tee mvn-playwright-log.txt
                               """
                        }
                    }
                }
                stage('Run playwright test for sequencediagram tests') {
                    steps {
                        sh """
                            cd tiger-uitests
                            mvn --no-transfer-progress -P run-playwright-test-for-sequencediagram-tests failsafe:integration-test failsafe:verify
                        """
                    }
                    post {
                        always {
                            // clean up mvn-playwright-log.txt and shutdown testenv as soon as tests have ended to minimize time container is running
                            sh """
                              cd tiger-uitests
                              rm -f mvn-playwright-log.txt
                              export ENV_PID=\"`ps -ef | grep java | grep start-tiger-dummy-for-sequencediagram-tests | awk -F\\   \'{ print \$2; }\'`\"
                              if [ \"\$ENV_PID\" ]; then
                                kill \$ENV_PID
                              fi
                            """
                        }
                    }
                }
            }
        }

        stage('Commit screenshots') {
            steps {
                script {
                    sh """
                     git add doc/user_manual/screenshots/*.png
                     git commit -m "TGR-9999: Jenkins adds PNG files"
                     git push origin ${BRANCH}
                """
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'tiger-uitests/target/playwright-artifacts/*', fingerprint: true
            showJUnitAsXUnitResult("**/target/*-reports/TEST-*.xml")
            sendEMailNotification(getTigerEMailList())
            teamsSendNotificationToChannel(CHANNEL_ID, GROUP_ID, [ "facts": [ "Branch": "${BRANCH}" ] ] )
        }
    }
}
