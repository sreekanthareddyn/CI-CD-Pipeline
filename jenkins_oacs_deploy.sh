#!/usr/bin/bash
set -e
set -o pipefail


# Usage
usage() { echo "Usage: $0 [-h] -e <ENVIRONMENT> -f <FUNCTIONS> -p <PROPERTIES> -t <dmgr|solr|node> -n <NODES> " 1>&2; exit 1; }


# Logging
log() {
    level=${1}
    message=${2}
    if [ -z "${level}" ]; then echo "Level is empty"; exit 1; fi
    if [ -z "${message}" ]; then echo "Message is empty"; exit 1; fi

    timestamp=$(date '+%Y/%m/%d %H:%M:%S')
    echo "${timestamp}:${level} > ${message}"
}

# Validate Input Parameters
if (($# == 0)); then
  echo "No parameters found, exiting" >&2
  usage
  exit 1
fi


# Constants
wcs_automation_dir_name="wcs-automation-curl"
jenkins_tmp="/tmp/jenkins"
jenkins_remote_dir="${jenkins_tmp}/${wcs_automation_dir_name}"
TYPE_DMGR="dmgr"
TYPE_NODE="node"
TYPE_SOLR="solr"

# Use environment variables to specify which pieces of automation to run
# This is useful for Jenkins to allow build parameters to be setup to feed in to the script
# By placing it here it can still be overridden by the -f option, which is intentional
script_functions=()
if [ "${SEED_CURL}" == "true" ]; then script_functions+=("seed_curl"); fi

# Process Input Paramaters
while getopts "he:f:p:r:t:n:N:" opt; do
  case $opt in
    e)
      environment=${OPTARG}
      ;;
    f)
      script_functions=(${OPTARG})
      ;;
    p)
      environment_file=${OPTARG}
      ;;
	r)
      release_package=${OPTARG}
      ;;
    t)
      node_type=${OPTARG}
      case ${node_type} in
        ${TYPE_DMGR}|${TYPE_NODE}|${TYPE_SOLR})
          ;;
        *)
          echo "Invalid option: ${node_type}"
          usage
          ;;
      esac
      ;;
    n)
      nodes=(${OPTARG})
      ;;
    h)
      echo "Tip: Setting JENKINS_TEST to true will stop the script from executing anything against the remote environment. This is for testing purposes."
      echo "Tip: Environment variables can be used to trigger automation, however the -f option will override it"
      echo "Tip: This script expects IFS to be set to a space"
      usage
      ;;
    *)
      echo "Invalid parameter(s) or option(s)."
      usage
      ;;
  esac
done


