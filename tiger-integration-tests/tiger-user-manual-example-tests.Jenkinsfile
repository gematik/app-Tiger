@Library('gematik-jenkins-shared-library') _/*
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

// testing the doc/example/tigerOnly folder contains a working setup / tiger project


def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
def BRANCH = 'master'
def JIRA_PROJECT_ID = 'TGR'
def GITLAB_PROJECT_ID = '644'
def TAG_NAME = "ci/build"
def POM_PATH = 'pom.xml'
def POM_PATH_PRODUCT = 'doc/examples/tigerOnly/pom.xml'

pipeline {
    options {
        disableConcurrentBuilds()
    }
    agent { label 'k8-backend-large' }

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

            stage('Set Tiger version in tigerOnly Example') {
            steps {
                sh """
                cd doc/examples/tigerOnly
                grep -q version.tiger ${POM_PATH}
                sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' ${POM_PATH}
                """
            }
        }

        stage('Build') {
            steps {
                sh """
                cd doc/examples/tigerOnly
                mvn -ntp verify -DskipTests
                """
            }
        }

        stage('Tests') {
            steps {
                sh """
                cd doc/examples/tigerOnly
                rm -rf target/site/serenity
                mvn verify && [ -d "target/site/serenity" ]
                """
            }
        }

        stage('Commit new Tiger version when needed') {
            steps {
                script {
                    if (params.UPDATE == 'YES') {
                        catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                            sh """
                            cd doc/examples/tigerOnly
                            sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' ${POM_PATH}
                            """
                            sh """
                                cd doc/examples/tigerOnly
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
        always {
            sendEMailNotification(getTigerEMailList())
            showJUnitAsXUnitResult("**/target/*-reports/TEST-*.xml")
        }
    }
}
