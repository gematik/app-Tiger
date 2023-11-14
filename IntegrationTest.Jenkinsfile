@Library('gematik-jenkins-shared-library') _

def JIRA_PROJECT_ID = 'TGR'
// using tiger as artefact id fails as the metadata xml fo tiger is purged randomly on nexus (SWF-247)
def ARTIFACT_ID = 'tiger-testenv-mgr'
def GROUP_ID = "de.gematik.test"


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
         stage('Apollo-Testsuite Integrationtest') {
             steps {
                  script {
                       if (!NEW_VERSION?.trim()) {
                             VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                             NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                       }
                  }
                  catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                      build job: 'Tiger-Integrationtest-TIGER-Apollo-Testsuite',
                      parameters: [
                           string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                           string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
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
                     build job: 'Tiger-Integrationtest-TIGER-Authenticator',
                     parameters: [
                         string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                         string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
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
                build job: 'Tiger-Integrationtest-TIGER-EAU',
                parameters: [
                   string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                   string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
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
                  build job: 'Tiger-Integrationtest-TIGER-IDP',
                  parameters: [
                       string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                       string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
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
                   build job: 'Tiger-Integrationtest-TIGER-Konnektor-e2e',
                   parameters: [
                       string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                       string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
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
                   build job: 'Tiger-Integrationtest-TIGER-TI-M-Testsuite',
                   parameters: [
                        string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                        string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
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
                   build job: 'Tiger-Integrationtest-TIGER-TKME',
                   parameters: [
                        string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                        string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
                   ]
                   }
              }
         }

          stage('PolarionToolbox-Integrationtest') {
                       steps {
                             script {
                                  if (!NEW_VERSION?.trim()) {
                                        VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                                        NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                                  }
                             }
                            catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                            build job: 'Tiger-Integrationtest-TIGER-Polarion-Toolbox',
                            parameters: [
                                 string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                                 string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
                            ]
                            }
                       }
          }

          stage('Tiger-Cloud-Extension-Integrationtest') {
                       steps {
                             script {
                                  if (!NEW_VERSION?.trim()) {
                                        VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                                        NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                                  }
                             }
                            catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                            build job: 'Tiger-Integrationtest-TIGER-Cloud-Extension',
                            parameters: [
                                 string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                                 string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
                            ]
                            }
                       }
          }

          stage('Tiger-Pssim-Extension-Integrationtest') {
                       steps {
                             script {
                                  if (!NEW_VERSION?.trim()) {
                                        VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                                        NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                                  }
                             }
                            catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                            build job: 'Tiger-Integrationtest-TIGER-PSSIM',
                            parameters: [
                                 string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                                 string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
                            ]
                            }
                       }
          }

          stage('Tiger-on-Fhir Integrationtest') {
             steps {
                 script {
                      if (!NEW_VERSION?.trim()) {
                               VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                               NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                      }
                 }
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                build job: 'Tiger-Integrationtest-TIGER-FHIR',
                parameters: [
                   string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                   string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
                ]
                }
             }
         }
          stage('Tiger Manual ExampleIntegrationtest') {
             steps {
                 script {
                      if (!NEW_VERSION?.trim()) {
                               VERSION = jiraCheckAndGetSingleVersion(jiraGetVersions(JIRA_PROJECT_ID))
                               NEW_VERSION = nexusGetLatestVersion(VERSION, ARTIFACT_ID, GROUP_ID).trim()
                      }
                 }
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                build job: 'Tiger-Integrationtest-TIGER-Manual-Example',
                parameters: [
                   string(name: 'TIGER_VERSION', value: String.valueOf("${NEW_VERSION}")),
                   string(name: 'UPDATE', value: String.valueOf(params.UPDATE)),
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
