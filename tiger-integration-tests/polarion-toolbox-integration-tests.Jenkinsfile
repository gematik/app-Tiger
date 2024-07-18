@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
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

          stage('Test') {
              environment {
                  POLARION_CREDENTIALS_DEV= credentials('svc_aurora_test_PAT_DEV')
                  POTO_PASSWORD = "$POLARION_CREDENTIALS_DEV_PSW"
                  POLARION_CREDENTIALS_TEST = credentials('svc_aurora_test_PAT_TEST')
              }
              steps {
                  mavenVerify(POM_PATH, "-DPolarionPasswordDev=$POLARION_CREDENTIALS_DEV_PSW -DPolarionPasswordTest=$POLARION_CREDENTIALS_TEST_PSW")
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
