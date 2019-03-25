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
def APPLICATION_NAME="otbi"
def ARTIFACT_DIR= "jenkins_otbi_deploy"
def PROJECT_NAME="Accenture"
def SSH_USER=""
def SSH_HOST=""
def DEPLOYMENT_PATH=""
def BACKUP_PATH=""
def PATH="./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}"
def username=""
def password=""
def wsdl=""

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
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: [params.NEX_SSH_USER_CRED], 
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
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: [params.NEX_SSH_USER_CRED], 
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
        stage ('Deplpoy') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy catalog started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    //Deploying the catalog file
                    
                        sh """
                        echo "Deploying Catalog's"
for d in \$(find ${PATH}/* -mindepth 0 -type d); do
    cd \$d
    for file in *; do
        if [[ -f \$file ]]; then
            if [ \$file = *.xdo ] || [ \$file = *.xdm ] || [ \$file = *.xdz ]; then
                gzip \$file
                encodedText=`echo -n \$file.gz | base64`
                filename="\${file%.*}"
                ext="\${file##*.}"
                python3 ./repository/python/lib/SoapUtils_uploadObject.py -w ${wsdl} -d `pwd` -u ${username} -p ${password} -t ${encodedText} -f ${filename} -e ${ext}
                if [[ \$? -ne 0 ]]; then
                echo "soap request failed"
                exit 2;
                fi
            fi
        fi
    done
done
exit 0
                        """  
                    
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy catalog completed.'
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

