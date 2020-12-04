import groovy.transform.Field
@Field private STAGE = "scope:nom_syve|name:%s|status:%s"
@Field private Conf = null

echo "Loaded class Stage.groovy"
Conf = readYaml(file: "${env.WORKSPACE}/configuration/syve.yaml")

def stageStarted(String packageName, String stageName, String props = "") {
    sendFeedback(packageName, String.format(STAGE, stageName, 'started'), 0, props)
}

def stageSucceed(String packageName, String stageName, String props = "") {
    sendFeedback(packageName, String.format(STAGE, stageName, 'success'), 0, props)
}

def sendFeedback(String packageName, String stage, int scopeComplete = 0, String props = "") {
    triggerRemoteJob auth: NoneAuth(), job: Conf.FP_FEEDBACK_COLLECTOR.FQDN, maxConn: 1, overrideTrustAllCertificates: true,
    parameters: """token=${Conf.FP_FEEDBACK_COLLECTOR.TOKEN}
        package_path=${packageName}
        stage=${stage}
        scope_complete=${scopeComplete}
        props=${props}""",
    useCrumbCache: true,
    useJobInfoCache: true
}

return this