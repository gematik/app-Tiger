@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/communications/ti-m/ti-m-testsuite')
def BRANCH = 'main'
def POM_PATH = 'pom.xml'

pipeline {
      options {
          disableConcurrentBuilds()
      }
      agent { label 'k8-maven' }

      tools {
          maven 'Default'
      }

      parameters {
          string(name: 'TIGER_VERSION', defaultValue: '', description: 'Bitte die aktuelle Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
          booleanParam(name: 'UPDATE', defaultValue: false, description: 'Flag, um zu prüfen, ob die neue Tiger-Version in einigen Projekten aktualisiert werden soll. Default: false')
      }

      stages {
          stage('Initialise') {
              steps {
                  useJdk("OPENJDK17")
                  sh "git submodule init"
                  sh "git submodule update --remote api-ti-messenger"
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
                  mavenBuild(POM_PATH)
              }
          }

          stage('Tests') {
              steps {
                  mavenVerify(POM_PATH, "-P=ci-pipeline")
              }
          }

          stage('Commit new Tiger version when needed') {
              environment {
                  UPDATE_FLAG = "${UPDATE}"
              }
               steps {
                   script {
                        if (UPDATE_FLAG == true) {
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
                     if (UPDATE == true)
                         sendEMailNotification(getCommunicationsEMailList() + "," + getTigerEMailList())
                }
           }

           failure {
                script {
                     if (UPDATE == true)
                         sendEMailNotification(getCommunicationsEMailList() + "," + getTigerEMailList())
                }
           }
      }
}
