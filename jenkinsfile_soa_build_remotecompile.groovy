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
		string(defaultValue: 'to define', description: 'bb1118f7-1e92-4461-859a-31820f202e4c', name: 'SOA_SSH_USER_KEY_CRED')
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
        stage ('QualityCheck') {
            steps {
                timestamps {
					echo 'code quality analysis using geritt'
				}
			}
        }
        stage ('CodeAnalysis'){
            steps {
                timestamps {
					echo 'Perform code vulnarability checks'
            }
        }
        stage ('Build Packaging') {
            steps {
                timestamps {
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Starting packaging...'
					echo '-------------------------------------------------------------------------------------------------------'
					sh """#!/bin/bash +x
echo 'create a jenkins dir'
mkdir ./${ARTIFACT_DIR} && mkdir ./${ARTIFACT_DIR}/Integrations
echo 'moving dirs for SOA to jenkins directory'
cp -r ./Integrations/SOA ./${ARTIFACT_DIR}/Integrations
echo 'creating soa tarball...'
tar -zcf ./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}.tar.gz ./${ARTIFACT_DIR}/Integrations/SOA
echo 'created soa build tarball'	
					"""
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Completed packaging.'
					echo '-------------------------------------------------------------------------------------------------------'
				}
			}
        }

		stage ('COPYING PACKAGE TO INSTANCE') {
			steps {
				timestamps {
					echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Started copying the package to the instance...'
                    echo '--------------------------------------------------------------------------------------------------------'
                    sshagent (credentials: [params.SOA_SSH_USER_KEY_CRED]) {
						sh """#!/bin/bash +x
echo 'Creating remote build utility directory on environment SOA instance'
ssh ${SSH_USER}@${SSH_HOST} mkdir ~/jenkins_soa_build
cd ./${ARTIFACT_DIR}
echo 'Uploading package to remote build utility directory on environment SOA instance'
scp ${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}.tar.gz ${SSH_USER}@${SSH_HOST}:~/jenkins_soa_build
echo 'Uploading package completed'
echo 'Extracting remote package in remote build utility directory'
ssh ${SSH_USER}@${SSH_HOST} "tar -xvzf ~/jenkins_oacs_deploy/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}.tar.gz"
						"""
					}
					echo '-----------------------------------------------------------------------------------------------'
					echo 'Completed copying'
					echo '-----------------------------------------------------------------------------------------------'
				}
			}
		}
		
        stage ('Compile Stage') {
            steps {
				timestamps {
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Compilation Started....'
					echo '-------------------------------------------------------------------------------------------------------'
					
					sshagent (credentials: [params.SOA_SSH_USER_KEY_CRED]) {
						sh """#!/bin/bash +x
echo 'Compiling SOA build on remote environment SOA instance'
ssh ${SSH_USER}@${SSH_HOST} withAnt(installation: 'LocalAnt') {	sh "ant ~/jenkins_soa_build/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}/${BUILD_XML_PATH}/build.xml compile-package" }
echo 'Creating compiled package tarball'
ssh ${SSH_USER}@${SSH_HOST} "tar -zcf ~/jenkins_soa_build/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}.tar.gz ~/jenkins_soa_build/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}"
cd ./${ARTIFACT_DIR}
echo 'Removing local tarball on jenkins instance'
rm -f ${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}.tar.gz
echo 'Copying compiled package tarball to Jenkins instance'
scp . ${SSH_USER}@${SSH_HOST}:~/jenkins_soa_build/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}.tar.gz
						"""
					}
					echo '-----------------------------------------------------------------------------------------------'
					echo 'compiliation completed'
					echo '-----------------------------------------------------------------------------------------------'
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

		stage ('clean up'){
			steps {
				timestamps {
					echo '--------------------------------------------------------------------------------------------------------'
					echo 'Cleanup started...'
					echo '--------------------------------------------------------------------------------------------------------'
					sshagent (credentials: [params.SOA_SSH_USER_KEY_CRED]) {
						sh """
ssh ${SSH_USER}@${SSH_HOST} rm -rf ~/jenkins_soa_build 
						"""
					}
					echo '--------------------------------------------------------------------------------------------------------'
                    echo 'Cleanup completed.'
                    echo '--------------------------------------------------------------------------------------------------------'
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
