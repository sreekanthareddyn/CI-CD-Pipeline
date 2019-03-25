#!/bin/bash
echo "cd into deployment directory"
IFS=$'\n'
LinuxDir="$1"
for d in $(find $1* -mindepth 0 -type d); do
ApplicationDir="${d:${#LinuxDir}:$((${#d}-${#LinuxDir}))}"
echo "The Application Directory is $ApplicationDir"
    cd $d
    echo "path is $d"
    for file in *; do
        if [[ -f $file ]]; then
            if [[ $file == *.catalog ]]; then
                echo "$file"
                echo "Deployment of $file started"
                echo "pwd start"
				pwd
                /bi/domain/fmw/user_projects/domains/bi/bitools/bin/runcat.sh -cmd unarchive -online "$2" -credentials "/u01/unec.props" -inputFile "${file}" -folder "/shared/$ApplicationDir" 2>&1 | tee -a ~/deploycat_$(date +"%T").log
                echo "Deployment of $file completed"
            fi
        fi
    done
done
exit 0