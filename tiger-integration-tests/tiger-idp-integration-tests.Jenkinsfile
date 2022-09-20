@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/idp/idp-global')
def BRANCH = 'master'
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
             }
          }

          stage('Checkout') {
              steps {
                  git branch: BRANCH,
                      credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                      url: REPO_URL
              }
          }

          stage('Set Tiger Integrationtest Version for IDP') {
              steps {
                script {
                    mavenSetVersion("TigerIntegrationTest", POM_PATH)
                }
              }
          }

          stage('Set Tiger version in IDP') {
              steps {
                   sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' pom.xml"
              }
          }

          stage('Build') {
              steps {
                  mavenBuild(POM_PATH, '-Dskip.unittests -Dskip.inttests')
              }
          }

          stage('Tests') {
              steps {
                   mavenVerify(POM_PATH, '-ntp -Dskip.unittests -Dcucumber.filter.tags="@Approval and not @OpenBug and not @WiP and not @LongRunning"')
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
                      sendEMailNotification(getIdpEMailList() + "," + getTigerEMailList())
             }
         }

         failure {
              script {
                   if (UPDATE == true)
                      sendEMailNotification(getIdpEMailList() + "," + getTigerEMailList())
              }
         }
      }
}
