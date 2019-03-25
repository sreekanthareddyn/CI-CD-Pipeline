#!/bin/bash 

DEPLOY_SCRIPT_PATH="/bi/domain/fmw/user_projects/domains/bi/bitools/bin"
SNAPSHOT_PATH="/u01/app/oracle/tools/home/oracle"
DEPLOY_LOGS="/u01/app/oracle/tools/home/oracle"

pattern_snap="/Errors Detected/
/nothing committed/"
cd ${DEPLOY_SCRIPT_PATH}
echo "snapshot creation started"
./exportarchive.sh bootstrap ${SNAPSHOT_PATH} encryptionpassword="$1" 2>&1 | tee -a ${DEPLOY_LOGS}/createsnap_$(date +"%T").log
    if [$? -ne 0]; then 
        echo "Snaptshot creation failed";
        exit 2
    fi
    if grep -F "${pattern_snap}" createsnap_$(date +"%T").log; then 
        echo "Snap creation failed"
        exit 2
    else
        echo "Snapshot creation sucess!"
    fi
exit 0
