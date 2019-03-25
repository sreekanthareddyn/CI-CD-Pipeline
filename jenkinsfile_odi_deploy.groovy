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
def ARTIFACT_DIR= "jenkins_odi_deploy"
def DEPLOY_SCRIPT_PATH = "/oacs/domain/home/bi/bitools/bin"
def SNAPSHOT_PATH = "/oacs/snap/home"
def DEPLOY_LOGS = ""
def APPLICATION_NAME="ODI"
def SERVICE_INSTANCE = ""
def PROJECT_NAME="Accenture"
def SSH_USER_APP=""
def SSH_HOST_APP=""
def SSH_USER_DB=""
def SSH_HOST_DB=""
def DEPLOYMENT_PATH=""
def PATH="./${ARTIFACT_DIR}"
def f1
def ORACLE_HOME=""


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
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'ODI_SSH_APP_USER_KEY_CRED')
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'ODI_SSH_DB_USER_KEY_CRED')
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
mkdir ./deploy_db
mkdir ./deploy_app

mv_file()
{
    f1=\$1
    if [[ \$f1 = *.xml ]]; then
        mv \$f1 ./deploy_app
    elif [[ \$f1 = *.sql ]]; then
        mv \$f1 ./deploy_db
    fi
}

for d in \$(find ${PATH}/* -mindepth 0 -type d); do
    cd \$d
    ls -l manifest.txt
    if [[ $? == 0 ]]; then
        for file in *; do
            if [[ -f \$file ]]; then
                if [[ \$file = manifest.txt ]]; then
                    while read f1
                    do 
                        mv_file \$f1
                    done < manifest.txt               
                fi
            fi
        done
    else
        for file in *; do
            if [[ -f \$file ]]; then
                mv_file \$file
            fi
        done
    fi
                    
done
exit 0
mv ./deploy_db ./deploy_app ./${ARTIFACT_DIR}
cp ./repository/env.xml ./${ARTIFACT_DIR}/deploy_app
cp ./repository/db.prop ./repository/key.bin ./${ARTIFACT_DIR}/deploy_db
tar -zcf ./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}-DB.tar.gz ./${ARTIFACT_DIR}/deploy_db
tar -zcf ./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}-APP.tar.gz ./${ARTIFACT_DIR}/deploy_app
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
                    echo "APP artifact copy"
                    sshagent (credentials: [params.ODI_SSH_APP_USER_KEY_CRED]) {
                        sh """#!/bin/bash +x
echo 'Creating remote deploy utility directory on environment ODI instance'
ssh ${SSH_USER_APP}@${SSH_HOST_APP} \"mkdir ~/jenkins_odi_deploy\"
echo 'Uploading package to remote deploy utility directory on environment ODI instance'
cd ./${ARTIFACT_DIR}
scp ${APPLICATION_NAME}-${ARTIFACT_VERSION}-APP.tar.gz ${SSH_USER_APP}@${SSH_HOST_APP}:~/jenkins_odi_deploy
echo 'Uploading package completed'
echo 'Extracting remote package in remote deploy utility directory'
ssh ${SSH_USER_APP}@${SSH_HOST_APP} \"tar -xvzf ~/jenkins_odi_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}-APP.tar.gz\"
                        """
                    } 

                    sshagent (credentials: [params.ODI_SSH_DB_USER_KEY_CRED]) {
                        sh """#!/bin/bash +x
echo 'Creating remote deploy utility directory on environment ODI instance'
ssh ${SSH_USER_DB}@${SSH_HOST_DB} \"mkdir ~/jenkins_odi_deploy\"
echo 'Uploading package to remote deploy utility directory on environment ODI instance'
cd ./${ARTIFACT_DIR}
scp ${APPLICATION_NAME}-${ARTIFACT_VERSION}-DB.tar.gz ${SSH_USER_DB}@${SSH_HOST_DB}:~/jenkins_odi_deploy
echo 'Uploading package completed'
echo 'Extracting remote package in remote deploy utility directory'
ssh ${SSH_USER_DB}@${SSH_HOST_DB} \"tar -xvzf ~/jenkins_odi_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}-DB.tar.gz\"
                        """
                    } 


                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Completed copying.'
                    echo '--------------------------------------------------------------------------------------------------------'
                } 
            }
        }
      

        stage ('DeployAPP') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy APP started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //Updating the rpd file
                    sshagent (credentials: [params.ODI_SSH_APP_USER_KEY_CRED]) {
                        sh """
ssh ${SSH_USER_APP}@${SSH_HOST_APP} "
echo "moving files to deployment path"
mv ~/jenkins_odi_deploy/deploy_app/* ${DEPLOYMENT_PATH}/
"                    
                        """  
                    }  
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy APP completed.'
                    echo '--------------------------------------------------------------------------------------------------------'
                } 
            }
        }

        stage ('DeployDB') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy DB started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //Updating the rpd file
                    sshagent (credentials: [params.ODI_SSH_DB_USER_KEY_CRED]) {
                        sh """
ssh ${SSH_USER_DB}@${SSH_HOST_DB} "
\"openssl enc -d -aes-256-cbc -in ~/jenkins_odi_deploy/deploy_db/db.prop -out ~/jenkins_odi_deploy/deploy_db/db.prop -pass file:~/jenkins_odi_deploy/deploy_db/key.bin\"
\"'username=`sed -n 's/^username=//p' ~/jenkins_odi_deploy/deploy_db/db.prop`'\" ; \"'echo \${username}'\" ; \"'password=`sed -n 's/^password=//p' ~/jenkins_odi_deploy/deploy_db/db.prop`'\" ; \"'echo \${password}'\" ; \"                    
for d in \$(find ~/jenkins_odi_deploy/* -mindepth 0 -type d); do
    cd \$d
    for file in *; do
        if [[ -f \$file ]]; then
            if [[ \$file = *.sql ]]; then
               ${ORACLE_HOME}/bin/sqlplus -s ${username}/${password} \$file
               if [[ \$? != 0 ]]; then
               exit 2
               fi
            fi
        fi
    done
        
done
exit 0                        
"                                        
                        """  
                    }  
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy DB completed.'
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
                    sshagent (credentials: [params.ODI_SSH_APP_USER_KEY_CRED]) {
                        sh """
ssh ${SSH_USER_APP}@${SSH_HOST_APP} \"rm -rf ~/jenkins_odi_deploy\"
                        """
                    }

                    sshagent (credentials: [params.ODI_SSH_DB_USER_KEY_CRED]) {
                        sh """
ssh ${SSH_USER_DB}@${SSH_HOST_DB} \"rm -rf ~/jenkins_odi_deploy\"
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




