@Library('gematik-jenkins-shared-library') _

def runKimStages(kimVersion) {
    // init kim sim
    executeRun("Copy Config", "config", kimVersion)
    executeRun("Start KIM Simulation", "kimSim", kimVersion)
    executeRun("Start Tiger Proxies", "tiger", kimVersion)
    echo "KIM Simulation gestartet – warte 60 Sekunden..."
    sleep 60
    executeRun("Start CM Simulation", "cmSim", kimVersion)
    executeRun("Init Data", "init", kimVersion)
    executeRun("Start AutoResponderMailClient", "ar", kimVersion)

    // run tests
    executeTest("Run IOP-KIM-Nachrichten->KIM_IOP_MAIL_001", "-t TCID:KIM_IOP_MAIL_001 IOP-KIM-Nachrichten.feature", kimVersion)
}

def executeRun(desc, parameter, kimVersion) {
    execute(desc, "run.sh", parameter, kimVersion)
}

def executeTest(desc, parameter, kimVersion) {
    execute(desc, "runTest.sh", parameter, kimVersion)
}

def execute(desc, scriptName, parameter, kimVersion) {
    echo "Starte: ${desc}"
    dir('kim-tiger-dc') {
        withEnv(['NO_UI=true', "DOCKER_HOSTNAME=${env.DOCKER_HOSTNAME}"]) {
            sh "./${scriptName} tigerVersion=${params.TIGER_VERSION} kimVersion=${kimVersion} ${parameter}"
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
        string(name: 'TIGER_VERSION', defaultValue: '3.6.0', description: 'Bitte die aktuelle Version für das Projekt eingeben, format [0-9]+.[0-9]+.[0-9]+ \nHinweis: Version 0.0.[0-9] ist keine gültige Version!')
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
        stage('Checkout KIM-Simulator') {
            steps {
                script {
                    dir('kim-tiger-dc') {
                        git url: "https://gitlab.prod.ccs.gematik.solutions/git/communications/kim-tiger/kim-tiger-dc",
                            branch: KIM_BRANCH,
                            credentialsId: 'svc_gitlab_prod_credentials'
                    }
                }
            }
        }
        stage('Checkout KIM-Tiger-Testsuite') {
            steps {
                script {
                    dir('kim-tiger-testsuite') {
                        git url: "https://gitlab.prod.ccs.gematik.solutions/git/communications/kim-tiger/kim-tiger-testsuite",
                            branch: KIM_BRANCH,
                            credentialsId: 'svc_gitlab_prod_credentials'
                    }
                }
            }
        }
        stage('Login to Gematik Registry') {
            steps {
                dockerLoginGematikRegistry()
            }
        }
        stage('Run Kim Test for KIM v1_5_2') {
            steps {
                timeout(time: 20, unit: 'MINUTES') {
                    script {
                        runKimStages("1_5_2")
                    }
                }
            }
        }
    }

    post {
        always {
            //kim cleanup
            dir('kim-tiger-dc') {
                withEnv(['NO_UI=true', "DOCKER_HOSTNAME=${env.DOCKER_HOSTNAME}"]) {
                    script {
                        sh "./stop.sh"
                    }
                }
            }

            // Archive Tiger proxy logs as artifacts
            archiveArtifacts artifacts: 'kim-tiger-dc/tiger-proxies-nodocker/logs/*.log',
                             allowEmptyArchive: true,
                             fingerprint: true
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
            script {
                if (params.UPDATE == 'YES')
                    sendEMailNotification(getTigerEMailList() + "," + getCommunicationsEMailList())
            }
        }
    }
}
