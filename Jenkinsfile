pipeline {
    agent any
    options {
        timeout(time: 4, unit: 'HOURS')
        timestamps()
    }
    parameters {
        string(
            name: 'FP_PACKAGE_LINK',
            description: '''<p style="color:rgb(255,0,0)">MANDATORY</p>
            <p>Fast pass package link, e.g. https://esisoj70.emea.nsn-net.net/artifactory/netact-fast-pass-release-local/fast_pass_packages/SBTS20C_ENB_9999_200602_000006/20200615T052532/SBTS20C_ENB_9999_200602_000006_20200615T052532.zip</p>''',
            trim: true)
        string(
            name: 'NE_MO_CLASS_ID',
            defaultValue: '',
            description: '''MO attribute: managed object class ID, for example, com.nokia.nms.integration:MRBTSINT.''')
        string(
            name: 'NE_DIST_NAME',
            defaultValue: '',
            description: '''MO attribute: the Distinguished Name (DN) of the managed object, for example, MRBTS-1813/MRBTSINT-AUTO''')
        string(
            name: 'NE_HOST',
            defaultValue: '',
            description: '''NE IP or FQDN''')
        string(
            name: "NE_PORT",
            defaultValue: '',
            description: '''NE port''')
        string(
            name: "NE_USER_NAME",
            defaultValue: '',
            description: '''NE user name''')
        password(
            name: "NE_PASSWORD",
            description: '''NE password''')
        string(
            name: "NE_SBINET_CONNECTED_TO",
            // defaultValue: 'sbi-default-ipv4',
            description: '''Defines the connection method to NOM, one of the following three values: sbi-default-dns, sbi-default-ipv4 or sbi-default-ipv6.''')
        string(
            name: "NE_MO_CLASS_VERSION",
            // defaultValue: '1.0',
            description: '''MO attribute: managed object class version, default is 1.0''')
        string(
            name: "CUSTOM_INTEGRATION_PARAMS",
            defaultValue: '',
            description: 'Key value pairs for custom integration parameters, for example, "elementManagerURL=abc.net,sbiSwDownloadEndpoint=def.net"')
        string(
            name: "NOM_BASE_DOMAIN",
            defaultValue: '',
            description: 'NOM base domain, for example, neo0033.dyn.nesc.nokia.net')
        string(
            name: "NOM_USER_NAME",
            defaultValue: '',
            description: 'NOM user for deploying FP package, integrating NE.')
        password(
            name: "NOM_PASSWORD",
            description: 'The password of NOM user for deploying FP package, integrating NE.')
        string(
            name: "NOM_REALM_NAME",
            defaultValue: '',
            description: 'This is required in order to get access token. Contact the Network Operations Master administrator to get the value.')
        string(
            name: "NE_TC_PACKAGE_URL",
            defaultValue: '',
            description: 'NE specific test cases package URL.')
        string(
            name: "NE_TC_DOCKER_IMAGE",
            defaultValue: '',
            description: 'Docker image name for running NE specific test cases.')
        string(
            name: "NE_TC_DOCKER_REGISTRY",
            defaultValue: '',
            description: 'Docker registry of the docker image which is for running NE specific test cases.')
        string(
            name: "NE_TC_PARAMETERS",
            defaultValue: '',
            description: 'Key value pairs for NE specific test case parameters, for example, "DN=MRBTS-1813/MRBTSINT-AUTO, myPara=value1".')
    }

    environment {
        FP_PACKAGE_NAME = ""

        PIPELINE = ""

        // TODO: should be removed after Test stage un-use them
        NOM = ""
        CCTF = ""
        COMMON_TC_PACKAGE_URL = ""
        
        CLIENT_ID = " "
        CLIENT_SECRET = " "
        ACCESS_TOKEN=""
        STAGE = "scope:nom_syve|name:%s|status:%s"
        STAGE_NAME = ""
        STAGE_STATUS = ""
    }

    stages {
        stage('Precheck') {
            steps {
                // check the input parameter
                script {
                    if (!FP_PACKAGE_LINK) {
                        error('Fast Pass package link is mandatory but is missing.')
                    }
                    FP_PACKAGE_NAME = sh script:'basename ${FP_PACKAGE_LINK}', returnStdout: true
                    FP_PACKAGE_NAME = FP_PACKAGE_NAME.trim().replaceAll("(\\r|\\n)", "")
                }

                // get default value from the configuration file
                script {
                    PIPELINE = load "${env.WORKSPACE}/scripts/Pipeline.groovy"

                    // NOM related parameters can be passed by NE SyVe tester, if not, get from pool
                    if (NOM_BASE_DOMAIN && NOM_USER_NAME && NOM_PASSWORD && NOM_REALM_NAME) {
                        NOM = [NOM_BASE_DOMAIN: NOM_BASE_DOMAIN, NOM_USER_NAME: NOM_USER_NAME, NOM_PASSWORD: NOM_PASSWORD, NOM_REALM_NAME: NOM_REALM]
                    } else if (!NOM_BASE_DOMAIN && !NOM_USER_NAME && !NOM_PASSWORD && !NOM_REALM_NAME) {
                        echo "==> PIPELINE.initConf"
                        PIPELINE.initConf(FP_PACKAGE_NAME)
                    } else {
                        error("NOM related parameters are incomplete, please check.")
                    }

                    def paramMap = ['class' : NE_MO_CLASS_ID, distName: NE_DIST_NAME, host: NE_HOST, port: NE_PORT,
                                    username: NE_USER_NAME, password: NE_PASSWORD, sbiNetConnectedTo: NE_SBINET_CONNECTED_TO, version: NE_MO_CLASS_VERSION]
                    echo "===>paramMpa is $paramMap"
                    PIPELINE.validateAndGenPlan(paramMap, CUSTOM_INTEGRATION_PARAMS, FP_PACKAGE_LINK)
                    
                    PIPELINE.preCheck(paramMap)
                    
                    NOM = PIPELINE.getNOMConf()
                    CCTF = PIPELINE.getCCTFConf()
                    COMMON_TC_PACKAGE_URL = PIPELINE.getCommonTCPackageUrl()
                }
            }
        }
    }

}

