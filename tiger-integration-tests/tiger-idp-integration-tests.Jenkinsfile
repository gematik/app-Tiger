@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/idp/idp-global')
def BRANCH = 'master'
def POM_PATH = 'pom.xml'

pipeline {
      options {
          disableConcurrentBuilds()
      }
      agent { label 'Docker-Maven' }

      tools {
          maven 'Default'
      }

      parameters {
          string(name: 'TIGER_VERSION', defaultValue: '', description: 'Bitte die nächste Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
      }

      // TODO: TGR-355 "Integrationstest für unwissentliche Changes aufbereiten"
      stages {
          stage('Checkout') {
              steps {
                  git branch: BRANCH,
                      credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                      url: REPO_URL
              }
          }

          // TODO: JSL-122 "Funktion benötigt, um die beliebige Dependencies in pom verändern zu können"
          stage('Set Tiger version in IDP') {
              steps {
                  sh "mvn versions:set-property -Dproperty=idp-global.tiger -DnewVersion=${TIGER_VERSION}"
              }
          }

          stage('Build') {
              steps {
                  mavenBuild(POM_PATH)
              }
          }

          stage('Tests') {
              steps {
                   withCredentials([string(credentialsId: 'GITHUB.API.Token', variable: 'GITHUB_TOKEN')]) {
                       dockerLogin()
                       mavenVerify(POM_PATH, "-Dwdm.gitHubToken=$GITHUB_TOKEN -PWithUiTests")
                       dockerLogout()
                   }
              }
          }
      }

      post {
         always {
             sendEMailNotification(getIdpEMailList(), getTigerEMailList())
         }
      }
}
