@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/Testtools/tiger-konnektor-e2e')
def BRANCH = 'Development_1.x'
def POM_PATH_BUILD = 'pom.xml'
def POM_PATH_TEST = 'tigkone2e-testsuite/pom.xml'

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
          stage('Initialize') {
              steps {
                  useJdk('OPENJDK17')
              }
          }

          stage('Checkout') {
              steps {
                  git branch: BRANCH,
                      credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                      url: REPO_URL
              }
          }

          stage('Set Tiger version in tiger-konnektor-e2e') {
              steps {
                   sh "sed -i -e 's@ <tiger.version>.*</tiger.version>@<tiger.version>${TIGER_VERSION}</tiger.version>@' ${POM_PATH_TEST}"
              }
          }

          stage('Build') {
              steps {
                  mavenBuild(POM_PATH_BUILD)
              }
          }

          stage('Tests') {
              steps {
                  mavenVerify(POM_PATH_TEST)
              }
          }

          stage('Commit new Tiger version when needed') {
              steps {
                   script {
                      if (params.UPDATE == 'YES') {
                            catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                sh "sed -i -e 's@ <tiger.version>.*</tiger.version>@<tiger.version>${TIGER_VERSION}</tiger.version>@' ${POM_PATH_TEST}"
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
                      sendEMailNotification(getEdgeDevEMailList() + "," + getTigerEMailList())
              }
          }

          failure {
              script {
                    if (params.UPDATE == 'YES')
                       sendEMailNotification(getIdpEMailList() + "," + getTigerEMailList())
              }
          }
      }
}
