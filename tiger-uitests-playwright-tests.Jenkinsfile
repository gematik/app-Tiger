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

pipeline {
    options {
        disableConcurrentBuilds()
    }
    agent { label 'k8-maven-large' }

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

        stage('set Version') {
            steps {
                mavenSetVersionFromJiraProject(JIRA_PROJECT_ID, POM_PATH, true, "", false)
            }
        }

        stage('Build') {
            steps {
                sh """
                  mvn install -DskipTests
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
                                mvn -U -P start-tiger-dummy failsafe:integration-test | tee mvn-playwright-log.txt
                               """
                        }
                    }
                }
                stage('Run playwright test') {
                    steps {
                        sh """
                            cd tiger-uitests
                            mvn -P run-playwright-test failsafe:integration-test
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
    }
    post {
        always {
            sendEMailNotification(getTigerEMailList())
            showJUnitAsXUnitResult("**/target/*-reports/TEST-*.xml")
        }
    }
}
