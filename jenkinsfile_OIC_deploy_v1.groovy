#!groovy

//variable declaration
def GIT_HTTP_URL = 'https://sreekantha.narravula@innersource.accenture.com/scm/xgox/mosri-repo-test.git'
def GIT_SSH_URL = ''
def GIT_REPO_PATH = 'scm/xgox'
def GIT_REPO_NAME= 'mosri-repo-test'
def ARTIFACT_NAME = "apache-maven-3.0.5-bin.tar.gz"
def NEXUS_URL = "http://www.us.apache.org/dist/maven/binaries"
def ARTIFACT_DIR= "jenkins_oic_deploy"
def SERVICE_INSTANCE = "abc"
def PROJECT_NAME="Accenture"
def APPLICATION_NAME="OIC"
def SSH_USER=""
def SSH_HOST=""
def DEPLOYMENT_PATH=""
def BUILD_XML_PATH = ""
def BACKUP_PATH=""
def status_code=""
def OIC_Username =""
def OIC_Password =""
def OIC_url =""
def IAR_CONN_PROP = ""
def f1
def f2
def IntegrationID
//def CreateIntegration = "/ic/api/integration/v1/integrations"      //post method, creates an integration
//def DeleteIntegration = "/ic/api/integration/v1/integrations/"      //delete method, delete the integration
//def RetrieveIntegration = "/ic/api/integration/v1/integrations/"    //Get method, retrieve the integration
def ImportAddIar = "/ic/api/integration/v1/integrations/archive"         //post method, importing an integration under a different username or version
def ImpprtAddLookup = "/icsapis/v2/lookups/archive"                      //post method, Importing a lookup under a different username or version
def UpdateConnection = "/icsapis/v2/connections/"
//def ImportReplace = "/ic/api/integration/v1/integrations/archive"     //put method, importing an integration with the same name 
def PATH="./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}/Integrations/OIC/"
def IntegrationName = "xxxx.iar"

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

        stage ('Updating Parameters') {

            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Updating Parameters...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sh """#!/bin/bash +x
    for d in \$(find ${PATH}/* -mindepth 0 -type d); do
    cd \$d
    dir=`echo "\$d" | sed 's!.*/!!'`
        if [ \$dir = Util ] || [[ \$dir = Service ]]; then          
            for file in *; do
                if [[ -f \$file ]]; then
                    if [[ \$file = \${IntegrationName} ]]; then
                        if [[ \$dir = Lookup ]]; then
                            for file in *; do
                                if [[ -f \$file ]]; then
                                    if [[ \$file = *.csv ]]; then
                                    while read f1 f2
                                    do 
                                        echo "$f1, $f2"
                                    done < env.prop >  \$file.csv               
                                    fi
                                fi
                            done
                        fi
                    fi
                fi
            done
        fi
    done
exit 0
                    """

                    echo '-------------------------------------------------------------------------------------------------------'
					echo 'Completed updating parameters.'
					echo '-------------------------------------------------------------------------------------------------------'

                }
            }
        }    

        stage ('DeployLookup') {

            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploying Lookup...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sh """#!/bin/bash +x
    for d in \$(find ${PATH}/* -mindepth 0 -type d); do
    cd \$d
    dir=`echo "\$d" | sed 's!.*/!!'`
        if [ \$dir = Util ] || [[ \$dir = Service ]]; then          
            for file in *; do
                if [[ -f \$file ]]; then
                    if [[ \$file = \${IntegrationName} ]]; then
                        if [[ \$dir = Lookup ]]; then
                            for file in *; do
                                if [[ -f \$file ]]; then
                                    if [[ \$file = *.csv ]]; then
                                        status_code=\$(curl -k -v -X POST -u ${OIC_Username}:${OIC_Password} -F file=@./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}/Integrations/OIC/Integration/\$dir/\$file -F type=application/octet-stream ${ICS_URL}/${ImpprtAddLookup})
                                        if [[ \$status_code != 200 ]]; then
                                            echo "Lookup deployment failed"
                                            exit 2;
                                        fi    
                                    fi
                                fi
                            done
                        fi
                    fi
                fi
            done
        fi
    done
exit 0
                    """

                    echo '-------------------------------------------------------------------------------------------------------'
					echo 'Completed deploying lookup.'
					echo '-------------------------------------------------------------------------------------------------------'
                }

            }
        }
        stage ('Deplopy IAR file') {

            steps {
                timestamps {
                    echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Deploying integration...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sh """#!/bin/bash +x
for d in \$(find ${PATH}/* -mindepth 0 -type d); do
cd \$d
dir=`echo "\$d" | sed 's!.*/!!'`
    if [ \$dir = Util ] || [[ \$dir = Service ]]; then          
        for file in *; do
            if [[ -f \$file ]]; then
                if [[ \$file = \${IntegrationName} ]]; then
                    IntegrationID="\${file%.*}"
                    echo "\${IntegrationID}"
                    status_code=\$(curl -u ${OIC_Username}:${OIC_Password}  -H "Accept: application/json" ${ICS_URL}/${ImportAddIar} -X POST -F "file=@./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}/Integrations/OIC/Integration/\$dir/\$file"; type=application/octet-stream)
                    if [[ \$status_code != 200 ]]; then
                        echo "Iar deployment failed"
                        exit 2;
                    fi
                    connection_status_code=\$(curl -X POST -u ${OIC_Username}:${OIC_Password} -H "X-HTTP-Method-Override:PATCH" -H "Content-Type:application/json" -d @./repository/${IAR_CONN_PROP} ${ICS_URL}/${UpdateConnection}/${IntegrationID})    
                    if [[ \$connection_status_code != 200 ]]; then
                        echo "Update connection details failed"
                        exit 2;
                    fi
                fi
            fi
        done
    fi
done
exit 0
"""
                    echo '-------------------------------------------------------------------------------------------------------'
					echo 'Completed deploying IAR file.'
					echo '-------------------------------------------------------------------------------------------------------'
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
    