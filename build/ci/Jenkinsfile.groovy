pipeline {
    agent {
        docker {
            image 'sdk:latest'
            args '-v /docker/volume/jenkins-slave/maven:/data/maven/ --net=host'
        }
    }
    triggers {
        pollSCM('H/5 * * * *')
    }
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '5', daysToKeepStr: '30'))
    }
    stages {
        stage('Clean, checkout project and SDK') {
            steps{
                deleteDir()
                checkout(changelog: false, scm: scm)
                sh "prj sel ."
            }
        }
        stage('Build') {
            steps {
                sh "mvn install"
            }
        }
        stage('Publish maven artifacts on Nexus') {
            steps {
                sh "sdk deploy"
            }
        }
    }
    post {
        always {
            step([$class: 'JUnitResultArchiver', testResults: '**/target/*-reports/TEST-*.xml', allowEmptyResults: true])
            archiveArtifacts allowEmptyArchive: true, artifacts: '*.log', caseSensitive: false, defaultExcludes: false
            step([$class: 'Mailer', recipients: [emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])].join(' ')])
        }
    }
}
