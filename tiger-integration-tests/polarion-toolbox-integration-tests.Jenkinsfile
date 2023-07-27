@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/Testtools/tiger/polariontoolbox')
def BRANCH = 'master'
def POM_PATH = 'pom.xml'

pipeline {
      options {
          disableConcurrentBuilds()
      }
      agent { label 'LTU-DEV-Docker-Maven' }

      tools {
          maven 'Default'
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

          stage('Set Tiger version in Polarion Toolbox') {
              steps {
                   sh "sed -i -e 's@<version.tiger-test-lib>.*</version.tiger-test-lib>@<version.tiger-test-lib>${TIGER_VERSION}</version.tiger-test-lib>@' ${POM_PATH}"
              }
          }

          stage('Build') {
              steps {
                  mavenBuild(POM_PATH)
              }
          }

        stage('Tests') {
            environment {
                POLARION_CREDENTIALS = credentials('svc_aurora')
            }
            steps {
                mavenVerify(POM_PATH, "-DPolarionPassword=$POLARION_CREDENTIALS_PSW")
            }
        }

          stage('Commit new Tiger version when needed') {
                        steps {
                             script {
                                if (params.UPDATE == 'YES') {
                                      catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                          sh "sed -i -e 's@<version.tiger-test-lib>.*</version.tiger-test-lib>@<version.tiger-test-lib>${TIGER_VERSION}</version.tiger-test-lib>@' ${POM_PATH}"
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
                     if (params.UPDATE == 'YES') {
                         sendEMailNotification(getTigerEMailList())
                     }
                 }
            }

            failure {
                 script {
                     if (params.UPDATE == 'YES')
                         sendEMailNotification(getTigerEMailList())
                 }
            }
       }
}
