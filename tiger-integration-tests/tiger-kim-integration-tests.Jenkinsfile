@Library('gematik-jenkins-shared-library') _

def executeRun(desc, parameter, kimVersion, tigerVersion) {
    execute(desc, parameter, kimVersion, tigerVersion)
}

def executeTest(desc, parameter, kimVersion, tigerVersion) {
    echo "Test: ${desc} (kim=${kimVersion}) (tiger=${tigerVersion}) ${parameter}"
    def archiveRootDir = "serenity-report-${desc}-v${kimVersion}"
    dir('kim-tiger-dc') {
        withEnv(['kim_sim_startup_delay_sec=120', "DOCKER_HOSTNAME=${env.DOCKER_HOSTNAME}", "kim_version=${kimVersion}", "tiger_version=${tigerVersion}"]) {
            echo desc

            def testStatus = 'Failed'  // Default to failed
            catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                def exitCode = sh(script: "./runCM.sh ${parameter} --no_UI --exitOnFinish", returnStatus: true)
                testStatus = exitCode == 0 ? 'Passed' : 'Failed'
            }
            echoMemoryUsage()

            // Archive Serenity reports, test log, and info file
            def archiveDir = "../${archiveRootDir}"
            sh "mkdir -p ${archiveDir}"
            sh "cp -r target/site/serenity/* ${archiveDir}/ || true"
            sh "cp tiger-proxies-nodocker/logs/test_run.log ${archiveDir}/ || true"

            // Create and save test info file
            writeFile file: "${archiveDir}/test_info.txt", text: """Test Description: ${desc}
Parameters: ${parameter}
KIM Version: ${kimVersion}
Date: ${new Date()}
Test Status: ${testStatus}
"""
        }
    }

    // Archive the Serenity reports, logs, and info file
    // this ignore the command dir('kim-tiger-dc')
    archiveArtifacts artifacts: "${archiveRootDir}/**/*",
                        allowEmptyArchive: true,
                        fingerprint: true
}

def execute(desc, parameter, kimVersion, tigerVersion) {
    echo "Starting: ${desc} (kim=${kimVersion}) (tiger=${tigerVersion}) ${parameter}"
    dir('kim-tiger-dc') {
        withEnv(['kim_sim_startup_delay_sec=120', "DOCKER_HOSTNAME=${env.DOCKER_HOSTNAME}", "kim_version=${kimVersion}", "tiger_version=${tigerVersion}"]) {
            sh "./runCM.sh ${parameter} --no_UI"
            echoMemoryUsage()
        }
    }
}

def echoMemoryUsage() {
    sh 'free -m'
}

def archiveKimVersionLogs(kimVersion) {
    echo "Archiving logs for KIM version ${kimVersion}"
    sh "mkdir -p kim-logs-v${kimVersion}"
    sh "cp -r kim-tiger-dc/tiger-proxies-nodocker/logs/* kim-logs-v${kimVersion}/ || true"
    archiveArtifacts artifacts: "kim-logs-v${kimVersion}/**/*.log",
                     allowEmptyArchive: true,
                     fingerprint: true
}

def stopKimSim(){
    executeRun("stop kim sim", "--command.command=stop_all_processes --exitOnFinish", "not-set", "not-set")
}

def runKimStages(kimVersion) {
    executeTest("run", "--command.command=start_kim_sim_and_run_tests_smoke_test --command.command-on-exit=stop_all_processes", kimVersion, env.TIGER_VERSION)

    // Archive logs for this specific KIM version
    archiveKimVersionLogs(kimVersion)
}
def checkoutProject(projectName){
    dir(projectName) {
        try {
            // Try to check out the current branch directly
            echo "Attempting to checkout ${projectName} with branch ${env.BRANCH_NAME}"
            git url: "https://gitlab.prod.ccs.gematik.solutions/git/communications/kim-tiger/${projectName}",
                branch: "${env.BRANCH_NAME}",
                credentialsId: 'svc_gitlab_prod_credentials'
        } catch (Exception e) {
            // If that fails, use the 'main' branch
            echo "Branch ${env.BRANCH_NAME} not found. Falling back to 'main' branch"
            git url: "https://gitlab.prod.ccs.gematik.solutions/git/communications/kim-tiger/${projectName}",
                branch: 'main',
                credentialsId: 'svc_gitlab_prod_credentials'
        }
    }
}

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
def REPO_URL = createGitUrl('git/Testtools/tiger/tiger-konnektor-management-extensions')
def BRANCH = 'master'
def POM_PATH = 'pom.xml'
def KIM_BRANCH = 'main'

pipeline {
    options {
        disableConcurrentBuilds()
    }
    agent { label 'k8-backend-large' }

    tools {
        maven 'Default'
    }

    parameters {
        string(name: 'TIGER_VERSION', defaultValue: '3.6.1', description: 'Bitte die aktuelle Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
        choice(name: 'UPDATE', choices: ['NO', 'YES'], description: 'Flag, um zu prüfen, ob die neue Tiger-Version in einigen Projekten aktualisiert werden soll')
    }

    stages {
        stage('Initialize') {
            steps {
                useJdk('OPENJDK17')
                script {
                    env.DOCKER_HOSTNAME = dockerGetCurrentHostname()
                    echo "TIGER_VERSION: ${params.TIGER_VERSION}"
                    echo "DOCKER_HOSTNAME: ${env.DOCKER_HOSTNAME}"
                }
            }
        }

        stage('Activate Cache') {
            steps {
                activateOptionalBuildCache()
            }
        }

        stage('Checkout KIM-Tiger-DC') {
            steps {
                script {
                    checkoutProject('kim-tiger-dc')
                }
            }
        }

        stage('Checkout Kim-Tiger-Testsuite') {
            steps {
                script {
                    checkoutProject('kim-tiger-testsuite')
                }
            }
        }

        stage('Checkout KIM-Tiger-Lib') {
            steps {
                script {
                    checkoutProject('kim-tiger-lib')
                }
            }
        }

        stage('Install KIM-Tiger-Runner') {
            steps {
                script {
                    dir('kim-tiger-lib/kim-tiger-runner') {
                        sh 'mvn clean install -DskipTests'
                    }
                }
            }
        }

        stage('Login to Gematik Registry') {
            steps {
                dockerLoginGematikRegistry()
            }
        }
        stage('Run Kim Test for KIM v1_5_3') {
            steps {
                timeout(time: 20, unit: 'MINUTES') {
                    script {
                        runKimStages("1.5.3")
                    }
                }
            }
        }
    }

    post {
        always {
            //kim cleanup
            stopKimSim()
        }
        changed {
            sendEMailNotification(getTigerEMailList())
        }
        success {
            script {
                if (params.UPDATE == 'YES')
                    sendEMailNotification(getTigerEMailList() + "," + getCommunicationsEMailList())
            }
        }
        failure {
            // Archive Tiger proxy logs as artifacts (as a fallback)
            archiveArtifacts artifacts: 'kim-tiger-dc/tiger-proxies-nodocker/logs/*.log',
                             allowEmptyArchive: true,
                             fingerprint: true
            script {
                if (params.UPDATE == 'YES')
                    sendEMailNotification(getTigerEMailList() + "," + getCommunicationsEMailList())
            }
        }
    }
}
