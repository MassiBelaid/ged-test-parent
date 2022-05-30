pipeline {
    agent {
        docker {
            image 'sdk:latest'
            args '-v /docker/volume/jenkins-slave/maven:/data/maven/ --net=host'
        }
    }
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '5', daysToKeepStr: '30'))
    }
    parameters {
        choice(name: 'RELEASE', choices: ['BUILD', 'PATCH', 'MINOR', 'MAJOR' ], description: 'Realese type')
    }
    stages {
        stage('Clean, checkout project and SDK') {
            steps{
                deleteDir()
                checkout(changelog: false, scm: scm)
                sh "prj sel ."
            }
        }
        stage ('Set release version'){
            steps{
                sh "sdk version -t ${params.RELEASE}"
            }
        }
        stage('Deploy artifacts'){
            steps {
                // deploy on AtoS nexus
                sh 'sdk deploy'
            }
        }
        stage('Set snapshot version'){
            steps{
                sh "sdk version -t snapshot"
            }
        }
        stage('Push '){
            steps{
                // push tag and versions
                withCredentials([usernamePassword(credentialsId: '5ec4f4cb-1973-452d-a97e-b3b1d14c5d83', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                    sh('git push --follow-tags ${GIT_URL%://*}://${GIT_USERNAME}:${GIT_PASSWORD}@${GIT_URL#*://} ${GIT_BRANCH}')
                }
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
