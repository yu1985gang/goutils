import groovy.transform.Field

// configuration related object
@Field private FP_PACKAGE_LINK = ''
@Field private FP_PACKAGE_NAME = ''
@Field private Conf = null
@Field private NOM = null
@Field private NE = null
@Field private NE_TC = null
@Field private CCTF = null
@Field private COMMON_TC_PACKAGE_URL = null
@Field private CUR_STAGE_NAME = null

// some global variable
@Field private CLIENT_ID = null
@Field private CLIENT_SECRET = null
@Field private ACCESS_TOKEN = null
@Field private INTEGRATION_PLAN_NAME = ""
@Field private INTEGRATION_PLAN = ""

echo "Loaded class Pipeline.groovy"

Download = load "${env.WORKSPACE}/scripts/DownloadFP.groovy"
Deploy = load "${env.WORKSPACE}/scripts/Deploy.groovy"
Stage = load "${env.WORKSPACE}/scripts/Stage.groovy"
Precheck = load "${env.WORKSPACE}/scripts/Precheck.groovy"
IntegrateNE = load "${env.WORKSPACE}/scripts/IntegrateNE.groovy"
TestCase = load "${env.WORKSPACE}/scripts/TestCase.groovy"

def preCheck(NE_SW_ID, NE_MO_CLASS_ID, NE_DIST_NAME, NE_HOST, NE_PORT, NE_USER_NAME, NE_PASSWORD, CUSTOM_INTEGRATION_PARAMS,
                NE_TC_PACKAGE_URL, NE_TC_DOCKER_IMAGE_URL, NE_TC_PARAMETERS, NE_CERTIFICATES) {
    CUR_STAGE_NAME = "PRE_CHECK"
    Precheck.validateParams(NE_SW_ID, NE_DIST_NAME, NE_HOST, NE_PORT, NE_USER_NAME, NE_PASSWORD)
    
    FP_PACKAGE_LINK = Precheck.getFPLink(ne_sw_id)
    echo "FP_PACKAGE_LINK: ${FP_PACKAGE_LINK}"
    FP_PACKAGE_NAME = Precheck.getFPPackageName(FP_PACKAGE_LINK)
    echo "FP_PACKAGE_NAME: ${FP_PACKAGE_NAME}"

    NE = ['class' : NE_MO_CLASS_ID, distName: NE_DIST_NAME, host: NE_HOST, port: NE_PORT,
            username: NE_USER_NAME, password: NE_PASSWORD]

    (Conf, NOM, CCTF) = Precheck.getDefaultConf()
    
    (INTEGRATION_PLAN_NAME, INTEGRATION_PLAN) = Precheck.validateAndGenPlan(NE, Precheck.createParamOptionalMap(CUSTOM_INTEGRATION_PARAMS), Conf, FP_PACKAGE_LINK, NE_CERTIFICATES)
    Precheck.validateHost(NE, CCTF)
    Precheck.checkCCTF(CCTF)
    
    NE_TC = ['NE_TC_PACKAGE_URL': NE_TC_PACKAGE_URL, 'NE_TC_DOCKER_IMAGE_URL': NE_TC_DOCKER_IMAGE_URL, 'NE_TC_PARAMETERS': NE_TC_PARAMETERS]
    echo 'Use the following parameters to SyVe:'
    echo "NOM.NOM_USER_NAME: ${NOM.NOM_USER_NAME}"
    echo "NOM.NOM_BASE_DOMAIN: ${NOM.NOM_BASE_DOMAIN}"
    echo "NOM.NOM_REALM_NAME: ${NOM.NOM_REALM_NAME}"
    echo "NOM.NOM_RELEASE: ${NOM.NOM_RELEASE}"
    echo "CCTF.FQDN: ${CCTF.FQDN}"
    echo "COMMON_TC_PACKAGE_URL: ${Conf.COMMON_TC_PACKAGE_URL}"
    echo "NE_TC: ${NE_TC}"
}

def downloadFpPackage() {
    Download.checkFpLink(FP_PACKAGE_LINK)

    CUR_STAGE_NAME = "DOWNLOAD_FP_PACKAGE"
    echo "FP package name: ${FP_PACKAGE_NAME}"
    Stage.stageStarted(FP_PACKAGE_NAME, CUR_STAGE_NAME)

    Download.downloadFp(FP_PACKAGE_LINK)

    Download.validateFp(FP_PACKAGE_NAME)

    echo "Set FP package picked label for NOM SyVe"
    def props = 'name:nom_syve_picked|value:yes|mode:overwrite'
    Stage.stageSucceed(FP_PACKAGE_NAME, CUR_STAGE_NAME, props)

}

def deployPackage() {
    CUR_STAGE_NAME = "DEPLOY_FP_PACKAGE"
    Stage.stageStarted(FP_PACKAGE_NAME, CUR_STAGE_NAME)

    CLIENT_ID = Deploy.generateRandClientId()
    CLIENT_SECRET = Deploy.createClientIfNotExists(CLIENT_ID, NOM)
    ACCESS_TOKEN = Deploy.refreshAccessToken(CLIENT_SECRET, CLIENT_ID, NOM)
    if (Deploy.deployPackage(FP_PACKAGE_NAME, ACCESS_TOKEN, NOM)) {
        Stage.stageSucceed(FP_PACKAGE_NAME, CUR_STAGE_NAME)
    } else {
        error("Unknown reason, set this stage failed.")
    }
}

def integrateNE() {
    try {
        CUR_STAGE_NAME = "INTEGRATE_NE"
        Stage.stageStarted(FP_PACKAGE_NAME, CUR_STAGE_NAME)

        IntegrateNE.initConf(NOM, ACCESS_TOKEN, INTEGRATION_PLAN_NAME, INTEGRATION_PLAN)

        IntegrateNE.importPlan()
        IntegrateNE.doIntegration()
        IntegrateNE.checkIntegrationStatus()

        Stage.stageSucceed(FP_PACKAGE_NAME, CUR_STAGE_NAME)
    } catch (Exception e) {
        error("Integrate NE failed. Exception: ${e}")
    }
}

def exeTestCase() {
    CUR_STAGE_NAME = "RUN_TEST_CASES"
    Stage.stageStarted(FP_PACKAGE_NAME, CUR_STAGE_NAME)

    TestCase.execute(Conf, NOM, NE, CCTF, NE_TC, FP_PACKAGE_LINK, FP_PACKAGE_NAME)

    Stage.stageSucceed(FP_PACKAGE_NAME, CUR_STAGE_NAME)
}

def postAlways() {
    Download.removeFp(FP_PACKAGE_NAME)
    if(CLIENT_ID) {
        Deploy.deleteClient(CLIENT_ID, NOM)
    } else {
        echo "No need to delete client for client id: ${CLIENT_ID}"
    }
}

def scopeFailed() {
    Stage.scopeFailed(FP_PACKAGE_NAME, CUR_STAGE_NAME)
}

def scopeSucceed() {
    Stage.scopeSucceed(FP_PACKAGE_NAME, 'nom_syve_pipe')
}

def scopeAborted() {
    Stage.scopeAborted(FP_PACKAGE_NAME, 'nom_syve_pipe')
}

return this
