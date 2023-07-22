@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
def REPO_URL = createGitUrl('git/communications/ti-m/ti-m-testsuite')
def BRANCH = 'main'
def POM_PATH = 'pom.xml'

pipeline {
      options {
          disableConcurrentBuilds()
          buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10')
      }
      agent { label 'k8-maven' }

      tools {
          maven 'Default'
      }

      environment {
              TIM_KEYSTORE_PW = 'gematik123'
              TIM_TRUSTSTORE_PW = 'gematik123'
              TIM_TRUSTSTORE = 'src/main/resources/certs/truststore.p12'
              TIM_KEYSTORE = 'src/main/resources/certs/c_keystore.p12'
      }

      parameters {
          string(name: 'TIGER_VERSION', description: 'Bitte die aktuelle Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
          choice(name: 'UPDATE', choices: ['NO', 'YES'], description: 'Flag, um zu prüfen, ob die neue Tiger-Version in einigen Projekten aktualisiert werden soll')
      }

    stages {
        stage('Initialise') {
            steps {
                useJdk("OPENJDK17")

                // TODO: script block can be removed when JSL v1.21.0 is released
                script {
                    env.DOCKER_HOST_HOSTNAME = sh(
                            script: "docker info -f '{{ index .Name}}'",
                            returnStdout: true
                    ).trim() + getServerDomain()
                }
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
                sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' ${POM_PATH}"
            }
        }

        stage('Build') {
            steps {
                mavenBuild(POM_PATH,"-Dprofile.ci")
            }
        }

        stage('Tests') {
            steps {
                withEnv(["TIGER_DOCKER_HOST=$DOCKER_HOST_HOSTNAME"]){
                    mavenVerify("pom.xml", "-Dprofile.ci")
                }
            }
        }

        stage('Commit new Tiger version when needed') {
            steps {
                script {
                    if (params.UPDATE == 'YES') {
                        catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                            sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' ${POM_PATH}"
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
