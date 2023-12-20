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

          stage('Build') {
              steps {
                  mavenBuild(POM_PATH)
              }
          }

        stage('Tests') {
            environment {
                POLARION_CREDENTIALS = credentials('svc_aurora_test')
            }
            steps {
                mavenVerify(POM_PATH, "-DPolarionPassword=$POLARION_CREDENTIALS_PSW")
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
