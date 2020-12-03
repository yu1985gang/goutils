import groovy.transform.Field
@Field private STAGE = "scope:nom_syve|name:%s|status:%s"
@Field private JOB_Props = null

echo "Loaded class Stage.groovy"
JOB_Props = readProperties(file: "${env.WORKSPACE}/scripts/conf/sconf/job.properties")

def stageStarted(String packageName, String stageName, String props = "") {
    sendFeedback(packageName, String.format(STAGE, stageName, 'started'), 0, props)
}

def stageSucceed(String packageName, String stageName, String props = "") {
    sendFeedback(packageName, String.format(STAGE, stageName, 'success'), 0, props)
}

def sendFeedback(String packageName, String stage, int scopeComplete = 0, String props = "") {
    triggerRemoteJob auth: NoneAuth(), job: JOB_Props['FEEDBACK_COLLECTOR_URL'], maxConn: 1, overrideTrustAllCertificates: true,
    parameters: """token=FPTOKEN
        package_path=${packageName}
        stage=${stage}
        scope_complete=${scopeComplete}
        props=${props}""",
    useCrumbCache: true,
    useJobInfoCache: true
}

return this