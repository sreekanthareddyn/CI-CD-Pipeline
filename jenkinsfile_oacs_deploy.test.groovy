#!groovy

//variable declaration
def GIT_HTTP_URL = 'https://sreekantha.narravula@innersource.accenture.com/scm/xgox/mosri-repo-test.git'
def GIT_SSH_URL = ''
def GIT_REPO_PATH = 'scm/xgox'
def GIT_REPO_NAME= 'mosri-repo-test'
def ARTIFACT_NAME = "apache-maven-3.0.5-bin.tar.gz"
def NEXUS_URL = "http://www.us.apache.org/dist/maven/binaries"
def ARTIFACT_PATH_JEN = "./"
def DEPLOY_SCRIPT_PATH = "/oacs/domain/home/bi/bitools/bin"
def SNAPSHOT_PATH = "/oacs/snap/home"
def DEPLOY_LOGS = "/oacs/depl update"
def RPDFILE_PATH = "${ARTIFACT_PATH_JEN}/${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}/oacs/rpd"
def SERVICE_INSTANCE = "abc"
def RPD_FILE_NAME = "oacs"
def PROJECT_NAME="Accenture"
def APPLICATION_NAME="OACS"
//def SSH_USER=""
//def SSH_HOST=""
def DEPLOYMENT_PATH=""
def SHELL_SCRIPT_PATH="${ARTIFACT_PATH_JEN}/scripts/"

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
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'OACS_SSH_USER_KEY_CRED')
        booleanParam(defaultValue: true, description: 'GIT using SSH not HTTP repo', name: 'GIT_SSH_NOT_HTTP')
    }

    stages {
        stage ('Extract Artifact') {
            steps {
                timestamps {
                    //Extracting the file from Nexus
                    sh """
                        cd ${ARTIFACT_PATH_JEN}
                        rm -rf *
                        wget ${NEXUS_URL}/${ARTIFACT_NAME}
                        tar -xvzf ${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}.tar.gz
                    """
                }
            }
        stage ('Git Checkout - HTTP') {
			when {
                not {
					expression {
						return params.GIT_SSH_NOT_HTTP
					}
				}
            }
            steps {
                timestamps {
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Starting Git HTTP checkout...'
					echo '-------------------------------------------------------------------------------------------------------'
					checkout([
						$class: 'GitSCM', 
						branches: [[name: 'master']], 
						userRemoteConfigs: [[credentialsId: 'params.GIT_HTTP_USER_CRED', url: "${GIT_HTTP_URL}"]],
						submoduleCfg: [], 
						doGenerateSubmoduleConfigurations: false, 
						extensions: [
						[$class: 'CleanBeforeCheckout'],
						[$class: 'CloneOption', 
							depth: 1, 
							noTags: false,
							reference: '',
							shallow: true]
						]
					])
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Completed Git HTTP checkout.'
					echo '-------------------------------------------------------------------------------------------------------'
				}
			}
        }
        stage ('Git Checkout - SSH') {
			when {
                expression {
					return params.GIT_SSH_NOT_HTTP
                }
            }
            steps {
                timestamps {
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Starting Git SSH checkout...'
					echo '-------------------------------------------------------------------------------------------------------'
					checkout([
						$class: 'GitSCM', 
						branches: [[name: 'master']], 
						userRemoteConfigs: [[credentialsId: 'params.GIT_SSH_USER_CRED', url: "${GIT_SSH_URL}"]],
						submoduleCfg: [], 
						doGenerateSubmoduleConfigurations: false, 
						extensions: [
						[$class: 'CleanBeforeCheckout'],
						[$class: 'CloneOption', 
							depth: 1, 
							noTags: false,
							reference: '',
							shallow: true]
						]
					])
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Completed Git SSH checkout.'
					echo '-------------------------------------------------------------------------------------------------------'
				}
			}
        }

        stage ('Packaging') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Starting Packaging...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sh """
                        cd ${SHELL_SCRIPT_PATH}
                        echo 'moving shell scripts to artifact directory'
                        mv *.sh ${ARTIFACT_PATH_JEN}/${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}
                        cd ${ARTIFACT_PATH_JEN}
                        echo 'creating tarball with updated rpd and shell scripts'
                        tar -zcf ${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}.tar.gz ./${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}
                    """
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Completed Packaging.'
                    echo '--------------------------------------------------------------------------------------------------------'
                }
            }
        }
        stage ('CopyingArtifactToInstance') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Started copying the artifacts to the instance...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "Creating tmp directory on OACS instance"
                            if ssh ${SSH_USER}@${SSH_HOST} '[ -d /var/temp/]'; then   
                            echo "temp directory exists, removing the temp directory"
                            ssh ${SSH_USER}@${SSH_HOST} rm -rf /var/temp
                            else
                            ssh ${SSH_USER}@${SSH_HOST} mkdir /var/temp/
                            fi
                            echo "Giving permissions to tmp directory"
                            ssh ${SSH_USER}@${SSH_HOST} chmod a+rx /var/temp/
                            echo "Copy is initiated"
                            scp -r ${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}.tar.gz ${SSH_USER}@${SSH_HOST}:/var/temp/
                            echo "Copy is completed"
                            ssh ${SSH_USER}@${SSH_HOST} cd /var/temp/
                            echo "extracting the files on OACS instance"
                            ssh ${SSH_USER}@${SSH_HOST} tar -xvzf ${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}.tar.gz
                            echo "Giving executable permissions to shell scripts"
                            ssh ${SSH_USER}@${SSH_HOST} sudo su - oracle chmod u+x /var/temp/${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}/*.sh
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
        stage ('UpdateRPD') { 
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Updating the RPD file...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //Updating RPD file
                    sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                        sh """
                            echo 'extracting parameters from env file'
                            ssh ${SSH_USER}@${SSH_HOST} cd /var/temp/${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}
                            echo 'extracting username and password from env files'
                            ssh ${SSH_USER}@${SSH_HOST} rpd_password=$(awk -F = '$1=="rpd_password"{print $2;exit}' sys.env)
                            ssh ${SSH_USER}@${SSH_HOST} username=$(awk -F = '$1=="username"{print $2;exit}' sys.env)
                            #echo $username
                            ssh ${SSH_USER}@${SSH_HOST} password=$(awk -F = '$1=="password"{print $2;exit}' sys.env)
                            #echo $password
                            ssh ${SSH_USER}@${SSH_HOST} url=$(awk -F = '$1=="url"{print $2;exit}' sys.env)
                            #echo $url
                            ssh ${SSH_USER}@${SSH_HOST} cd ${RPDFILE_PATH}
                            echo 'updating rpd file with parameters'
                            ssh ${SSH_USER}@${SSH_HOST} sed -e "s/username=\(.*\)/username=${username}/" \-e "s/password=\(.*\)/password=${password}/" \-e "s/url=\(.*\)/url=${url}/" ${RPD_FILE_NAME}.rpd > ${RPD_FILE_NAME}_parsed.rpd
                            ssh ${SSH_USER}@${SSH_HOST} pwd
                        """
                    }
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'RPD file is updated.'
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
