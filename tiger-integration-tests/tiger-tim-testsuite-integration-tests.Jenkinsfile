@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
def REPO_URL = createGitUrl('git/communications/ti-m/ti-m-testsuite')
def BRANCH = 'main'
def POM = 'pom.xml'

pipeline {
      options {
          disableConcurrentBuilds()
          buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10')
      }
      agent { label 'k8-backend-large' }

      tools {
          maven 'Default'
      }

      environment {
              TIM_KEYSTORE_PW = 'gematik123'
              TIM_TRUSTSTORE_PW = 'gematik123'
              TIM_TRUSTSTORE = 'src/main/resources/certs/truststore.p12'
              TIM_KEYSTORE = 'src/main/resources/certs/c_keystore.p12'
              BRANCH_LOWER_CASE = sh(returnStdout: true, script: "echo ${BRANCH_NAME} | tr '[:upper:]' '[:lower:]' | tr '/' '_' | tr -d '\n'")

      }

      parameters {
          string(name: 'TIGER_VERSION', description: 'Bitte die aktuelle Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
          choice(name: 'UPDATE', choices: ['NO', 'YES'], description: 'Flag, um zu prüfen, ob die neue Tiger-Version in einigen Projekten aktualisiert werden soll')
      }

    stages {
        stage('Initialise') {
            steps {
                useJdk("OPENJDK17")
            }
        }

        stage('Checkout') {
            steps {
                git branch: BRANCH,
                  credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                  url: REPO_URL
            }
        }

        stage('Set Tiger version in TI-M') {
            steps {
                sh "grep -q tiger.version ${POM}"
                sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' ${POM}"
            }
        }

        stage('Build') {
            steps {
                mavenBuild(POM,"-Dprofile.ci")
            }
        }

        stage('Start DB') {
            steps {
                sh("docker compose -p ${BRANCH_LOWER_CASE} up -d")
            }
        }

        stage('Test') {
            environment {
                TIGER_DOCKER_HOST = dockerGetCurrentHostname()
                TIM_DB_PORT = sh(returnStdout: true, script: "docker ps --format='{{.Names}} {{.Ports}}' | grep '${BRANCH_LOWER_CASE}' | cut -d \' \' -f2 | grep -oP ':\\K\\d+(?=-)' | tr -d '\\n'")

            }
            steps {
                mavenVerify(POM, "-Dprofile.ci")
            }
        }

        stage('Commit new Tiger version when needed') {
            steps {
                script {
                    if (params.UPDATE == 'YES') {
                        catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                            sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' ${POM}"
                            sh """
                                git add -A
                                git commit -m "Tiger version updated"
                                git push origin ${BRANCH}

                            """
                        }
                    }
                }
            }
        }
    }

    post {
        changed {
            sendEMailNotification(getTigerEMailList())
        }
        success {
            script {
                 if (params.UPDATE == 'YES')
                     sendEMailNotification(getCommunicationsEMailList() + "," + getTigerEMailList())
            }
        }

        failure {
            script {
                 if (params.UPDATE == 'YES')
                     sendEMailNotification(getCommunicationsEMailList() + "," + getTigerEMailList())
            }
        }
    }
}
