@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'GITLAB.tst_tt_build.Username_Password'
def REPO_URL = createGitUrl('git/idp/idp-global')
def BRANCH = 'master'
def POM_PATH = 'pom.xml'

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

        stage('Checkout') {
            steps {
                git branch: BRANCH,
                        credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                        url: REPO_URL
            }
        }

        stage('Set Tiger rbel version in IDP-server') {
            steps {
                sh "grep -q version.tiger-rbel idp-server/pom.xml"
                sh "sed -i -e 's@<version.tiger-rbel>.*</version.tiger-rbel>@<version.tiger-rbel>${TIGER_VERSION}</version.tiger-rbel>@' idp-server/pom.xml"
            }
        }

        stage('Set Tiger version in IDP-testsuite') {
            steps {
                sh "grep -q version.tiger idp-testsuite/pom.xml"
                sh "sed -i -e 's@<version.tiger>.*</version.tiger>@<version.tiger>${TIGER_VERSION}</version.tiger>@' idp-testsuite/pom.xml"
            }
        }

        stage('Build') {
            steps {
                mavenBuild(POM_PATH, '-Dskip.unittests -Dskip.inttests')
            }
        }

        stage('Tests') {
            steps {
                script {
                    sh label: 'integrationtest', script: """
                        export TIGER_TESTENV_CFGFILE=tiger-jenkins.yaml
                        mvn verify -ntp -Dskip.unittests -Dskip.dockerbuild -Dcucumber.filter.tags="@Approval and not @OpenBug and not @WiP and not @LongRunning"
                        """
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
                    sendEMailNotification(getIdpEMailList() + "," + getTigerEMailList())
            }
        }

        failure {
            script {
                if (params.UPDATE == 'YES')
                    sendEMailNotification(getIdpEMailList() + "," + getTigerEMailList())
            }
        }
    }
}
