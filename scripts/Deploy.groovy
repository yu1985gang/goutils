import groovy.transform.Field
@Field private Utils = null
@Field private API_Props = null

echo "Loaded class Deploy.groovy"

Utils = load "${env.WORKSPACE}/scripts/Utils.groovy"
API_Props = readProperties(file: "${env.WORKSPACE}/scripts/conf/sconf/nom.properties")

def generateRandClientId() {
    return "fp-" + UUID.randomUUID().toString();
}

def createClientIfNotExists(client_id, NOM) {
    def apiStr = String.format(API_Props['API_REGISTRATION'], NOM.NOM_BASE_DOMAIN, client_id, NOM.NOM_USER_NAME, NOM.NOM_PASSWORD)
    def fpClientJson = Utils.shCmd(Utils.aCurl() + "-X POST " + apiStr, "Create a new client with client id ${client_id}")

    def fpClient = Utils.parseJson(fpClientJson);
    if (fpClient == "" || fpClient.clientSecret == null || fpClient.clientSecret == "") {
        error "Client secret not existed in response: ${fpClient}"
    }
    return fpClient.clientSecret
}

def refreshAccessToken(client_secrete, client_id, NOM) {
    def apiStr = String.format(API_Props['API_GET_TOKEN'],
        NOM.NOM_BASE_DOMAIN, NOM.NOM_REALM_NAME, client_id, client_secrete, NOM.NOM_USER_NAME, NOM.NOM_PASSWORD)
    def accessTokenJson = Utils.shCmd(Utils.aCurl() + "-X POST " + apiStr, "Get access token")

    def accessToken = Utils.parseJson(accessTokenJson);
    if (accessToken == "" || accessToken.access_token == null || accessToken.access_token == "") {
        error "Access token not existed in response: ${accessToken}"
    }
    return accessToken.access_token
}

def deployPackage(fp_package_name, access_token, NOM) {
    def apiStr = String.format(API_Props['API_DEPLOY_PACKAGE'],
        NOM.NOM_BASE_DOMAIN, fp_package_name, access_token)
    def importHttpCodeResponse = Utils.shCmd(Utils.aCurl() + "-X POST " + apiStr, "Import fast pass package")

    echo "import response: ${importHttpCodeResponse}"
    if (importHttpCodeResponse == "201" || importHttpCodeResponse == "400") {
        echo "This package is imported successfully or already exist."
        return true
    } else {
        echo 'Re-execute to get the error response'
        importHttpCodeResponse = Utils.shCmd(Utils.aCurl() + "-X POST " + apiStr, "Import fast pass package")
        echo "import response: ${importHttpCodeResponse}"
    }

    return false
}

def deleteClient(client_id, NOM) {
    def apiStr = String.format(API_Props['API_DELETE_CLIENT'],
        NOM.NOM_BASE_DOMAIN, client_id, NOM.NOM_USER_NAME, NOM.NOM_PASSWORD)
    def deletionMessage = Utils.shCmd(Utils.aCurl() + "-X DELETE " + apiStr, "Delete client with client id ${client_id}")
    echo "deletion response: ${deletionMessage}"
}

return this
