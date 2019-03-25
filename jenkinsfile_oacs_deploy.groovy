#!groovy

//variable declaration
def GIT_HTTP_URL = 'https://sreekantha.narravula@innersource.accenture.com/scm/xgox/mosri-repo-test.git'
def GIT_SSH_URL = ''
def GIT_REPO_PATH = 'scm/xgox'
def GIT_REPO_NAME= 'mosri-repo-test'
def NEXUS_BASE_URL = 'XXX.XXX.XXX.XXX/nexus'
def NEXUS_URL = "${NEXUS_BASE_URL}/content/repositories/releases/homeoffice/metis/oacs"
def PROJECT_GROUP = 'homeoffice.metis'
def TYPE = 'tar.gz'
def ARTIFACT_DIR= "jenkins_oacs_deploy"
//def DEPLOY_SCRIPT_PATH = "/oacs/domain/home/bi/bitools/bin"
def SNAPSHOT_PATH = "/oacs/snap/home"
def DEPLOY_LOGS = ""
def APPLICATION_NAME="OACS"
def SERVICE_INSTANCE = ""
def RPD_FILE_NAME = "OracleBIApps_BI0002_20181205_UAT"
def PROJECT_NAME="Accenture"
def SSH_USER=""
def SSH_HOST=""
def DEPLOYMENT_PATH=""


