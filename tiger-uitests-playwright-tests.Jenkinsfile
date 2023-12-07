@Library('gematik-jenkins-shared-library') _

// details about this integration test and how it is composed / working can be found in the java file
// tiger-maven-plugin/src/test/java/de/gematik/test/tiger/maven/adapter/mojos/EnvironmentMojoIT.java

String GCLOUD_SERVICE_ACCOUNT = 'gcp-deployer-tiger-dev-jsonfile'
String GCLOUD_PROJECT_NAME = 'gematik-all-k8s-db-dev'
String GCLOUD_CLUSTER_NAME = 'shared-k8s-dev'
String GCLOUD_CLUSTER_REGION = 'europe-west3-a'
String GEMATIK_NEXUS_CREDENTIALS = 'Nexus'

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def BRANCH = 'master'
def JIRA_PROJECT_ID = 'TGR'
def GITLAB_PROJECT_ID = '644'
def TAG_NAME = "ci/build"
def POM_PATH = 'pom.xml'
def POM_PATH_PRODUCT = 'tiger-testenv-mgr/pom.xml'
def REPO_URL = createGitUrl('git/Testtools/tiger/tiger')

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

        stage('Checkout') {
              steps {
                  git branch: BRANCH,
                      credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                      url: REPO_URL
              }
          }


        stage('set Version') {
            steps {
                mavenSetVersionFromJiraProject(JIRA_PROJECT_ID, POM_PATH, true, "", false)
            }
        }

        stage('Build') {
            steps {
                sh """
                  mvn -ntp install -DskipTests
                  cd tiger-uitests
                  mvn -ntp test-compile -P start-tiger-dummy
                """
            }
        }

        // start test env via mvn

        // run test

        stage('Integration Test') {
            environment {
                TIGER_DOCKER_HOST = dockerGetCurrentHostname()
            }
            parallel {
                stage('Start Tiger and Dummy Featurefile') {
                    steps {
                        withCredentials([string(credentialsId: 'GITHUB.API.Token', variable: 'GITHUB_TOKEN')]) {
                            sh """
                                cd tiger-uitests
                                rm -f mvn-playwright-log.txt
                                mvn --no-transfer-progress -P start-tiger-dummy failsafe:integration-test | tee mvn-playwright-log.txt
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
                        // clean up mvn-playwright-log.txt and shutdown testenv as soon as tests have ended to minimize time container is running
                        sh """
                          cd tiger-uitests
                          rm -f mvn-playwright-log.txt
                          export ENV_PID=\"`ps -ef | grep java | grep setup-testenv | awk -F\\   \'{ print \$2; }\'`\"
                          if [ \"\$ENV_PID\" ]; then
                            kill \$ENV_PID
                          fi
                        """
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
            sendEMailNotification(getTigerEMailList())
            showJUnitAsXUnitResult("**/target/*-reports/TEST-*.xml")
        }
    }
}
