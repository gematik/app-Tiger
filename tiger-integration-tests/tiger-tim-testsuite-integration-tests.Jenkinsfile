@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/communications/ti-m/ti-m-testsuite')
def BRANCH = 'main'
def POM_PATH_TEST = 'testsuite/pom.xml'
def POM_PATH_BUILD = 'pom.xml'

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

          stage('Set Tiger version in TI-M') {
              steps {
                   sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' ${POM_PATH_TEST}"
              }
          }

          stage('Build') {
              steps {
                  mavenBuild(POM_PATH_BUILD)
              }
          }

          stage('Tests') {
              steps {
                  mavenVerify(POM_PATH_TEST, "-P=ci-pipeline")
              }
          }

          stage('Commit new Tiger version when needed') {
              steps {
                    catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                        sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' ${POM_PATH_TEST}"
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
             sendEMailNotification(getCommunicationsEMailList(), getTigerEMailList())
         }
      }
}
