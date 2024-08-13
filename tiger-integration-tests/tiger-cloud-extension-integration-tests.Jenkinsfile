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


String GCLOUD_SERVICE_ACCOUNT = 'gcp-deployer-tiger-dev-jsonfile'
String GCLOUD_PROJECT_NAME = 'gematik-all-k8s-db-dev'
String GCLOUD_CLUSTER_NAME = 'shared-k8s-dev'
String GCLOUD_CLUSTER_REGION = 'europe-west3-a'
String GEMATIK_NEXUS_CREDENTIALS = 'Nexus'

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
def REPO_URL = createGitUrl('git/Testtools/tiger/tiger-cloud-extension')
def BRANCH = 'master'
def POM_PATH = 'pom.xml'


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

          stage('Set Tiger version in Tiger Cloud Extension Testsuite') {
              steps {
                   sh "grep -q version.tiger.testenv ${POM_PATH}"
                   sh "sed -i -e 's@<version.tiger.testenv>.*</version.tiger.testenv>@<version.tiger.testenv>${TIGER_VERSION}</version.tiger.testenv>@' ${POM_PATH}"
              }
          }

           stage('Cloud Login') {
                      steps {
                          gcloudLogin(GCLOUD_SERVICE_ACCOUNT)
                          gcloudClusterLogin(GCLOUD_PROJECT_NAME, GCLOUD_CLUSTER_NAME, GCLOUD_CLUSTER_REGION)
                          withCredentials([usernamePassword(credentialsId: GEMATIK_NEXUS_CREDENTIALS, passwordVariable: 'NEXUS_PASSWORD', usernameVariable: 'NEXUS_USER')]) {
                             sh """
                                 helm repo add demis-gematik https://nexus.prod.ccs.gematik.solutions/repository/helm_demis/ --force-update --username ${NEXUS_USER} --password '${NEXUS_PASSWORD}'
                                 helm repo add bitnami https://charts.bitnami.com/bitnami
                             """
                          }
                      }
           }


          stage('Build') {
                 steps {
                     mavenBuild(POM_PATH)
                 }
          }
          stage('Test') {
                 environment {
                     TIGER_DOCKER_HOST = dockerGetCurrentHostname()
                 }
                 steps {
                      mavenVerify(POM_PATH)
                 }
          }

          stage('Commit new Tiger version when needed') {
                        steps {
                             script {
                                if (params.UPDATE == 'YES') {
                                      catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                          sh "sed -i -e 's@<tiger.testenv.version>.*</tiger.testenv.version>@<tiger.testenv.version>${TIGER_VERSION}</tiger.testenv.version>@' ${POM_PATH}"
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
