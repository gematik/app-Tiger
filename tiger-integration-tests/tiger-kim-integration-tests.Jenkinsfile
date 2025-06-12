@Library('gematik-jenkins-shared-library') _

def executeKimCommand(desc, parameter, kimVersion, tigerVersion, archiveResults = false) {
    echo "Running: ${desc} (kim=${kimVersion}) (tiger=${tigerVersion}) ${parameter}"
    def archiveRootDir = archiveResults ? "serenity-report-${desc}-v${kimVersion}" : null

    dir('kim-tiger-dc/KIM-CM-Testsuite') {
        withKimEnv(kimVersion, tigerVersion) {
            def testStatus = 'Failed'
            def exitCode = 0

            if (archiveResults) {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    exitCode = sh(script: "./run.sh ${parameter} --no_UI --exitOnFinish", returnStatus: true)
                    testStatus = exitCode == 0 ? 'Passed' : 'Failed'
                    if (exitCode != 0) {
                        error("Test=${desc} fehlgeschlagen mit Exit-Code ${exitCode}")
                    }
                }
            } else {
                sh "./run.sh ${parameter} --no_UI"
            }
            echoMemoryUsage()

            if (archiveResults) {
                archiveTestResults(archiveRootDir, desc, parameter, kimVersion, testStatus)
            }
        }
    }

    if (archiveResults) {
        archiveArtifacts artifacts: "${archiveRootDir}/**/*",
                        allowEmptyArchive: true,
                        fingerprint: true
    }
}

def withKimEnv(kimVersion, tigerVersion, Closure body) {
    withEnv(['kim_sim_startup_delay_sec=120',
             "DOCKER_HOSTNAME=${env.DOCKER_HOSTNAME}",
             "kim_version=${kimVersion}",
             "tiger_version=${tigerVersion}"]) {
        body()
    }
}

def archiveTestResults(archiveDir, desc, parameter, kimVersion, testStatus) {
    def fullArchiveDir = "../${archiveDir}"
    sh "mkdir -p ${fullArchiveDir}"
    sh "cp -r target/site/serenity/* ${fullArchiveDir}/ || true"
    sh "cp KIM-CM-Testsuite/tmp/logs/test_run.log ${fullArchiveDir}/ || true"

    writeFile file: "${fullArchiveDir}/test_info.txt", text: """Test Description: ${desc}
Parameters: ${parameter}
KIM Version: ${kimVersion}
Date: ${new Date()}
Test Status: ${testStatus}
"""
}

def echoMemoryUsage() {
    sh 'free -m'
}

def archiveKimVersionLogs(kimVersion) {
    echo "Archiving logs for KIM version ${kimVersion}"
    sh "mkdir -p kim-logs-v${kimVersion}"
    sh "cp -r kim-tiger-dc/KIM-CM-Testsuite/tmp/logs/* kim-logs-v${kimVersion}/ || true"
    archiveArtifacts artifacts: "kim-logs-v${kimVersion}/**/*.log",
                     allowEmptyArchive: true,
                     fingerprint: true
}

def stopKimSim() {
    executeKimCommand("stop kim sim", "--command.command=stop-all-processes --exitOnFinish", "not-set", "not-set")
}

def runKimStages(kimVersion) {
    executeKimCommand("run", "--command.command=start-kim-sim-and-run-tests-smoke-test --command.command-on-exit=stop-all-processes", kimVersion, env.TIGER_VERSION, true)

    archiveKimVersionLogs(kimVersion)
}

def checkoutProject(projectName) {
    dir(projectName) {
        try {
            echo "Attempting to checkout ${projectName} with branch ${env.BRANCH_NAME}"
            git url: "https://gitlab.prod.ccs.gematik.solutions/git/communications/kim-tiger/${projectName}",
                branch: "${env.BRANCH_NAME}",
                credentialsId: 'svc_gitlab_prod_credentials'
        } catch (Exception e) {
            echo "Branch ${env.BRANCH_NAME} not found. Falling back to 'main' branch"
            git url: "https://gitlab.prod.ccs.gematik.solutions/git/communications/kim-tiger/${projectName}",
                branch: 'main',
                credentialsId: 'svc_gitlab_prod_credentials'
        }
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

        stage("Wait until KIM resources are free") {
            options {
                lock("KIM-Resources-Lock")
            }
            stages {
                stage('Activate Cache') {
                    steps {
                        activateOptionalBuildCache()
                    }
                }

                stage('Checkout Projects') {
                    steps {
                        script {
                            ['kim-tiger-dc', 'KIM-CM-Testsuite', 'KIM-Testsuite-Library'].each { project ->
                                checkoutProject(project)
                            }
                        }
                    }
                }

                stage('Install KIM-Tiger-Runner') {
                    steps {
                        script {
                            dir('KIM-Testsuite-Library/kim-tiger-runner') {
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
        }
    }

    post {
        always {
            stopKimSim()
        }
        success {
            script {
                if (params.UPDATE == 'YES')
                    sendEMailNotification(getTigerEMailList() + "," + getCommunicationsEMailList())
            }
        }
        changed {
            sendEMailNotification(getTigerEMailList())
        }
        failure {
            archiveArtifacts artifacts: 'kim-tiger-dc/KIM-CM-Testsuite/logs/*.log',
                             allowEmptyArchive: true,
                             fingerprint: true
            script {
                if (params.UPDATE == 'YES')
                    sendEMailNotification(getTigerEMailList() + "," + getCommunicationsEMailList())
            }
        }
    }
}
