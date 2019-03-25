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
                        cd ${ARTIFACT_PATH_JEN} && rm -rf *
                        wget ${NEXUS_URL}/${ARTIFACT_NAME} && tar -xvzf ${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}.tar.gz
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
                        cd ${ARTIFACT_PATH_JEN}/${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER} && mkdir deploy_oacs_shellscripts
                        echo 'moving shell scripts to artifact directory'
                        cd ${SHELL_SCRIPT_PATH} && mv deplopy_oacs*.sh ${ARTIFACT_PATH_JEN}/${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}/deploy_oacs_shellscripts
                        echo 'creating tarball with updated rpd and shell scripts'
                        cd ${ARTIFACT_PATH_JEN} && tar -zcf ${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}.tar.gz ./${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}
                    """
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Completed Packaging.'
                    echo '--------------------------------------------------------------------------------------------------------'
                }
            }
        }
        stage ('DirectoryCreation/CleanUp') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Checking if Directory exists, cleaning if exists else creating one'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "Checking for temp directory and deleting the dir if exists"   
                            ssh ${SSH_USER}@${SSH_HOST} cd /var/temp/ && ssh ${SSH_USER}@${SSH_HOST} rm -rf /var/temp
                            echo "Creating the temp directory and giving permissions"
                            ssh ${SSH_USER}@${SSH_HOST} mkdir /var/temp/ && ssh ${SSH_USER}@${SSH_HOST} chmod a+rx /var/temp/
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
                    sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "Copy is initiated"
                            scp -r ${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}.tar.gz ${SSH_USER}@${SSH_HOST}:/var/temp/
                            echo "Copy is completed"
                            echo "extracting the files on OACS instance and giving permissions to scripts"
                            ssh ${SSH_USER}@${SSH_HOST} cd /var/temp/ && ssh ${SSH_USER}@${SSH_HOST} tar -xvzf ${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}.tar.gz && ssh ${SSH_USER}@${SSH_HOST} sudo su - oracle chmod u+x /var/temp/${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}/deploy_oacs_shellscripts/*.sh
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
                            echo 'decrypting the sys env files and extracting the variables'
                            ssh ${SSH_USER}@${SSH_HOST} cd /u01/ && ssh ${SSH_USER}@${SSH_HOST} openssl enc -d -aes-256-cbc -in /u01/sys.env -out ./unec.props -pass <tbc> && ssh ${SSH_USER}@${SSH_HOST} rpd_password=$(awk -F = '$1=="rpd_password"{print $2;exit}' unec.props) && ssh ${SSH_USER}@${SSH_HOST} username=$(awk -F = '$1=="username"{print $2;exit}' unec.props) && ssh ${SSH_USER}@${SSH_HOST} password=$(awk -F = '$1=="password"{print $2;exit}' unec.props) && ssh ${SSH_USER}@${SSH_HOST} url=$(awk -F = '$1=="url"{print $2;exit}' unec.props)
                            echo 'updating the rpd file with parameters'
                            ssh ${SSH_USER}@${SSH_HOST} cd ${RPDFILE_PATH} && ssh ${SSH_USER}@${SSH_HOST} sed -e "s/username=\(.*\)/username=${username}/" \-e "s/password=\(.*\)/password=${password}/" \-e "s/url=\(.*\)/url=${url}/" ${RPD_FILE_NAME}.rpd > ${RPD_FILE_NAME}_parsed.rpd
                            echo 'removing the properties file'
                            ssh ${SSH_USER}@${SSH_HOST} rm /u01/unec.props
                        """
                    }
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'RPD file is updated.'
                    echo '--------------------------------------------------------------------------------------------------------'
                }
            }
        }        
        stage ('CreateSnapshot') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Snapshot creation started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //creating a snapshot
                    sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "Initiated create snapshot"
                            ssh ${SSH_USER}@${SSH_HOST} cd /var/temp/${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}/deploy_oacs_shellscripts && ssh ${SSH_USER}@${SSH_HOST} sudo su - oracle ./deploy_oacs_CreateSnapshot.sh          
                        """   
                    } 
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Snapshot creation completed.'
                    echo '--------------------------------------------------------------------------------------------------------'
                }       
            }
        }
        stage ('DeployRPD') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Update RPD started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //Updating the rpd file
                    sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                        sh """
                            echo 'updating rpd file with password'
                            ssh ${SSH_USER}@${SSH_HOST} cd /var/temp/${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}
                            ssh ${SSH_USER}@${SSH_HOST} sudo su - oracle /oacs/domain/home/bi/bitools/bin/nqudmlexec.sh -P ${rpd_password}} -B ${RPD_FILE_NAME}_parsed.rpd -O ${RPD_FILE_NAME}_parsed.rpd 2>&1 | tee -a ${DEPLOY_LOGS}/udml_$(date +"%T").log")
                            if [$? -ne 0]; then 
                                echo "Update password failed";
                                exit 2
                            fi
                            if grep -F "${pattern_deploy}" udml_$(date +"%T").log"; then 
                                echo "Post update check failed"
                                exit 2
                            else
                                echo "Post update check completed successfully!"
                            fi
                        """  
                    }  
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Update RPD completed.'
                    echo '--------------------------------------------------------------------------------------------------------'
                } 
            }
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy RPD started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //Deploying the rpd file
                    sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "moving files to deployment path"
                            ssh ${SSH_USER}@${SSH_HOST} cd /var/temp/ && ssh ${SSH_USER}@${SSH_HOST} mv * ${DEPLOYMENT_PATH}
                            echo "Deploying rpd file"
                            ssh ${SSH_USER}@${SSH_HOST} cd ${DEPLOYMENT_PATH}/${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}/deploy_oacs_shellscripts && ssh ${SSH_USER}@${SSH_HOST} sudo su - oracle ./deploy_oacs_DeployRpd.sh ${rpd_password} ${username} ${password} ${SERVICE_INSTANCE} ${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}
                        """   
                    }  
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy RPD completed.'
                    echo '--------------------------------------------------------------------------------------------------------'
                }             
            }
        }
        stage ('DeplpoyCatalog') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy catalog started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //Deploying the catalog file
                    sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                        sh """
                            echo "Deploying Catalog's"
                            ssh ${SSH_USER}@${SSH_HOST} cd ${DEPLOYMENT_PATH}/${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER}/deploy_oacs_shellscripts && ssh ${SSH_USER}@${SSH_HOST} sudo su - oracle ./deploy_oacs_DeployCatalog.sh ${PROJECT_NAME}-${APPLICATION_NAME}-1.0.${BUILD_NUMBER} ${DEPLOYMENT_PATH}                            
                        """  
                    }   
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy catalog completed.'
                    echo '--------------------------------------------------------------------------------------------------------'
                }      
            }
        }
        /*
        stage ('Cleaning') {
            //Removing the files in /var/temp after the deployment
            sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                sh """
                    echo "Removing files in temp directory post deployment"
                    ssh ${SSH_USER}@${SSH_HOST} cd /var
                    ssh ${SSH_USER}@${SSH_HOST} rm -rf temp
                """
            }
        }
        */
    }
    post {
        always {
            echo "Pipeline  completed - result: ${currentBuild.result}"
            sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                sh """
                    echo "Removing files in temp directory post deployment"
                    ssh ${SSH_USER}@${SSH_HOST} cd /var && ssh ${SSH_USER}@${SSH_HOST} rm -rf temp   
                """
            }
        }
        success {
            echo 'Pipeline Succeeded.'
        }
        failure {
            echo 'Pipeline Failed!'
        }
    }
}





