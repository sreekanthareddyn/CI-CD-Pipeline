#!groovy

//variable declaration
def GIT_HTTP_URL = 'https://sreekantha.narravula@innersource.accenture.com/scm/xgox/mosri-repo-test.git'
def GIT_SSH_URL = ''
def GIT_REPO_PATH = 'scm/xgox'
def GIT_REPO_NAME= 'mosri-repo-test'
def ARTIFACT_NAME = "apache-maven-3.0.5-bin.tar.gz"
def NEXUS_URL = "http://www.us.apache.org/dist/maven/binaries"
def ARTIFACT_PATH_JEN = "./jenkins_soa_deploy"
def SERVICE_INSTANCE = "abc"
def PROJECT_NAME="Accenture"
def APPLICATION_NAME="soa"
def SSH_USER=""
def SSH_HOST=""
def DEPLOYMENT_PATH=""
def BUILD_XML_PATH = ""
def BACKUP_PATH=""
def compositepath="./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}"
def SOA_PROJECT="HOUtilsApp"
def SOA_COMPOSITE_PATH="Integrations/SOA/${SOA_PROJECT}"

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
						return return params.NEX_MVN_NOT_LUCENE
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
        stage ('prepare') {
            steps{
                timestamps {
                    echo '-------------------------------------------------------------------------------------------------------'
					echo 'Reading properties from SOAbuild properties...'
					echo '-------------------------------------------------------------------------------------------------------'
					sh """#!/bin/bash +x
					echo "Creating dir in jenkins"
					cp -p ./repository/build.xml SOAbuild.properties ./${ARTIFACT_DIR}
					"""
                    script {
						properties = readProperties file: "./${ARTIFACT_DIR}/SOAbuild.properties"
						echo "${properties.composite.workspace}"
                    }
                }
            }
        }
        stage ('Setting Env') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Setting SOA Env started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                        sh """#!/bin/bash +x
cd ./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}
for d in \$(find ${compositepath}/* -maxdepth 0 -type d); do
    dir=`echo "\$d" | sed 's!.*/!!'`
    #echo "dir=\$dir"
    #echo "d=\$d"
    cd \$d
    for file in *; do
        if [[ -f \$file ]]; then
            if [[ \$file == *.jar ]]; then
                #cp *.jar /Users/sreekantha.narravula/Desktop/Project/POC/deploy/$dir/jar
                cp *.jar ./Integrations/SOA/${SOA_PROJECT}/\$dir/deploy
            fi
            if [[ \$file == *config* ]]; then
                #cp *config* /Users/sreekantha.narravula/Desktop/Project/POC/deploy/$dir/config
                cp *config* ./Integrations/SOA/${SOA_PROJECT}/\$dir/....(Need to add config path)
            fi
        fi
    done
done
exit 0
                        """  
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'SOA Env SetUp completed.'
                    echo '--------------------------------------------------------------------------------------------------------'
                } 
            }
        }
        stage ('Deploy') {
            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploy started...'
                    echo '--------------------------------------------------------------------------------------------------------'
                        sh """#!/bin/bash +x
echo "Deploying SOA"
for d in \$(find ${SOA_COMPOSITE_PATH}/* -mindepth 0 -type d); do
    cd \$d
    for file in *; do
        if [[ -f \$file ]]; then
            if [[ \$file == composite.xml ]]; then
                pwd
                "Need to update the ant path"
				chmod +x ./${ARTIFACT_DIR}/Integrations/SOA/bin/ant
				./${ARTIFACT_DIR}/Integrations/SOA/bin/ant -f ./${ARTIFACT_DIR}/build.xml deploy
               
		echo "\$file"
            fi
        fi
    done
done
exit 0
            """
            echo '--------------------------------------------------------------------------------------------------------'
            echo 'Deploy completed.'
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




