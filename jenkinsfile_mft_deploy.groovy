#!groovy

//variable declaration
def GIT_HTTP_URL = 'https://sreekantha.narravula@innersource.accenture.com/scm/xgox/mosri-repo-test.git'
def GIT_SSH_URL = ''
def GIT_REPO_PATH = 'scm/xgox'
def GIT_REPO_NAME= 'mosri-repo-test'
def ARTIFACT_NAME = "apache-maven-3.0.5-bin.tar.gz"
def NEXUS_URL = "http://www.us.apache.org/dist/maven/binaries"
def ARTIFACT_PATH_JEN = "~/mft_deploy"
def SERVICE_INSTANCE = "abc"
def PROJECT_NAME="Accenture"
def APPLICATION_NAME="mft"
def SSH_USER=""
def SSH_HOST=""
def DEPLOYMENT_PATH=""

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
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'MFT_SSH_USER_KEY_CRED')
        booleanParam(defaultValue: true, description: 'GIT using SSH not HTTP repo', name: 'GIT_SSH_NOT_HTTP')
    }

    stages {
         stage ('Extract Artifact') {
            steps {
                timestamps {
                    //Extracting the file from Nexus
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'XXXXXXXXXXXXXXXXXXXX', 
					usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                    sh """
                        echo ${USERNAME}
                        cd ${ARTIFACT_PATH_JEN} && rm -rf ${ARTIFACT_PATH_JEN}
                        mkdir ${ARTIFACT_PATH_JEN}
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
                    sshagent (credentials: [params.MFT_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "Creating tmp directory on mft instance
                            ssh ${SSH_USER}@${SSH_HOST} rm -rf /u01/jenkins_mft_deploy && mkdir /u01/jenkins_mft_deploy
                            cd ${ARTIFACT_PATH_JEN}
                            scp -r ${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz ${SSH_USER}@${SSH_HOST}:/u01/jenkins_mft_deploy
                            echo "Copy is completed"
                            echo "extracting the files on mft instance"
                            ssh ${SSH_USER}@${SSH_HOST} cd /u01/jenkins_mft_deploy && tar -xvzf ${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz
                            echo "listing the files now"
                            ssh ${SSH_USER}@${SSH_HOST} ls -l 
                        """
                    } 
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Completed copying.'
                    echo '--------------------------------------------------------------------------------------------------------'
                } 
            }
        }
        stage ('UpdateBuild') { 
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Updating the Build.xml...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //Updating MFT file
                    sshagent (credentials: [params.MFT_SSH_USER_KEY_CRED]) {
                        sh """
                            echo 'extracting parameters from env file'
                            ssh ${SSH_USER}@${SSH_HOST} cd /u01/
                            echo 'decrypting the sys env file'
                            ssh ${SSH_USER}@${SSH_HOST} openssl enc -d -aes-256-cbc -in /u01/sys.env -out ./unec.props -pass <tbc>
                            echo 'extracting username and password from env files'
                            ssh ${SSH_USER}@${SSH_HOST} rpd_password=\$(awk -F = '\$1=="rpd_password"{print \$2;exit}' unec.props)
                            ssh ${SSH_USER}@${SSH_HOST} username=$(awk -F = '\$1=="username"{print \$2;exit}' unec.props)
                            #echo $username
                            ssh ${SSH_USER}@${SSH_HOST} password=$(awk -F = '\$1=="password"{print \$2;exit}' unec.props)
                            #echo $password
                            ssh ${SSH_USER}@${SSH_HOST} url=$(awk -F = '\$1=="url"{print \$2;exit}' unec.props)
                            #echo $url
                            ssh ${SSH_USER}@${SSH_HOST} cd /u01/jenkins_mft_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}/mft/rpd/
                            echo 'updating rpd file with parameters'
                            ssh ${SSH_USER}@${SSH_HOST} sed -e "s/username=\(.*\)/username=${username}/" \-e "s/password=\(.*\)/password=${password}/" \-e "s/url=\(.*\)/url=${url}/" ${RPD_FILE_NAME}.rpd > ${RPD_FILE_NAME}_parsed.rpd
                        """
                    }
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'file is updated.'
                    echo '--------------------------------------------------------------------------------------------------------'
                }
            }
        }

        stage ('Deploy') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy mft started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sshagent (credentials: [params.mft_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "Deploying mft"
                            ssh ${SSH_USER}@${SSH_HOST} cd ${DEPLOYMENT_PATH} && mkdir backup && cp -pr ./integrations/mft/* ./backup
                            ssh ${SSH_USER}@${SSH_HOST} cd /u01/jenkins_mft_deploy && cp -r ${DEPLOYMENT_PATH}
                        """  
                    }  
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy mft completed.'
                    echo '--------------------------------------------------------------------------------------------------------'
                } 
            }
        }

        stage ('cleanup') {
            timestamps {
                steps {
                    sshagent (credentials: [params.MFT_SSH_USER_KEY_CRED]) {
                        sh """
                            rm -rf ${ARTIFACT_PATH_JEN}
                            ssh ${SSH_USER}@${SSH_HOST} rm -rf /u01/jenkins_mft_deploy
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




