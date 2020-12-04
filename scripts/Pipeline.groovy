import groovy.transform.Field

// configuration related object
@Field private FP_PACKAGE_NAME = null
@Field private Conf = null
@Field private NOM = null
@Field private CCTF = null
@Field private COMMON_TC_PACKAGE_URL = null

// stage related object
@Field private Deploy = null

// some global variable
@Field private CLIENT_ID = null
@Field private CLIENT_SECRET = null
@Field private ACCESS_TOKEN = null
@Field private INTEGRATION_PLAN_NAME = ""
@Field private INTEGRATION_PLAN = ""

echo "Loaded class Pipeline.groovy"

Deploy = load "${env.WORKSPACE}/scripts/Deploy.groovy"
Stage = load "${env.WORKSPACE}/scripts/Stage.groovy"
Precheck = load "${env.WORKSPACE}/scripts/Precheck.groovy"
IntegrateNE = load "${env.WORKSPACE}/scripts/IntegrateNE.groovy"

//def precheck(Map<String, String> paramMap, String customIntegrationParams) {
def validateAndGenPlan(Map<String, String> paramMap, String customIntegrationParams, String fpPackageLink) {
    (INTEGRATION_PLAN_NAME, INTEGRATION_PLAN) = Precheck.validateAndGenPlan(paramMap, Precheck.createParamOptionalMap(customIntegrationParams), Conf, fpPackageLink)
}

def deployPackage() {
    def stage_name = "DEPLOY_FP_PACKAGE"
    Stage.stageStarted(FP_PACKAGE_NAME, stage_name)

    CLIENT_ID = Deploy.generateRandClientId()
    CLIENT_SECRET = Deploy.createClientIfNotExists(CLIENT_ID, NOM)
    ACCESS_TOKEN = Deploy.refreshAccessToken(CLIENT_SECRET, CLIENT_ID, NOM)
    if (Deploy.deployPackage(FP_PACKAGE_NAME, ACCESS_TOKEN, NOM)) {
        Stage.stageSucceed(FP_PACKAGE_NAME, stage_name)
    } else {
        error("Unknown reason, set this stage failed.")
    }
}

def integrateNE() {
    try {
        def stageName = "INTEGRATE_NE"
        Stage.stageStarted(FP_PACKAGE_NAME, stageName)

        IntegrateNE.initConf(NOM, ACCESS_TOKEN, INTEGRATION_PLAN_NAME, INTEGRATION_PLAN)

        IntegrateNE.importPlan()
        IntegrateNE.doIntegration()
        IntegrateNE.checkIntegrationStatus()

        Stage.stageSucceed(FP_PACKAGE_NAME, stageName)
    } catch (Exception e) {
        error("Integrate NE failed. Exception: ${e}")
    }
}

def initConf(fpPackageName) {
    FP_PACKAGE_NAME = fpPackageName
    Conf = readYaml(file: "${env.WORKSPACE}/configuration/syve.yaml")
    if(Conf.NOM.size() >= 1) {
        NOM = Conf.NOM[0]
    } else {
        error("No enough NOM defined for SyVe!")
    }

    CCTF = Conf.CCTF
    if(!CCTF) {
        error("No default CCTF defined for SyVe!")
    }

    COMMON_TC_PACKAGE_URL = Conf.COMMON_TC_PACKAGE_URL
    if(!COMMON_TC_PACKAGE_URL) {
        error("No default COMMON_TC_PACKAGE_URL defined for SyVe!")
    }
}

def preCheck(paramMap) {
    Precheck.validateHost(paramMap, Conf.NE[0], CCTF)
    
    echo 'Use the following parameters to SyVe:'
    echo "NOM.NOM_USER_NAME: ${NOM.NOM_USER_NAME}"
    echo "NOM.NOM_BASE_DOMAIN: ${NOM.NOM_BASE_DOMAIN}"
    echo "NOM.NOM_REALM_NAME: ${NOM.NOM_REALM_NAME}"
    echo "NOM.NOM_RELEASE: ${NOM.NOM_RELEASE}"
    echo "CCTF.FQDN: ${CCTF.FQDN}"
    echo "COMMON_TC_PACKAGE_URL: ${COMMON_TC_PACKAGE_URL}"
}

def getNOMConf() {
    return NOM
}

def getCCTFConf() {
    return CCTF
}

def getCommonTCPackageUrl() {
    return COMMON_TC_PACKAGE_URL
}

def postAlways() {
    Deploy.deleteClient(CLIENT_ID, NOM)
}

return this
