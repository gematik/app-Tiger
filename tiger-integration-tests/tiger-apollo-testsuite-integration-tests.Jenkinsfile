@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/Testtools/epa-iop-tests')
def BRANCH = 'master'
def POM_PATH = 'pom.xml'
def TEAMS_URL = 'https://teams.microsoft.com/l/channel/19%3ac0d9de5fe84a4a3da5278a9446049318%40thread.tacv2/Jenkins-CI?groupId=01cda61b-5c5e-4000-ad6a-7b087feaf0e8&tenantId=30092c62-4dbf-43bf-a33f-10d21b5b660a'


pipeline {
      options {
          disableConcurrentBuilds()
      }
      agent { label 'k8-backend-medium' }

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

          stage('Set Tiger version in Apollo Testsuite') {
              steps {
                   sh "sed -i -e 's@<tiger.version>.*</tiger.version>@<tiger.version>${TIGER_VERSION}</tiger.version>@' ${POM_PATH}"
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
      }

       post {
            changed {
                 sendEMailNotification(getTigerEMailList())
            }
            success {
                 script {
                     if (params.UPDATE == 'YES') {
                         sendEMailNotification(getPatientEMailList() + "," + getTigerEMailList())
                         sendTeamsNotification(TEAMS_URL)
                     }
                 }
            }

            failure {
                 script {
                     if (params.UPDATE == 'YES')
                         sendEMailNotification(getPatientEMailList() + "," + getTigerEMailList())
                 }
            }
       }
}
