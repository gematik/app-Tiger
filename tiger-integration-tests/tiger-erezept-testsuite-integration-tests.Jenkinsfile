@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/erezept/fachdienst/erp-e2e')
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
          string(name: 'TIGER_VERSION', defaultValue: '', description: 'Bitte die nächste Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
      }

      stages {
          stage('Checkout') {
              steps {
                  git branch: BRANCH,
                      credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                      url: REPO_URL
              }
          }

          stage('Set Tiger version in Erezept Testsuite') {
              steps {
                   sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' pom.xml"
              }
          }

          stage('Build') {
              steps {
                  mavenBuild(POM_PATH, '-Dskip.unittests=true -Dskip.inttests=true')
              }
          }

          stage('Tests') {
              steps {
                  mavenTest()
              }
          }
      }

      post {
          always {
                sendEMailNotification(getErpE2ETestsuiteEMailList() + "," + getTigerEMailList())
          }
      }
}