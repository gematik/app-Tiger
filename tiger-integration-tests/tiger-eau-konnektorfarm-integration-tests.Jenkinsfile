@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/Testtools/tiger-integration-eau')
def BRANCH = 'main'
def POM_PATH = 'pom.xml'

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

          stage('Set Tiger version in eAU') {
              steps {
                   sh "sed -i -e 's@<tiger.version>.*</tiger.version>@<tiger.version>${TIGER_VERSION}</tiger.version>@' pom.xml"
              }
          }

          stage('Deactivate workflow UI') {
              steps {
                  script{
                      activateWorkflowUi=false
                  }
                  sh "sed -i -e 's@activateWorkflowUi: [^ ]*@activateWorkflowUi: ${activateWorkflowUi}@' src/test/resources/tiger.yaml"
              }
          }

          // do not build with skipTests flag first as the serenity maven plugin will bail out not finding any report

          stage('Tests and Konnektor connection') {
              steps {
                    script{
                         password="b672\$#H%@eki66"
                         username="gematik_master"
                    }
                    withEnv(["GEMATIK_PASSWORD=${password}", "GEMATIK_USER=${username}"]){
                         mavenVerify(POM_PATH, '-Dcucumber.filter.tags="@IntegrationTest and not @Test and not @Demo"')
                    }
              }
          }

          stage('Activate workflow UI') {
              steps {
                  script{
                      activateWorkflowUi=true
                  }
                  sh "sed -i -e 's@activateWorkflowUi: [^ ]*@activateWorkflowUi: ${activateWorkflowUi}@' src/test/resources/tiger.yaml"
              }
          }

          stage('Commit new Tiger version when needed') {
                steps {
                    script {
                        if (params.UPDATE == 'YES') {
                            catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                sh "sed -i -e 's@<tiger.version>.*</tiger.version>@<tiger.version>${TIGER_VERSION}</tiger.version>@' pom.xml"
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
                    sendEMailNotification("rafael.schirru@gematik.de" + "," + "thomas.eitzenberger@gematik.de" + "," + "juliane.baerwind@gematik.de")
             }
          }

          failure {
             script {
                if (params.UPDATE == 'YES')
                    sendEMailNotification("rafael.schirru@gematik.de" + "," + "thomas.eitzenberger@gematik.de" + "," + "juliane.baerwind@gematik.de")
             }
          }
      }
}
