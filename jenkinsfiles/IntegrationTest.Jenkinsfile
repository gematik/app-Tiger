@Library('gematik-jenkins-shared-library') _

import groovy.transform.Field

@Field def JIRA_PROJECT_ID = 'TGR'

// using tiger as artefact id fails as the metadata xml of tiger is purged randomly on nexus (SWF-247)
// Field annotation allows to use the variables inside of functions
@Field def ARTIFACT_ID = 'tiger-testenv-mgr'
@Field def NEXUS_GROUP_ID = "de.gematik.test"

def CHANNEL_ID = "19:5e4a353b87974bfca7ac6ac55ad7358b@thread.tacv2"
def GROUP_ID = "9c4c4366-476c-465d-8188-940f661574c3"

@Field def TEST_RESULTS = [:]

def runTestJob(jobName, displayName) {
  if (!NEW_VERSION?.trim()) {
    VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
    NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, NEXUS_GROUP_ID).trim()
  }
  TEST_RESULTS[displayName] = "❌ " + NEW_VERSION
  catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
    build job: jobName,
          parameters: [
              string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
              string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
          ]
    TEST_RESULTS[displayName] = "✅ " + NEW_VERSION
  }
}

def runTestJobWithoutVersion(jobName, displayName) {
  TEST_RESULTS[displayName] = "❌ FAILED"
  catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
    build job: jobName
    TEST_RESULTS[displayName] = "✅ SUCCESS"
  }
}

pipeline {
    options {
        disableConcurrentBuilds()
    }

    agent { label 'k8-backend-large' }

    tools {
        maven 'Default'
    }

    parameters {
        string(name: 'NEW_VERSION', defaultValue: "", description: 'Version, die in anderen Projekten jede Nacht geprüft werden soll. Default: Snapshot Version vom Nexus')
        choice(name: 'UPDATE', choices: ['NO', 'YES'], description: 'Flag, um zu prüfen, ob die neue Tiger-Version in einigen Projekten aktualisiert werden soll')
    }


    stages {
      stage ('Parallel Tests') {
        parallel {
          stage('Apollo') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-Apollo-Testsuite', 'Apollo')
              }
          }
          stage('Authenticator') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-Authenticator', 'Authenticator')
              }
          }

          stage('EAU') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-EAU', 'EAU')
              }
          }

          stage('IDP') {
              steps {
                 runTestJob('Tiger-Integrationtest-TIGER-IDP', 'IDP')
              }
          }

          stage('KIM') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-KIM', 'KIM')
              }
          }

          stage('Kon-E2E') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-Konnektor-e2e', 'Kon-E2E')
              }
          }

          stage('TI-M') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-TI-M-Testsuite','TI-M')
              }
          }

          stage('TKME') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-TKME', 'TKME')
              }
          }

          stage('POTO') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-Polarion-Toolbox','POTO')
              }
          }

          stage('Tiger-Cloud') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-Cloud-Extension','Tiger-Cloud')
              }
          }

          stage('Tiger-Pssim') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-PSSIM', 'Tiger-Pssim')
              }
          }

          stage('Tiger-on-Fhir') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-FHIR','Tiger-on-Fhir')
              }
          }
          stage('TGR Example') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-Manual-Example', 'TGR Example')
              }
          }
          stage('TGR Custom Fail Msg') {
              steps {
                  runTestJobWithoutVersion('Tiger-Integrationtest-TIGER-Custom-Fail-Message', 'TGR Custom Fail Msg')
              }
          }
          stage('TGR Cucumber Tags') {
              steps {
                  runTestJob('Tiger-Integrationtest-TIGER-Cucumber-Tags','TGR Cucumber Tags')
              }
          }
        }
      }
    }

    post {
        always {
                teamsSendNotificationToChannel(CHANNEL_ID, GROUP_ID, ["facts": TEST_RESULTS ] )
        }
    }
}
