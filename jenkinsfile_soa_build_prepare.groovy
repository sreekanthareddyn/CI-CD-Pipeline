#!groovy
//20-02-2019 13:07

//Variables declaration
def GIT_HTTP_URL = 'https://sreekantha.narravula@innersource.accenture.com/scm/xgox/mosri-repo-test.git'
def GIT_SSH_URL = ''
def GIT_REPO_PATH = 'scm/xgox/'
def GIT_REPO_NAME= 'mosri-repo-test'
def NEXUS_CRED_ID = 'nexusadmin'
def NEXUS_URL = '34.214.232.47:8081/nexus'
def ARTIFACT_VERSION = "1.0"
def ARTIFACT_DIR = "jenkins_soa_build"
def NEXUS_REPO = 'http://34.214.232.47:8081/nexus/content/repositories/test/soa'
def PROJECT_NAME='Accenture'
def PROJECT_GROUP = 'accenture.test'
def PROJECT_RELEASE='2'
def APPLICATION_NAME='soa'
def GIT_USER_NAME = '<git username>'
def GIT_USER_EMAIL = '<git email address>'
def SSH_USER = ""
def SSH_HOST = ""
def BUILD_XML_PATH = ""
def SOA_PROJECT="HOUtilsApp"
def compositepath="./${ARTIFACT_DIR}/Integrations/SOA/${SOA_PROJECT}"

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
        string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'GIT_SSH_USER_KEY_CRED')
        booleanParam(defaultValue: true, description: 'GIT using SSH not HTTP repo', name: 'GIT_SSH_NOT_HTTP')
    }

    stages {
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
		stage ('Prepare') {
			steps {
				timestamps {
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Reading properties from SOAbuild properties...'
					echo '-------------------------------------------------------------------------------------------------------'
					sh """#!/bin/bash +x
					echo "Creating dir in jenkins"
					mkdir ./${ARTIFACT_DIR}
					cp -p ./repository/build.xml SOAbuild.properties ./${ARTIFACT_DIR}
					"""
					script {
						properties = readProperties file: "./${ARTIFACT_DIR}/SOAbuild.properties"
						echo "${properties.composite.workspace}"
					}
				}
			}
		}


        stage ('QualityCheck') {
            steps {
                timestamps {
					echo 'code quality analysis using geritt'
				}
			}
        }
        stage ('SonarQube Analysis'){
            steps {
                timestamps {
					withSonarQubeEnv ('My SonarQube Server') {
						


						echo 'Perform code vulnarability checks'
					}
				}
			}
        }
		stage ('Quality Gate') { 
			steps {
				timestamps {	
					timeout(time: 1, unit: 'HOURS') { 
						def qg = waitForQualityGate() 
						if (qg.status != 'OK') {
							error "Pipeline aborted due to quality gate failure: ${qg.status}"
						}
					}
				}
			}
    	}
        stage ('Compile') {
            steps {
                timestamps {
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Starting Compile...'
					echo '-------------------------------------------------------------------------------------------------------'
					sh """#!/bin/bash +x
echo 'creating integrations dir'
mkdir ./${ARTIFACT_DIR}/Integrations
echo 'moving dirs for SOA to jenkins directory'
cp -r ./Integrations/SOA ./${ARTIFACT_DIR}/Integrations

echo "cd into deployment directory"
cd ./${ARTIFACT_DIR}/Integrations/SOA
ls -ltr
for d in \$(find ${compositepath}/* -mindepth 0 -type d); do
    cd \$d
    for file in *; do
        if [[ -f \$file ]]; then
            if [[ \$file == composite.xml ]]; then
                pwd
				 "Need to update the ant path"
				chmod +x ./${ARTIFACT_DIR}/Integrations/SOA/bin/ant
				./${ARTIFACT_DIR}/Integrations/SOA/bin/ant -f ./${ARTIFACT_DIR}/build.xml compile-package 
				Need to check with Fenton on config plan which should be copied to deploy dir 
		echo "\$file"
            fi
        fi
    done
done
exit 0
					"""
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Compilation completed.'
					echo '-------------------------------------------------------------------------------------------------------'
				}
			}
        }
		stage ('Packaging') {
			steps {
				timestamps {
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Starting Packaging...'
					echo '-------------------------------------------------------------------------------------------------------'
					sh """#!/bin/bash +x
					mkdir ./${ARTIFACT_DIR}/Integrations/SOA/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}
					#cd ./${ARTIFACT_DIR}/Integrations/SOA
					for d in \$(find ${compositepath}/* -mindepth 0 -type d); do
  						dir=`echo "\$d" | sed 's!.*/!!'`
     					if [[ \$dir  == deploy ]]; then
							echo "into deployment directory"
							cd \$d
							for file in *; do
          						if [[ -f \$file ]]; then
             						if [[ \$file == *.jar ]]; then
										echo "\$file"
										cd ..
										composite_dir=`echo "\$PWD" | sed 's!.*/!!'`
										echo \$composite_dir
										jar_size=`wc -c "\$file" | awk '{print \$1}'`
										config_size=`wc -c *plan | awk '{print \$1}'`
										echo \$jar_size
										echo \$config_size
 										if [[ \$jar_size == 0 || \$config_size == 0 ]]; then
    										echo "file size is zero"
    										exit 1
										fi
										rm -rf ./${ARTIFACT_DIR}/Integrations/SOA/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}/${composite_dir} && mkdir ./${ARTIFACT_DIR}/Integrations/SOA/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}/${composite_dir}
										cp \$d/\$file *plan ./${ARTIFACT_DIR}/Integrations/SOA/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}/${composite_dir}
             						fi
          						fi
        					done
     					fi
					done
					exit 0
					tar -zcf ./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}.tar.gz ./${ARTIFACT_DIR}/Integrations/SOA/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}
					"""
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Packaging completed.'
					echo '-------------------------------------------------------------------------------------------------------'
				}
			}
		}
        
        stage ('Build Publishing') {
            steps {
                timestamps {
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Starting publish to nexus...'
					echo '-------------------------------------------------------------------------------------------------------'
					nexusArtifactUploader(
						nexusVersion: 'nexus2', 
						protocol: 'http', 
						nexusUrl: "${NEXUS_URL}", 
						repository: "${NEXUS_REPO}", 
						credentialsId: "${NEXUS_CRED_ID}", 
						groupId: "${PROJECT_GROUP}", 
						version: "${ARTIFACT_VERSION}.${BUILD_NUMBER}",
						artifacts: [
							[artifactId: "${APPLICATION_NAME}",
							type: 'tar.gz',
							file: "./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}.tar.gz"]
						]
					)
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Completed publish to nexus.'
					echo '-------------------------------------------------------------------------------------------------------'
				}
			}
        }
		
        stage('SCM Post Build Tag') {
            steps {
                timestamps {
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Starting Post Build TAG...'
					echo '-------------------------------------------------------------------------------------------------------'
                    sshagent (credentials: [params.GIT_SSH_USER_KEY_CRED]) {
                        sh "git config user.email ${GIT_USER_EMAIL}"
                        sh "git config user.name '${GIT_USER_NAME}'"
                        sh "git tag -a TAG-${BUILD_TAG} -m '${PROJECT_NAME}-${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER} Post Build TAG ${BUILD_TAG}'"
						sh "git push ${GIT_SSH_URL} --tags"                     }
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Completed Post Build TAG.'
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
