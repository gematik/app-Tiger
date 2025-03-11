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

pipeline {

    options {
        disableConcurrentBuilds()
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')
    }

    agent { label 'k8-backend-small' }

    stages {
        stage('Activate Cache') {
            steps {
                activateOptionalBuildCache()
            }
        }

        stage('Run Dependabots') {
            parallel {
                stage('maven') {
                    steps {
                         dependabot('maven', 'git/Testtools/tiger/tiger')
                    }
                }
                stage('npm_and_yarn') {
                    steps {
                         dependabot('npm_and_yarn', 'git/Testtools/tiger/tiger')
                    }
                }
            }
        }
    }
}
