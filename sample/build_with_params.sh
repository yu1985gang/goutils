#!/bin/bash

if [ "$1" == "" ]; then
  echo "Usage: $0 path/to/paramproperties"
  exit 0
fi
#file="/tmp/param.properties"
file=$1
echo "start trigger build"

jenkins_api="http://clab175node01.netact.nsn-rdnet.net/view/FP_SyVe/job/Fast_Pass_SyVe_Pipeline/buildWithParameters"
# --data-urlencode "NE_USER_NAME=wsuser" --data-urlencode "NE_PASSWORD=abcdefg"
curl_args=""

if [ -f "$file" ]; then
  echo "properties file $file found."
  while IFS='=' read -r key value; do
    if [[ ${key} == \#* ]]; then
      echo "skip commented: ${key}=${value}"
    else
      curl_args="${curl_args} --data-urlencode \"${key}=${value}\""
    fi
  done <"$file"
else
  echo "properties file $file not found."
fi

curl_args="${curl_args} ${jenkins_api}"

echo "curl args: ${curl_args}"
eval curl -D - -G "${curl_args}"
