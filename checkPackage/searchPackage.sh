#!/bin/bash -x

if [[ $# -ge 3 ]]; then
    BASE_URL=$1
    ARTIFACT_CREDENTIAL=$2
    NE_TECH=$3
else
    echo -e "parameter is missing.\n$0 <BASE_URL> <AUTH> <NE_TECH> [NE_RELEASE]"
    exit 1
fi

if [[ $# -ge 4 ]]; then
    NE_RELEASE=$4
fi

QUERY_STR_BEFORE='''
items.find({
    "$and":
    [
        {"repo": {"$eq": "netact-fast-pass-release-local" }},
        {"path": {"$match": "*"}},
        {"$and": [
            {"property.key": {"$eq": "ready_for_syve"}},
            {"@ready_for_syve": {"$eq": "yes"}},
            {"@nom_syve_picked": {"$ne": "yes"}},
            {"@NOM_Integrity_check": {"$eq": "success"}}'''

QUERY_STR_AFTER='''
        ]}
    ]
})'''

QUERY_OR_BEFORE='{"$or": [ '
QUERY_OR_AFTER=']}'

QUERY_NE_RELEASE_BEFORE='{"@ne_release": {"$eq": "'
QUERY_NE_RELEASE_AFTER='"}}'

QUERY_NE_TECH_BEFORE='{"@ne_technology": {"$eq": "'
QUERY_NE_TECH_AFTER='"}}'

ne_tech_list=(`echo $NE_TECH | tr ',' ' '`)

unset ne_tech_filter_str
for (( i=0; i<${#ne_tech_list[@]}; i++ )); do
    if [[ ! -z "${ne_tech_filter_str}" ]]; then
        ne_tech_filter_str="${ne_tech_filter_str},"
    fi
    ne_tech_filter_str="${ne_tech_filter_str}${QUERY_NE_TECH_BEFORE}${ne_tech_list[$i]}${QUERY_NE_TECH_AFTER}"
done

if [[ ! -z "${ne_tech_filter_str}" ]]; then
    ne_tech_filter_str="${QUERY_OR_BEFORE}${ne_tech_filter_str}${QUERY_OR_AFTER}"
fi
echo "${ne_tech_filter_str}"

if [[ ! -z "${ne_tech_filter_str}" ]]; then
    QUERY_STR="${QUERY_STR_BEFORE},${ne_tech_filter_str}"
fi

ne_release_list=(`echo $NE_RELEASE | tr ',' ' '`)

unset ne_release_filter_str
for (( i=0; i<${#ne_release_list[@]}; i++ )); do
    if [[ ! -z "${ne_release_filter_str}" ]]; then
        ne_release_filter_str="${ne_release_filter_str},"
    fi
    ne_release_filter_str="${ne_release_filter_str}${QUERY_NE_RELEASE_BEFORE}${ne_release_list[$i]}${QUERY_NE_RELEASE_AFTER}"
done

if [[ ! -z "${ne_release_filter_str}" ]]; then
    ne_release_filter_str="${QUERY_OR_BEFORE}${ne_release_filter_str}${QUERY_OR_AFTER}"
fi
echo "${ne_release_filter_str}"


if [[ ! -z "${ne_release_filter_str}" ]]; then
    QUERY_STR="${QUERY_STR},${ne_release_filter_str}"
fi

QUERY_STR="${QUERY_STR}${QUERY_STR_AFTER}"
echo "aql query string is: ${QUERY_STR}"

curl -u "${ARTIFACT_CREDENTIAL}" -X POST -H "content-type: text/plain" "${BASE_URL}/api/search/aql" -d "${QUERY_STR}" > package.json

exit $?
