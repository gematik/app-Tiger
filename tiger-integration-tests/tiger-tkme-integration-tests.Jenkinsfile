@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/Testtools/tiger/tiger-konnektor-management-extensions')
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

      // TODO: TGR-355 "Integrationstest für unwissentliche Changes aufbereiten"
      stages {
          stage('Checkout') {
              steps {
                  git branch: BRANCH,
                      credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                      url: REPO_URL
              }
          }

          stage('Set Tiger version in TKME') {
              steps {
                   sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' pom.xml"
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
                       mavenVerify(POM_PATH)
                   }
              }
          }

           stage('Commit new Tiger version when needed') {
               steps {
                   catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                          sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' pom.xml"
                          sh """
                              git add -A
                              git commit -m "Tiger version updated"
                              git push origin ${BRANCH}

                          """
                   }
               }
           }
      }

      post {
          always {
             sendEMailNotification(getTigerEMailList())
          }
      }
}