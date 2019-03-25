#!groovy

// 21-02-2019 18:15

//Variables declaration
def GIT_HTTP_URL = 'https://sreekantha.narravula@innersource.accenture.com/scm/xgox/mosri-repo-test.git'
def GIT_SSH_URL = ''
def GIT_REPO_PATH = 'scm/xgox/'
def GIT_REPO_NAME= 'mosri-repo-test'
def NEXUS_CRED_ID = 'nexusadmin'
def NEXUS_URL = '34.214.232.47:8081/nexus'
def ARTIFACT_VERSION = "1.0"
def ARTIFACT_DIR= "jenkins_csf_build"
def NEXUS_REPO = 'http://34.214.232.47:8081/nexus/content/repositories/test/csf'
def PROJECT_NAME='Accenture'
def PROJECT_GROUP = 'accenture.test'
def APPLICATION_NAME='csf'
def GIT_USER_NAME = '<git username>'
def GIT_USER_EMAIL = '<git email address>'

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
        stage ('QualityCheck') {
            steps {
                timestamps {
					echo 'code quality analysis for rpd using geritt'
				}
			}
        }
        stage ('CodeAnalysis'){
            steps {
                timestamps {
					echo 'Perform code vulnarability checks'
				}
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
echo 'moving dirs for csf to jenkins directory'
cp -r ./Integrations/CSF ./${ARTIFACT_DIR}/Integrations
echo 'moving rpd files for csf to jenkins directory'
echo 'creating csf tarball...'
tar -zcf ./${ARTIFACT_DIR}/${APPLICATION_NAME}-${ARTIFACT_VERSION}.${BUILD_NUMBER}.tar.gz ./${ARTIFACT_DIR}/Integrations/CSF
echo 'created csf build tarball'
					"""
					echo '-------------------------------------------------------------------------------------------------------'
					echo 'Completed packaging.'
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