//def SHELL_SCRIPT_PATH="repository/./${APPLICATION_NAME}/scripts"

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
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'NEX_SSH_USER_CRED')
        booleanParam(defaultValue: true, description: 'GIT using SSH not HTTP repo', name: 'GIT_SSH_NOT_HTTP')
        booleanParam(defaultValue: false, description: 'GIT using SSH not HTTP repo', name: 'NEX_MVN_NOT_LUCENE')
    }

    stages {
        stage ('Extract Artifact - MAVEN') {
            when {
                expression {
					return params.NEX_MVN_NOT_LUCENE
                }
            }
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Extracting the artifact from Nexus...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //Extracting the file from Nexus
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${NEX_SSH_USER_CRED}", 
					usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                        sh """#!/bin/bash +x
echo "Reading Nexus build package with user : ${USERNAME}"
mkdir ./${ARTIFACT_DIR}
cd ./${ARTIFACT_DIR}
wget -qO - -o /dev/null --http-user=\"${env.USERNAME}\" --http-password=\"${env.PASSWORD}\" \"${NEXUS_URL}/maven-metadata.xml\" | grep "<version>.*</version>" | sort | uniq | tail -n1 | sed -e \"s#\\(.*\\)\\(<version>\\)\\(.*\\)\\(</version>\\)\\(.*\\)#\3#g\" > artifact_version.txt
"""
script{
ARTIFACT_VERSION = readFile ("./${ARTIFACT_DIR}/artifact_version.txt").trim()
echo "Found latest Nexus build package : ${ARTIFACT_VERSION}"
}
sh """#!/bin/bash +x
cd ./${ARTIFACT_DIR}
wget -o /dev/null --http-user=\"${env.USERNAME}\" --http-password=\"${env.PASSWORD}\" \"${NEXUS_URL}/${ARTIFACT_VERSION}/${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz\"
tar -xvzf ${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz
cd ..
                        """
                    }
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Completed extraction.'
                    echo '--------------------------------------------------------------------------------------------------------'
                }
            }
        }
        stage ('Extract Artifact - LUCENE') {
            when {
                not {
					expression {
						return params.NEX_MVN_NOT_LUCENE
					}
				}
            }
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Extracting the artifact from Nexus...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //Extracting the file from Nexus
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${NEX_SSH_USER_CRED}", 
					usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                        sh """#!/bin/bash +x
echo "Reading Nexus build package with user : ${USERNAME}"
mkdir ./${ARTIFACT_DIR}
cd ./${ARTIFACT_DIR}
wget -qO - -o /dev/null --http-user=\"${env.USERNAME}\" --http-password=\"${env.PASSWORD}\" \"${NEXUS_BASE_URL}/service/local/lucene/search?g=${PROJECT_GROUP}&a=oacs&r=releases&p=tar.gz\" | grep -m 1 "<latestRelease>*" | sed -n \"s:.*<latestRelease>\\(.*\\)</latestRelease>.*:\\1:p\" > artifact_version.txt
"""
script{
ARTIFACT_VERSION = readFile("./${ARTIFACT_DIR}/artifact_version.txt").trim()
echo "Found latest Nexus build package : ${ARTIFACT_VERSION}"
}
sh """#!/bin/bash +x
cd ./${ARTIFACT_DIR}
wget -o /dev/null --http-user=\"${env.USERNAME}\" --http-password=\"${env.PASSWORD}\" \"${NEXUS_URL}/${ARTIFACT_VERSION}/${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz\"
tar -xvzf ${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz
cd ..
                        """
                    }
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Completed extraction.'
                    echo '--------------------------------------------------------------------------------------------------------'
                }
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
					echo 'Checking automation artefacts iout to local folder  : ${WORKSPACE}/repository'
					checkout([
						$class: 'GitSCM', 
						branches: [[name: 'master']], 
						userRemoteConfigs: [[credentialsId: 'params.GIT_HTTP_USER_CRED', url: "${GIT_HTTP_URL}"]],
						submoduleCfg: [], 
						doGenerateSubmoduleConfigurations: false, 
						extensions: [
						//[$class: 'CleanBeforeCheckout'],
                        [$class: 'RelativeTargetDirectory', relativeTargetDir: 'repository'], 
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
					echo 'Checking automation artefacts iout to local folder  : ${WORKSPACE}/repository'
					checkout([
						$class: 'GitSCM', 
						branches: [[name: 'master']], 
						userRemoteConfigs: [[credentialsId: 'params.GIT_SSH_USER_CRED', url: "${GIT_SSH_URL}"]],
						submoduleCfg: [], 
						doGenerateSubmoduleConfigurations: false, 
						extensions: [
						//[$class: 'CleanBeforeCheckout'],
                        [$class: 'RelativeTargetDirectory', relativeTargetDir: 'repository'], 
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
                    sh """#!/bin/bash +x
echo 'Creating workspace local directory for shell scripts'
mkdir ./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}/deploy_oacs_shellscripts
echo 'moving shell scripts to artifact directory'
cp ./repository/oacs/scripts/deploy_oacs*.sh ./repository/oacs/scripts/key.bin ./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}/deploy_oacs_shellscripts
chmod -R +x ${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}/deploy_oacs_shellscripts 
echo 'Creating environment upload package tarball'
tar -zcf ./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz ./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}
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
                        sh """#!/bin/bash +x
echo 'Creating remote deploy utility directory on environment OACS instance'
ssh ${SSH_USER}@${SSH_HOST} \"mkdir ~/jenkins_oacs_deploy\"
echo 'Uploading package to remote deploy utility directory on environment OACS instance'
cd ./${ARTIFACT_DIR}
scp ${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz ${SSH_USER}@${SSH_HOST}:~/jenkins_oacs_deploy
echo 'Uploading package completed'
echo 'Extracting remote package in remote deploy utility directory'
ssh ${SSH_USER}@${SSH_HOST} \"tar -xvzf ~/jenkins_oacs_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}.tar.gz\"
echo 'Move to OACS oracle user workspace'
ssh ${SSH_USER}@${SSH_HOST} sudo su root -c \\\"\"sudo rm -R /u01/app/oracle/tools/home/oracle/jenkins_oacs_deploy\"\\\"
ssh ${SSH_USER}@${SSH_HOST} sudo su root -c \\\"\"mv /home/opc/jenkins_oacs_deploy /u01/app/oracle/tools/home/oracle/\"\\\"
ssh ${SSH_USER}@${SSH_HOST} sudo su root -c \\\"\"chown -R oracle:oracle /u01/app/oracle/tools/home/oracle/jenkins_oacs_deploy\"\\\"
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
echo 'Extracting remote package in remote deploy utility directory'
echo 'decrypting the sys env file'
ssh ${SSH_USER}@${SSH_HOST} sudo su oracle -c \\\"\"openssl enc -d -aes-256-cbc -in /u01/sys.env -out /u01/unec.props -pass file:/u01/app/oracle/tools/home/oracle/jenkins_oacs_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}/deploy_oacs_shellscripts/key.bin\"\\\"
ssh ${SSH_USER}@${SSH_HOST} sudo su oracle -c \\\""\"'rpd_password=`sed -n 's/^rpd_password=//p' /u01/unec.props`'\" ; \
\"'echo \\\${rpd_password}'\" ; \
\"'/bi/domain/fmw/user_projects/domains/bi/bitools/bin/nqudmlexec.sh -P \\\${rpd_password} -I /u01/sit.udml -B /u01/app/oracle/tools/home/oracle/jenkins_oacs_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}/Reporting/OACS/${RPD_FILE_NAME}.rpd -O /u01/app/oracle/tools/home/oracle/jenkins_oacs_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}/Reporting/OACS/${RPD_FILE_NAME}_parsed.rpd 2>&1 | tee -a ${DEPLOY_LOGS}/udml_\$(date +"%T").log'\""\\\"
if [ \$? -ne 0 ] 
then 
	echo "Update password failed"
	exit 2; 
fi
if grep -e 'Failed' -e 'aborting' -e 'Error' ${DEPLOY_LOGS}/udml_\$(date +"%T").log
then 
	echo "Post update check failed"
	exit 2; 
else 
	echo "Post update check completed successfully"
fi
"
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
echo "Snapshot creation initiated..."
ssh ${SSH_USER}@${SSH_HOST} sudo su oracle -c \\\"" \"'snapshot_password=`sed -n 's/^snapshot_password=//p' /u01/unec.props`'\" ; \
\"'echo \\\${snapshot_password}'\" ; \
\"~/jenkins_oacs_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}/deploy_oacs_shellscripts/deploy_oacs_CreateSnapshot.sh 'welcome123' \""\\\"
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
                    echo 'Deploy RPD started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //Updating the rpd file
                    sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                        sh """
ssh ${SSH_USER}@${SSH_HOST} sudo su oracle -c \\\""  \"'login=`sed -n 's/^login=//p' /u01/unec.props`'\" ; \
\"'echo \\\${login}'\" ; \
\"'pwd=`sed -n 's/^pwd=//p' /u01/unec.props`'\" ; \
\"'echo \\\${pwd}'\" ; \
\"'rpd_password=`sed -n 's/^rpd_password=//p' /u01/unec.props`'\" ; \
\"'echo \\\${rpd_password}'\" ; \
\"'url=`sed -n 's/^url=//p' /u01/unec.props`'\" ; \
\"'echo \\\${url}'\"; \
\"'echo \\"Deploying rpd file\\"'\" ; \
\"'~/jenkins_oacs_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}/deploy_oacs_shellscripts/deploy_oacs_DeployRpd.sh \\\${rpd_password} \\\${login} \\\${pwd} ${SERVICE_INSTANCE} ~/jenkins_oacs_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}/Reporting/OACS/${RPD_FILE_NAME}_parsed.rpd'\" ; \
"\\\"                 
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
ssh ${SSH_USER}@${SSH_HOST} sudo su oracle -c \\\""    \"'url=`sed -n 's/^url=//p' /u01/unec.props`'\" ; \
\"'echo \\\${url}'\"; \
\"~/jenkins_oacs_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}/deploy_oacs_shellscripts/deploy_oacs_DeployCatalog.sh /u01/app/oracle/tools/home/oracle/jenkins_oacs_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}/Reporting/OACS/ ${SERVICE_INSTANCE_URL}\"  "\\\" 
                        """  
                    }   
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy catalog completed.'
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
                    sshagent (credentials: [params.OACS_SSH_USER_KEY_CRED]) {
                        sh """
ssh ${SSH_USER}@${SSH_HOST} sudo su oracle -c \\\""	\"'rm -rf ~/jenkins_oacs_deploy'\"  "\\\"
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




