import groovy.transform.Field

// stage related object
@Field private FastPassFeature = null

echo "Loaded class TestCase.groovy"

FastPassFeature = load "${env.WORKSPACE}/scripts/FastPassFeature.groovy"

def execute(Conf, NOM, NE, CCTF, NE_TC, FP_PACKAGE_LINK, FP_PACKAGE_NAME) {
    try {
        TP_BASE_DOMAIN=NOM.NOM_BASE_DOMAIN
        TP_NOM_OPEN_API_GW="apigw."+NOM.NOM_BASE_DOMAIN
        TP_NOM_OPEN_API_USERNAME=NOM.NOM_USER_NAME
        TP_NOM_OPEN_API_PASSWORD=NOM.NOM_PASSWORD
        
        def ne_protocol = "NE3SWS"
        
        NE_INTEGRATED_ID=NE.distName.split('/')[0]

        NE_METADATA_FASTPASS_PACKAGE_LINK = FP_PACKAGE_LINK

        test_product_name = "product_" + FP_PACKAGE_NAME
        echo test_product_name
        test_report_path = JENKINS_HOME + "/jobs/" + JOB_NAME + "/builds/" + BUILD_NUMBER + "/test_report"
        test_log_file_name = "test_log_" + System.currentTimeMillis() + ".zip"
        echo test_report_path
        def supportedFeatures = FastPassFeature.getSupportedFeaturesString(FP_PACKAGE_NAME)
        
        test_tag = "ID_3001,ID_3002,ID_3003,ID_3004"

        sh """#!/bin/bash -x
        cd CCTF_API
        python -c "from run_case_in_cctf import run_case_in_cctf; run_case_in_cctf('${CCTF.FQDN}','${CCTF.USER_NAME}','${CCTF.PASSWORD}','${test_product_name}','${Conf.COMMON_TC_PACKAGE_URL}','${Conf.COMMON_TC_DOCKER_IMAGE}','${TP_BASE_DOMAIN}','${TP_NOM_OPEN_API_GW}','${TP_NOM_OPEN_API_USERNAME}','${TP_NOM_OPEN_API_PASSWORD}','${ne_protocol}','${NE_INTEGRATED_ID}','${NE_METADATA_FASTPASS_PACKAGE_LINK}','${NE_TC.NE_TC_PACKAGE_URL}','${NE_TC.NE_TC_DOCKER_IMAGE_URL}','${NE_TC.NE_TC_PARAMETERS}','${test_tag}','${test_log_file_name}')"
        
        mkdir ${test_report_path}
        cd ${test_report_path}
        mv ${WORKSPACE}/CCTF_API/${test_log_file_name} ./
        tar xzvf ${test_log_file_name}
        """
    } catch(Exception e) {
        error("Test execution failed. Exception: ${e}")
    } finally {
        publishTestResults()
    }
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

return this