def removeFpPackage(String packageName) {
    if (packageName) {
        def rc = sh script: "test -f ${packageName}", returnStatus: true
        if (rc == 0) {
            echo "Remove downloaded FP package: ${packageName}"
            sh script: "rm -f ${packageName}"
        }
    }
}

def shCmd(String cmd, String label = "") {
    return sh (script: cmd, returnStdout: true)
}

def parseJson(String json) {
    try {
        return readJSON(text: json)
    } catch (Exception e) {
        error("Fail to parse data to json: ${json}\nException: ${e}")
        return ""
    }
}

def sendFeedback(String packageName, String stage, int scopeComplete = 0, String props = "") {
    triggerRemoteJob job: "http://cidash.netact.nsn-rdnet.net/fastpass/job/Fast_Pass_Feedback_Collector", maxConn: 1, overrideTrustAllCertificates: true,
    parameters: """token=FPTOKEN
        package_path=${packageName}
        stage=${stage}
        scope_complete=${scopeComplete}
        props=${props}""",
    useCrumbCache: true,
    useJobInfoCache: true
}

def stageStarted(String packageName, String stageName, String props = "") {
    STAGE_STATUS = 'started'
    sendFeedback(packageName, String.format(STAGE, stageName, STAGE_STATUS), 0, props)
}

def stageSucceed(String packageName, String stageName, String props = "") {
    STAGE_STATUS = 'success'
    sendFeedback(packageName, String.format(STAGE, stageName, STAGE_STATUS), 0, props)
}

def scopeAborted(String packageName, String stageName, String props = "") {
    if (packageName) {
        sendFeedback(packageName, String.format(STAGE, stageName, "aborted"), 1, props)
    }
}

def scopeFailed(String packageName, String stageName, String props = "") {
    STAGE_STATUS = 'failed'
    if (packageName) {
        sendFeedback(packageName, String.format(STAGE, stageName, STAGE_STATUS), 1, props)
    }
}

def scopeSucceed(String packageName, String stageName, String props = "") {
    sendFeedback(packageName, String.format(STAGE, stageName, "success"), 1, props)
}

def publishTestResults() {
    echo "publish test result"
step([
        $class           : 'RobotPublisher',
        outputPath       : '../builds/${BUILD_NUMBER}/test_report/log/sut_log',
        passThreshold    : 100,
        unstableThreshold: 100,
        otherFiles       : '',
        reportFileName   : '*/report*.html',
        logFileName      : '*/*.html',
        outputFileName   : '*/output*.xml'
])}

def aCurl() {
    return """curl -k -x "" --connect-timeout 10 -m 30 --retry 3 --retry-delay 5 """
}

