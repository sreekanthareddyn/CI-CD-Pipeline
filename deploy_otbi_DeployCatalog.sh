#!/bin/bash
echo "cd into deployment directory"
cd $2
for d in $(find $1/* -mindepth 0 -type d) do
    cd $d
    for file in *; do
        if [[ -f $file ]]; then
            if [[ $file == *.catalog ]]; then
                echo "$file"
                echo "Deployment of $file started"
                cd ${DEPLOY_SCRIPT_PATH}
                ./runcat.sh -cmd unarchive -inputFile ${file} -folder /shared -online #{node['oacs']['cat']['link']} -credentials /bi/domain/fmw/user_projects/domains/bi/bitools/bin/catmancredential.properties -overwrite all 2>&1 | tee -a #{node['oacs']['depl updateogs']}/deploycat_#{time}.log")
                echo "Deployment of $file completed"
            fi
        fi
    done
done
exit 0