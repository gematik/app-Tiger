@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/Testtools/tiger/tiger-pssim-extension')
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
           string(name: 'TIGER_VERSION', description: 'Bitte die aktuelle Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
           choice(name: 'UPDATE', choices: ['NO', 'YES'], description: 'Flag, um zu prüfen, ob die neue Tiger-Version in einigen Projekten aktualisiert werden soll')
      }

    stages {
        stage('Initialise') {
            steps {
                useJdk('OPENJDK17')
                gitSetIdentity()
            }
        }

          stage('Checkout') {
              steps {
                  git branch: BRANCH,
                      credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                      url: REPO_URL
              }
          }

          stage('Set Tiger version in Tiger Pssim Extension') {
              steps {
                   sh "sed -i -e 's@<version.tiger-common>.*</version.tiger-common>@<version.tiger-common>${TIGER_VERSION}</version.tiger-common>@' ${POM_PATH}"
              }
          }

          stage('Build') {
              steps {
                  mavenBuild(POM_PATH)
              }
          }

          stage('Tests') {
              steps {
                  mavenVerify(POM_PATH)
              }
          }

          stage('Commit new Tiger version when needed') {
                        steps {
                             script {
                                if (params.UPDATE == 'YES') {
                                      catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                          sh "sed -i -e 's@<version.tiger-common>.*</version.tiger-common>@<version.tiger-common>${TIGER_VERSION}</version.tiger-common>@' ${POM_PATH}"
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
                         sendEMailNotification(getTigerEMailList() + "," + "rafael.schirru@gematik.de" + "," + "nico.bahlau@gematik.de")
                     }
                 }
            }

            failure {
                 script {
                     if (params.UPDATE == 'YES')
                         sendEMailNotification(getTigerEMailList() + "," + "rafael.schirru@gematik.de" + "," + "nico.bahlau@gematik.de")
                 }
            }
       }
}
