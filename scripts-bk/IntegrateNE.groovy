import groovy.transform.Field

@Field private Utils = null

echo "Loaded class IntegrateNE.groovy"

Utils = load "${env.WORKSPACE}/scripts/Utils.groovy"

@Field private String integrationPlan = ""
@Field private String integrationPlanName = ""
@Field private String baseUrl = ""
@Field private String noProxy = ""
@Field private String authToken = ""
@Field private String operationId = ""

def initConf(NOM, token, integrationPlanName, integrationPlan) {
    baseUrl = "https://apigw.${NOM.NOM_BASE_DOMAIN}/api/operations/v1"
    noProxy = " --noproxy apigw.${NOM.NOM_BASE_DOMAIN}"
    authToken = ''' -H ''' + '''"Authorization: Bearer ''' + token + '''"'''
    this.integrationPlanName = integrationPlanName
    this.integrationPlan = integrationPlan
}

def importPlan() {
    // start to import the integration plan
    def planImportApi = "curl -ksX POST ${baseUrl}/execute/plan-import?overwrite=true"
    def planImportData = ''' -d ' ''' + integrationPlan + '''' '''

    def httpStatusCode = " -w \'%{http_code}\'"
    def xmlContentType = " -H \"Content-Type: application/xml\" "

    def importPlanCMD = planImportApi + httpStatusCode + noProxy + xmlContentType + planImportData + authToken
    def planImportResult = Utils.shCmd(importPlanCMD, "Import a plan");

    echo "the import plan response: $planImportResult"
    if (planImportResult != '200') {
        error("Fail to Import the integration plan with: ${planImportResult}")
    } else {
        echo "Import the plan successfully"
    }
}

def doIntegration() {
    // start to do the integration
    def planIntegrateApi = "curl -ksX POST ${baseUrl}/request/start/integrate-planned-nes"
    def jsonContentType = " -H \"Content-Type: application/json\" "
    def planIntegrateData = ''' --data '{"operationAttributes":{"planName":"''' + integrationPlanName + '''"}}' '''

    def planIntegrateCmd = planIntegrateApi + noProxy + jsonContentType + planIntegrateData + authToken
    def planIntegrateResult = Utils.shCmd(planIntegrateCmd, "Execute integration plan");
    echo "The execute Integration Plan response: ${planIntegrateResult}"

    operationId = Utils.parseJson(planIntegrateResult).operationId
    echo "Get the operation ID is: ${operationId}"
}

def checkIntegrationStatus() {
    // start to check the integration status
    def operationStatusApi = "curl -kX GET ${baseUrl}/status?operationIds="
    def operationStatusCmd = operationStatusApi + operationId + authToken + noProxy

    def operationStatusResult = Utils.shCmd(operationStatusCmd, "Check integration status");
    echo "$operationStatusResult"
    def integrationStatus = Utils.parseJson(operationStatusResult).status;
    //[{"operationId":"9d0f156193d7421ba630273fba961658","status":"queuing","timestamp":"2020-11-10T06:04:56.428132Z"},{"operationId":"9d0f156193d7421ba630273fba961658","status":"ongoing","timestamp":"2020-11-10T06:04:56.598652Z"},{"operationId":"9d0f156193d7421ba630273fba961658","status":"started","timestamp":"2020-11-10T06:04:56.569053Z"},{"operationId":"9d0f156193d7421ba630273fba961658","status":"failed","timestamp":"2020-11-10T06:05:07.757054Z"}]
    echo "0: The integration status is: ${integrationStatus}"

    timeout(time: 5, unit: 'MINUTES') {
        int count = 1;
        while (!integrationStatus.contains("finished")) {
            if (integrationStatus.contains("failed")) {
                error("Integrate NE failed.")
                break
            }
            sleep 1
            operationStatusResult = Utils.shCmd(operationStatusCmd, "Check integration status");
            echo "$operationStatusResult"
            integrationStatus = Utils.parseJson(operationStatusResult).status;
            echo "${count}: The integration status is: ${integrationStatus}"

            count++
        }
    }
}

return this
