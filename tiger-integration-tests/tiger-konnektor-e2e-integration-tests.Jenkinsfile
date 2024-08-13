/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
def REPO_URL = createGitUrl('git/Testtools/tiger-konnektor-e2e')
def BRANCH = 'Development_1.x'
def POM_PATH_BUILD = 'pom.xml'
def POM_PATH_TEST = 'pom.xml'

pipeline {
      options {
          disableConcurrentBuilds()
      }
      agent { label 'k8-backend' }

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
                   sh "grep -q tiger.version ${POM_PATH_TEST}"
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
