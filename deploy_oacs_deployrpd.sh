#!/bin/bash

DEPLOY_SCRIPT_PATH="/bi/domain/fmw/user_projects/domains/bi/bitools/bin"
RPDFILE_PATH="$5"
RPD_FILE_NAME="OracleBIApps_BI0002_20181205_UAT"
DEPLOY_LOGS="~"

pattern_deploy="/Failed/
/aborting/
/Error/"
echo 'deploy updaterpd file with 4 parameters rpd_password, username, Passwprd and Instance details'
cd ${DEPLOY_SCRIPT_PATH}
/bi/domain/fmw/user_projects/domains/bi/bitools/bin/datamodel.sh uploadrpd -I ${RPDFILE_PATH} -W $1 -U $2 -P  $3 -SI $4 2>&1 | tee -a ${DEPLOY_LOGS}/deployrpd_$(date +"%T").log
    if [$? -ne 0]; then 
        echo "Deployment failed";
        exit 2
    fi
    if grep -F "${pattern_deploy}" deployrpd_$(date +"%T").log; then 
        echo "Post deplpoy check failed"
        exit 2
    else
        echo "Post depl updatek completed successfully!"
    fi
exit 0