#!groovy

//variable declaration
def GIT_HTTP_URL = 'https://sreekantha.narravula@innersource.accenture.com/scm/xgox/mosri-repo-test.git'
def GIT_SSH_URL = ''
def GIT_REPO_PATH = 'scm/xgox'
def GIT_REPO_NAME= 'mosri-repo-test'
def ARTIFACT_NAME = "apache-maven-3.0.5-bin.tar.gz"
def NEXUS_URL = "http://www.us.apache.org/dist/maven/binaries"
def ARTIFACT_PATH_JEN = "~/jenkins_soa_deploy"
def SERVICE_INSTANCE = "abc"
def PROJECT_NAME="Accenture"
def APPLICATION_NAME="soa"
def SSH_USER=""
def SSH_HOST=""
def DEPLOYMENT_PATH=""
def BUILD_XML_PATH = ""
def BACKUP_PATH=""

pipeline {
    agent any

    options {
        // Only keep the 10 most recent builds
        buildDiscarder(logRotator(numToKeepStr:'10'))
        skipDefaultCheckout(true)
    }

    parameters {
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'GIT_HTTP_USER_CRED')
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'GIT_SSH_USER_CRED')
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'SOA_SSH_USER_KEY_CRED')
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'NEX_SSH_USER_CRED')
        booleanParam(defaultValue: true, description: 'GIT using SSH not HTTP repo', name: 'GIT_SSH_NOT_HTTP')
    }

    stages {
        stage ('Extract Artifact') {
            steps {
                timestamps {
                    //Extracting the file from Nexus
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'params.NEX_SSH_USER_CRED', 
					usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                        sh """
                            echo ${USERNAME}
                            rm -rf ${ARTIFACT_PATH_JEN}
                            mkdir ${ARTIFACT_PATH_JEN} && cd ${ARTIFACT_PATH_JEN}
                            ARTIFACT_VERSION = wget -O - -o /dev/null --http-user=${env.USERNAME} --http-password=${env.PASSWORD} ${NEXUS_URL}/maven-metadata.xml | grep "<version>.*</version>" | sort | uniq | tail -n1 | sed -e "s#\(.*\)\(<version>\)\(.*\)\(</version>\)\(.*\)#\3#g"
                            echo ${ARTIFACT_VERSION}
                            wget --http-user=${env.USERNAME} --http-password=${env.PASSWORD} ${NEXUS_URL}/${ARTIFACT_VERSION}/${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz
                        """
                    }
                }
            }
        }

        stage ('CopyingArtifactToInstance') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Started copying the artifacts to the instance...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sshagent (credentials: [params.SOA_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "Creating jenkins directory on SOA instance
                            ssh ${SSH_USER}@${SSH_HOST} rm -rf ~/jenkins_soa_deploy && mkdir ~/jenkins_soa_deploy
                            cd ${ARTIFACT_PATH_JEN}
                            scp -r ${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz ${SSH_USER}@${SSH_HOST}:~/jenkins_soa_deploy
                            echo "Copy is completed"
                            echo "extracting the files on SOA instance"
                            ssh ${SSH_USER}@${SSH_HOST} cd ~/jenkins_soa_deploy && tar -xvzf ${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz
                        """
                    } 
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Completed copying.'
                    echo '--------------------------------------------------------------------------------------------------------'
                } 
            }
        }

        stage ('Deploy') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy SOA started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sshagent (credentials: [params.SOA_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "Deploying SOA"
                            ssh "${SSH_USER}@${SSH_HOST}" "
                                rm -rf ${BACKUP_PATH}/Integrations/SOA
                                cd ${DEPLOYMENT_PATH} && cp -pr ./Integrations/SOA/* ${BACKUP_PATH}/Integrations/SOA/
                                cp -r ~/jenkins_soa_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}/Integrations/SOA/* ${DEPLOYMENT_PATH}
                                cd ${BUILD_XML_PATH} && withAnt(installation: 'LocalAnt') {	sh "ant build.xml deploy"}
                            "
                        """  
                    }  
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy SOA completed.'
                    echo '--------------------------------------------------------------------------------------------------------'
                } 
            }
        }

        stage ('cleanup') {
            timestamps {
                steps {
                    sshagent (credentials: [params.SOA_SSH_USER_KEY_CRED]) {
                        sh """
                            rm -rf ${ARTIFACT_PATH_JEN}
                            ssh ${SSH_USER}@${SSH_HOST} rm -rf ~/jenkins_soa_deploy
                        """
                    }    
                }
            }
        }
    }
    post {
        always {
            echo "Pipeline completed - result: ${currentBuild.result}"
            deleteDir()
            dir("${env.WORKSPACE}@tmp") {
                deleteDir()
            }
            dir("${env.WORKSPACE}@script") {
                deleteDir()
            }
            dir("${env.WORKSPACE}@script@tmp") {
                deleteDir()
            }
        }
        success {
			echo 'Pipeline Succeeded.'
        }
        failure {
			echo 'Pipleine Failed!'
        }
    }
}




