@Library('gematik-jenkins-shared-library') _

def JIRA_PROJECT_ID = 'TGR'
def ARTIFACT_ID = 'tiger'
def GROUP_ID = "de.gematik.test"


pipeline {
    options {
        disableConcurrentBuilds()
    }

   agent { label 'k8-maven' }

   tools {
        maven 'Default'
    }

   parameters {
            string(name: 'NEW_VERSION', defaultValue: "", description: 'Version, die in anderen Projekten jede Nacht geprüft werden soll. Default: Snapshot Version vom Nexus')
            booleanParam(name: 'UPDATE', defaultValue: false, description: 'Flag, um zu prüfen, ob die neue Tiger-Version in einigen Projekten aktualisiert werden soll. Default: false')
   }


   stages {
         stage('Apollo-Testsuite Integrationtest') {
             steps {
                  script {
                       if (!NEW_VERSION?.trim()) {
                             VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                             NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                       }
                  }
                  catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                      build job: 'Tiger-Integrationtest-Apollo-Testsuite',
                      parameters: [
                           string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                           booleanParam(name: 'UPDATE', value: UPDATE),
                      ]
                  }
             }
         }
         stage('Authenticator Integrationtest') {
             steps {
                 script {
                      if (!NEW_VERSION?.trim()) {
                           VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                           NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                      }
                 }
                 catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                     build job: 'Tiger-Integrationtest-Authenticator',
                     parameters: [
                         string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                         booleanParam(name: 'UPDATE', value: UPDATE),
                     ]
                 }
             }
         }

         stage('EAU Integrationtest') {
             steps {
                 script {
                      if (!NEW_VERSION?.trim()) {
                               VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                               NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                      }
                 }
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                build job: 'Tiger-Integrationtest-EAU',
                parameters: [
                   string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                   booleanParam(name: 'UPDATE', value: UPDATE),
                ]
                }
             }
         }

         stage('Tiger-Integrationtest-Erezept-Testsuite') {
             steps {
                 script {
                     if (!NEW_VERSION?.trim()) {
                               VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                               NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                     }
                 }
                 catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                 build job: 'Tiger-Integrationtest-Erezept-Testsuite',
                 parameters: [
                      string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                      booleanParam(name: 'UPDATE', value: UPDATE),
                 ]
                 }
             }
         }

         stage('IDP-Integrationtest') {
              steps {
                   script {
                        if (!NEW_VERSION?.trim()) {
                                VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                                NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                        }
                   }
                  catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                  build job: 'Tiger-Integrationtest-IDP',
                  parameters: [
                       string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                       booleanParam(name: 'UPDATE', value: UPDATE),
                  ]
                  }
              }
         }

         stage('Konnektor-e2e-Integrationtest') {
              steps {
                   script {
                        if (!NEW_VERSION?.trim()) {
                             VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                             NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                        }
                   }
                   catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                   build job: 'Tiger-Integrationtest-Konnektor-e2e',
                   parameters: [
                       string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                       booleanParam(name: 'UPDATE', value: UPDATE),
                   ]
                   }
              }
         }

         stage('TI-M-Testsuite-Integrationtest') {
              steps {
                   script {
                        if (!NEW_VERSION?.trim()) {
                               VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                               NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                        }
                   }
                   catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                   build job: 'Tiger-Integrationtest-TI-M-Testsuite',
                   parameters: [
                        string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                        booleanParam(name: 'UPDATE', value: UPDATE),
                   ]
                   }
              }
         }

         stage('TKME-Integrationtest') {
              steps {
                    script {
                         if (!NEW_VERSION?.trim()) {
                               VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                               NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                         }
                    }
                   catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                   build job: 'Tiger-Integrationtest-TKME',
                   parameters: [
                        string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                        booleanParam(name: 'UPDATE', value: UPDATE),
                   ]
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
