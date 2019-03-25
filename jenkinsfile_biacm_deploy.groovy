#!groovy

//variable declaration
def GIT_HTTP_URL = 'https://sreekantha.narravula@innersource.accenture.com/scm/xgox/mosri-repo-test.git'
def GIT_SSH_URL = ''
def GIT_REPO_PATH = 'scm/xgox'
def GIT_REPO_NAME= 'mosri-repo-test'
def ARTIFACT_VERSION = ""
def NEXUS_URL = 'XXX.XXX.XXX.XXX.XXX/nexus/content/repositories/releases/homeoffice/metis/oacs'
def ARTIFACT_PATH_JEN = "~/jenkins_biacm_deploy"
def APPLICATION_NAME="biacm"
def PROJECT_NAME="Accenture"
def SSH_USER=""
def SSH_HOST=""
def DEPLOYMENT_PATH=""
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
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'BIACM_SSH_USER_KEY_CRED')
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'NEX_SSH_USER_CRED')
        booleanParam(defaultValue: true, description: 'GIT using SSH not HTTP repo', name: 'GIT_SSH_NOT_HTTP')
    }

    stages {
        stage ('Extract Artifact') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Extracting the artifact from Nexus...'
                    echo '--------------------------------------------------------------------------------------------------------'
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
                            tar -xvzf ${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz
                        """
                    }
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Completed extraction.'
                    echo '--------------------------------------------------------------------------------------------------------'
                }
            }
        }
        stage ('CopyingArtifactToInstance') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Started copying the artefacts to the instance...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sshagent (credentials: [params.BIACM_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "Creating jenkins directory on BIACM instance"
 						    ssh ${SSH_USER}@${SSH_HOST} rm -rf ~/jenkins_biacm_deploy && mkdir ~/jenkins_biacm_deploy
                            echo "Copy is initiated"
                            cd ${ARTIFACT_PATH_JEN}
                            scp ${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz ${SSH_USER}@${SSH_HOST}:~/jenkins_biacm_deploy
                            echo "Copy is completed"
                            echo "extracting the files on BIACM instance"
                            ssh ${SSH_USER}@${SSH_HOST} tar -xvzf ~/jenkins_biacm_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz
                        """
                    } 
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Completed copying.'
                    echo '--------------------------------------------------------------------------------------------------------'
                } 
            }
        }
        stage ('Deplpoy') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Started deployment...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sshagent (credentials: [params.BIACM_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "Deploying...."
                            ssh "${SSH_USER}@${SSH_HOST}" "
                                rm -rf ${BACKUP_PATH}/Reporting/BIACM
                                cd ${DEPLOYMENT_PATH} && cp -pr ./Reporting/BIACM ${BACKUP_PATH}/Reporting/BIACM/
                                cp -r ~/jenkins_biacm_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}/Reporting/BIACM/* ${DEPLOYMENT_PATH}
                            "    
                        """  
                    }   
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy completed.'
                    echo '--------------------------------------------------------------------------------------------------------'
                }      
            }
        }
        stage ('cleanup') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Cleanup started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sshagent (credentials: [params.BIACM_SSH_USER_KEY_CRED]) {
                        sh """
                            rm -rf ${ARTIFACT_PATH_JEN}
                            ssh ${SSH_USER}@${SSH_HOST} rm -rf ~/jenkins_biacm_deploy
                        """
                    }
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Cleanup completed.'
                    echo '--------------------------------------------------------------------------------------------------------'    
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