# Check values exist
if [ -z "${environment}" ] || [ ${#script_functions[@]} -eq 0 ] || [ -z "${environment_file}" ] || [ -z "${node_type}" ] || [ ${#nodes[@]} -eq 0 ] ; then
    echo "Parameter value(s) missing"
    usage
fi

# Validate property file exists
log "INFO" "Checking if the property file exists or not..."
if [ ! -f "${environment_file}" ]; then
    log "ERROR" "Property file does not exist: ${environment_file}"
    exit 1
else
    log "INFO" "Property file exists: ${environment_file}"
fi

# Validate property file is formatted so it can be processed without fail
log "INFO" "Checking that the property file has a newline at the end - this is so that the last line is not ignored"
log "INFO" "Executing: tail -c 1 \"${environment_file}\""
property_file_last_character=$(tail -c 1 "${environment_file}")

if [ "${property_file_last_character}" != "" ]; then
    log "ERROR" "Property file does not end with a newline - please rectify and re-run: ${environment_file}"
    exit 1
else
    log "INFO" "Property file has correct line ending for the last line"
fi


script_function="Curl -L command execution"


start_epoch=$(date '+%s')
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"


# Read each line of the environment property file
line_matched="false"
while read line
do
    # Split the line into an array
    IFS=',' read -a property_array <<< "${line}"
    env=${property_array[0]}
    type=${property_array[1]}
    id=${property_array[2]}

    # Only run if the checkbox has been ticked, via the nodes array    	
	if [ "${environment}" == "${env}" ] && [ "${node_type}" == "${type}" ] && [[ " ${nodes[*]} " == *" ${id} "* ]]; then
        line_matched="true"

        log "INFO" "Environment: ${env}"
        log "INFO" "Type: ${type}"
        log "INFO" "Node: ${id}"
        log "INFO" "Functions: ${script_functions[*]}"

        SSH_USER=${property_array[3]}
        SSH_HOST=${property_array[4]}
        WAS_USER=${property_array[5]}
        WCS_INSTALL_DIR=${property_array[6]}
        WCS_INSTANCE=${property_array[7]}
#        WAS_CELL_NAME=${property_array[8]}

        log "INFO" "SSH User: ${SSH_USER}"
        log "INFO" "SSH Host: ${SSH_HOST}"
        log "INFO" "WAS User: ${WAS_USER}"
        log "INFO" "WCS Installation Directory: ${WCS_INSTALL_DIR}"
        log "INFO" "WCS Instance: ${WCS_INSTANCE}"
		
        echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"

        # Variables
        automation_dir="/home/${WAS_USER}/${wcs_automation_dir_name}"
		curl_script="curl-${env}-${nodes}.sh"
		wasmaint_dir="${WCS_INSTALL_DIR}/instances/${WCS_INSTANCE}/wasmaint"

        # Prepare remote host
        log "INFO" "Preparing remote host: ${SSH_HOST}"

        # Check that the Jenkins tmp directory exists
        log "INFO" "Setting up: ${jenkins_tmp}"
        log "INFO" "Executing: ssh -t -t -o StrictHostKeyChecking=no \"${SSH_USER}\"@\"${SSH_HOST}\" \"if [ ! -d \"${jenkins_tmp}\" ]; then echo \"${jenkins_tmp} does not exist, creating: mkdir \"${jenkins_tmp}\"\"; mkdir \"${jenkins_tmp}\"; fi; echo \"Reprotecting ${jenkins_tmp}: chmod a+rx \"${jenkins_tmp}\"\"; chmod a+rx \"${jenkins_tmp}\"\""
        # Only SSH to the host and run the script if we aren't in Jenkins test mode
        if [ "${JENKINS_TEST}" == "true" ]; then
            log "INFO" "JENKINS_TEST set to true - skipping automation execution"
        else
            ssh -t -t -o StrictHostKeyChecking=no "${SSH_USER}"@"${SSH_HOST}" "if [ ! -d \"${jenkins_tmp}\" ]; then echo \"${jenkins_tmp} does not exist, creating: mkdir \"${jenkins_tmp}\"\"; mkdir \"${jenkins_tmp}\"; fi; echo \"Reprotecting ${jenkins_tmp}: chmod a+rx \"${jenkins_tmp}\"\"; chmod a+rx \"${jenkins_tmp}\"" < /dev/null
        fi

        log "INFO" "Setting up: ${jenkins_remote_dir}"
        log "INFO" "Executing: ssh -t -t -o StrictHostKeyChecking=no \"${SSH_USER}\"@\"${SSH_HOST}\" \"if [ -d \"${jenkins_remote_dir}\" ]; then echo \"Found ${jenkins_remote_dir}, removing: rm -rf \"${jenkins_remote_dir}\"\"; rm -rf \"${jenkins_remote_dir}\"; fi; echo \"Creating ${jenkins_remote_dir}: mkdir \"${jenkins_remote_dir}\"; chmod a+rx \"${jenkins_remote_dir}\"\"; mkdir \"${jenkins_remote_dir}\"; chmod a+rx \"${jenkins_remote_dir}\"\""
        # Only SSH to the host and run the script if we aren't in Jenkins test mode
        if [ "${JENKINS_TEST}" == "true" ]; then
            log "INFO" "JENKINS_TEST set to true - skipping automation execution"
        else
            ssh -t -t -o StrictHostKeyChecking=no "${SSH_USER}"@"${SSH_HOST}" "if [ -d \"${jenkins_remote_dir}\" ]; then echo \"Found ${jenkins_remote_dir}, removing: rm -rf \"${jenkins_remote_dir}\"\"; rm -rf \"${jenkins_remote_dir}\"; fi; echo \"Creating ${jenkins_remote_dir}: mkdir \"${jenkins_remote_dir}\"; chmod a+rx \"${jenkins_remote_dir}\"\"; mkdir \"${jenkins_remote_dir}\"; chmod a+rx \"${jenkins_remote_dir}\"" < /dev/null
        fi
        

        # Transfer script to host
        log "INFO" "Transferring script to host: ${SSH_HOST}:${jenkins_remote_dir}"
        log "INFO" "Executing: scp -o StrictHostKeyChecking=no wcs-automation-curl.sh \"${SSH_USER}@${SSH_HOST}:${jenkins_remote_dir}\"/"
        # Only SSH to the host and run the script if we aren't in Jenkins test mode
        if [ "${JENKINS_TEST}" == "true" ]; then
            log "INFO" "JENKINS_TEST set to true - skipping automation execution"
        else
            scp -o StrictHostKeyChecking=no wcs-automation-curl.sh "${SSH_USER}@${SSH_HOST}:${jenkins_remote_dir}/"
        fi
		
		# Transfer Curl script to host
        log "INFO" "Transferring curl script to host: ${SSH_HOST}:${jenkins_remote_dir}"
        log "INFO" "Executing: scp -o StrictHostKeyChecking=no \"./curl/${curl_script}\" \"${SSH_USER}@${SSH_HOST}:${jenkins_remote_dir}\"/"
        # Only SSH to the host and run the script if we aren't in Jenkins test mode
        if [ "${JENKINS_TEST}" == "true" ]; then
            log "INFO" "JENKINS_TEST set to true - skipping automation execution"
        else
            scp -o StrictHostKeyChecking=no "./curl/${curl_script}" "${SSH_USER}@${SSH_HOST}:${jenkins_remote_dir}/"
        fi

        log "INFO" "Setting permissions on: ${jenkins_remote_dir}"
        log "INFO" "Executing: ssh -t -t -o StrictHostKeyChecking=no \"${SSH_USER}\"@\"${SSH_HOST}\" \"chmod -R a+rx ${jenkins_remote_dir}\""
        # Only SSH to the host and run the script if we aren't in Jenkins test mode
        if [ "${JENKINS_TEST}" == "true" ]; then
            log "INFO" "JENKINS_TEST set to true - skipping automation execution"
        else
            ssh -t -t -o StrictHostKeyChecking=no "${SSH_USER}"@"${SSH_HOST}" "chmod -R a+rx ${jenkins_remote_dir}" < /dev/null
        fi	
		
		# Transfer Curl script to wasmaint
		log "INFO" "Transferring curl script to wasmaint diectory: ${SSH_HOST}:${wasmaint_dir}"
		log "INFO" "Executing: ssh -t -t -o StrictHostKeyChecking=no \"${SSH_USER}"@"${SSH_HOST}\" \"sudo su - ${WAS_USER} -c '${jenkins_remote_dir}/${curl_script}\" \"${wasmaint_dir}'\""
        # Only SSH to the host and run the script if we aren't in Jenkins test mode
        if [ "${JENKINS_TEST}" == "true" ]; then
            log "INFO" "JENKINS_TEST set to true - skipping automation execution"
        else
			ssh -t -t -o StrictHostKeyChecking=no "${SSH_USER}"@"${SSH_HOST}" "sudo su - ${WAS_USER} -c 'cp ${jenkins_remote_dir}/${curl_script}" "${wasmaint_dir}'" < /dev/null
        fi

        log "INFO" "Setting up: ${automation_dir}"
        log "INFO" "Executing: ssh -t -t -o StrictHostKeyChecking=no \"${SSH_USER}\"@\"${SSH_HOST}\" \"sudo su - ${WAS_USER} -c 'if [ -d ${automation_dir} ]; then echo \"Found ${automation_dir}, removing: rm -rf ${automation_dir}\"; rm -rf ${automation_dir}; fi; echo \"Creating ${automation_dir}: mkdir ${automation_dir}\"; mkdir ${automation_dir}'\""
        # Only SSH to the host and run the script if we aren't in Jenkins test mode
        if [ "${JENKINS_TEST}" == "true" ]; then
            log "INFO" "JENKINS_TEST set to true - skipping automation execution"
        else
            ssh -t -t -o StrictHostKeyChecking=no "${SSH_USER}"@"${SSH_HOST}" "sudo su - ${WAS_USER} -c 'if [ -d ${automation_dir} ]; then echo \"Found ${automation_dir}, removing: rm -rf ${automation_dir}\"; rm -rf ${automation_dir}; fi; echo \"Creating ${automation_dir}: mkdir ${automation_dir}\"; mkdir ${automation_dir}'" < /dev/null
        fi

        log "INFO" "Setting up script for ${WAS_USER} in: ${automation_dir}"
        log "INFO" "Executing: ssh -t -t -o StrictHostKeyChecking=no \"${SSH_USER}\"@\"${SSH_HOST}\" \"sudo su - ${WAS_USER} -c 'cp -R ${jenkins_remote_dir}/* ${automation_dir}/; chmod u+x ${automation_dir}/*.sh'\""
        # Only SSH to the host and run the script if we aren't in Jenkins test mode
        if [ "${JENKINS_TEST}" == "true" ]; then
            log "INFO" "JENKINS_TEST set to true - skipping automation execution"
        else
            ssh -t -t -o StrictHostKeyChecking=no "${SSH_USER}"@"${SSH_HOST}" "sudo su - ${WAS_USER} -c 'cp -R ${jenkins_remote_dir}/* ${automation_dir}/; chmod u+x ${automation_dir}/*.sh'" < /dev/null
        fi
        echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"

        for script_function in "${script_functions[@]}"
        do
            log "INFO" "SSH onto host: ${SSH_HOST} running function: ${script_function} started."

            case ${type} in
                ${TYPE_DMGR})
                    log "INFO" "Executing: nothing as DMGR is not valid for curl seeding"
                    ;;
                ${TYPE_NODE})
                    log "INFO" "Executing: ssh -t -t -o StrictHostKeyChecking=no \"${SSH_USER}\"@\"${SSH_HOST}\" \"sudo su - ${WAS_USER} -c 'cd \"${jenkins_remote_dir}\"; ./wcs-automation-curl.sh -f \"${script_function}\" -t \"${type}\" -x \"${id}\" -d \"${WCS_INSTALL_DIR}\" -i \"${WCS_INSTANCE}\" -N \"${curl_script}\"'\""
                    # Only SSH to the host and run the script if we aren't in Jenkins test mode
                    if [ "${JENKINS_TEST}" == "true" ]; then
                        log "INFO" "JENKINS_TEST set to true - skipping automation execution"
                    else
                        ssh -t -t -o StrictHostKeyChecking=no "${SSH_USER}"@"${SSH_HOST}" "sudo su - ${WAS_USER} -c 'cd \"${automation_dir}\"; ./wcs-automation-curl.sh -f \"${script_function}\" -t \"${type}\" -x \"${id}\" -d \"${WCS_INSTALL_DIR}\" -i \"${WCS_INSTANCE}\" -N \"${curl_script}\" '" < /dev/null
                    fi
                    ;;
				${TYPE_SOLR})
                    log "INFO" "Executing: nothing as SOLR is not valid for curl seeding"
                    ;;
                *)
                    echo "Invalid option: ${node_type}"
                    usage
                ;;
            esac

            log "INFO" "SSH onto host: ${SSH_HOST} running function: ${script_function} is now complete."
            echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
        done
    fi
done < "${environment_file}"
end_epoch=$(date '+%s')
script_duration=$((end_epoch - start_epoch))
log "INFO" "Jenkins Wrapper elapsed script time (in seconds): ${script_duration}"
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"

if [ "${line_matched}" == "false" ]; then
    log "WARN" "The script did not match any lines within the properties file"
    exit 2
fi
