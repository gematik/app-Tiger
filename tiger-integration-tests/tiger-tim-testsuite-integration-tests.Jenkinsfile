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
def REPO_URL = createGitUrl('git/communications/ti-m/ti-m-testsuite')
def BRANCH = 'main'
def POM = 'pom.xml'

pipeline {
      options {
          disableConcurrentBuilds()
          buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10')
      }
      agent { label 'k8-backend-large' }

      tools {
          maven 'Default'
      }

      environment {
              TIM_KEYSTORE_PW = 'gematik123'
              TIM_TRUSTSTORE_PW = 'gematik123'
              TIM_TRUSTSTORE = 'src/main/resources/certs/truststore.p12'
              TIM_KEYSTORE = 'src/main/resources/certs/c_keystore.p12'
              BRANCH_LOWER_CASE = sh(returnStdout: true, script: "echo ${BRANCH_NAME} | tr '[:upper:]' '[:lower:]' | tr '/' '_' | tr -d '\n'")

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

        stage('Set Tiger version in TI-M') {
            steps {
                sh "grep -q tiger.version ${POM}"
                sh "sed -i -e 's@<tiger.version>.*</tiger.version>@<tiger.version>${TIGER_VERSION}</tiger.version>@' ${POM}"
            }
        }

        stage('Build') {
            steps {
                mavenBuild(POM,"-Dprofile.ci")
            }
        }

        stage('Start DB') {
            steps {
                sh("docker compose -p ${BRANCH_LOWER_CASE} up -d")
            }
        }

        stage('Test') {
            environment {
                TIGER_DOCKER_HOST = dockerGetCurrentHostname()
                TIM_DB_PORT = sh(returnStdout: true, script: "docker ps --format='{{.Names}} {{.Ports}}' | grep '${BRANCH_LOWER_CASE}' | cut -d \' \' -f2 | grep -oP ':\\K\\d+(?=-)' | tr -d '\\n'")

            }
            steps {
                mavenVerify(POM, "-Dprofile.ci")
            }
        }

        stage('Commit new Tiger version when needed') {
            steps {
                script {
                    if (params.UPDATE == 'YES') {
                        catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                            sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' ${POM}"
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
                     sendEMailNotification(getCommunicationsEMailList() + "," + getTigerEMailList())
            }
        }

        failure {
            script {
                 if (params.UPDATE == 'YES')
                     sendEMailNotification(getCommunicationsEMailList() + "," + getTigerEMailList())
            }
        }
    }
}
