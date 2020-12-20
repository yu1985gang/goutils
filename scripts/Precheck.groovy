import groovy.transform.Field

@Field private Utils = null
@Field private DnValidator = null
@Field private selectedNE = null
@Field private Conf = null
@Field private GET_LATEST_FP_PACKAGE = "%s/api/storage/netact-fast-pass-release-local/fast_pass_packages/%s?lastModified"
@Field private String SshKeyFile = "${env.WORKSPACE}/${JOB_NAME}_${BUILD_NUMBER}.pem"

echo "Loaded class Precheck.groovy"

Utils = load "${env.WORKSPACE}/scripts/Utils.groovy"
DnValidator = load "${env.WORKSPACE}/scripts/DnValidator.groovy"
Conf = readYaml(file: "${env.WORKSPACE}/configuration/syve.yaml")

def validateParams(NE_SW_ID, NE_DIST_NAME, NE_HOST, NE_PORT, NE_USER_NAME, NE_PASSWORD) {
    if (!NE_SW_ID) {
        error('Parameter NE_SW_ID is mandatory but is missing.')
    }
    if (!NE_DIST_NAME) {
        error('Parameter NE_DIST_NAME is mandatory but is missing.')
    }
    if (!DnValidator.isValidDistName(NE_DIST_NAME)) {
        error('Parameter NE_DIST_NAME is invalid format, please check with DN spec.')
    }
    if (!NE_HOST) {
        error('Parameter NE_HOST is mandatory but is missing.')
    }
    if (!NE_PORT) {
        error('Parameter NE_PORT is mandatory but is missing.')
    }
    if (!NE_USER_NAME) {
        error('Parameter NE_USER_NAME is mandatory but is missing.')
    }
    if (!NE_PASSWORD) {
        error('Parameter NE_PASSWORD is mandatory but is missing.')
    }
}

def getFPPackageName(fpPackageLink) {
    def fpPackageName = sh script: 'basename ' + fpPackageLink, returnStdout: true
    return fpPackageName.trim().replaceAll("(\\r|\\n)", "")
}

def getFPLink(neSwId) {
    def baseUrl = Conf.FAST_PASS_ARTIFACTORY_BASE_URL
    if (!baseUrl) {
        error("Parameter FAST_PASS_ARTIFACTORY_BASE_URL is missing in configuration file")
    }

    echo "Get latest fast pass package download URI with ne_sw_id ${neSwId}"
    return getDownloadUriOfFpPackage(getLatestFpPackageUri(String.format(GET_LATEST_FP_PACKAGE, baseUrl, neSwId)))
}

def getLatestFpPackageUri(latestFpPackage) {
    def latestFpPackageUri = Utils.parseJson(Utils.shCmd(Utils.aCurl() + latestFpPackage, "Get latest fast pass package URI")).uri
    if (!latestFpPackageUri) {
        error("Failed to get latest fast pass package URI")
    }
    return latestFpPackageUri
}

def getDownloadUriOfFpPackage(fpPackageUri) {
    def fpPackageDownloadUri = Utils.parseJson(Utils.shCmd(Utils.aCurl() + fpPackageUri, "Get lastest fast pass package details")).downloadUri
    if (!fpPackageDownloadUri) {
        error("Failed to get fast pass package download URI")
    }
    return fpPackageDownloadUri
}

def getDefaultConf() {
    if(Conf.NOM.size() < 1) {
        error("No enough NOM defined for SyVe!")
    }

    if(!Conf.CCTF) {
        error("No default CCTF defined for SyVe!")
    }

    return [Conf, Conf.NOM[0], Conf.CCTF]
}

def validateHost(NE,NOM,CCTF) {
    //Ping NOM_BASE_DOMAIN, NE_HOST and CCTF_FQDN
    def neHost = '0.0.0.0'
    if (NE['host']) {
        neHost = NE['host']
        echo "Got ne host from parameter: NE_HOST=${neHost}"
    } else if (selectedNE != null) {
        neHost = selectedNE.FQDN
        echo "Use the ne host from configuration: NE_HOST=${neHost}"
    }

    //Check if NOM parameters are defined and not empty
    ["NOM_BASE_DOMAIN": NOM.NOM_BASE_DOMAIN, "CCTF_FQDN": CCTF.FQDN,"NOM_EDGE_NODE_HOST":NOM.NOM_EDGE_NODE_HOST, "NOM_SSH_USERNAME":NOM.NOM_SSH_USERNAME,"NOM_SSH_KEY":NOM.NOM_SSH_KEY].each{k,v->
        if ( v == null || v.trim() == ""){
            error("Invalid conf,${k}=${v}")
        }
    }

    //ping NOM_BASE_DOMAIN and CCTF
    ["NOM_BASE_DOMAIN": NOM.NOM_BASE_DOMAIN, "CCTF_FQDN": CCTF.FQDN].each { k, v ->
        pingAddress(v)
        echo "Ping ${k} successfully"
    }

    //ping NE (IP or DNS) in NOM
    ["NE_HOST": neHost].each {k, v ->
        pingNE(v,NOM.NOM_SSH_KEY,NOM.NOM_SSH_USERNAME,NOM.NOM_EDGE_NODE_HOST)
        echo "Ping ${k} successfully"
    }
}

def pingAddress(String addr) {
    // ping address in Jenkins server
    def res = sh script: "ping -c3 ${addr}", returnStatus: true, label: "Ping address"
    if (res != 0) {
        error("ping address ${addr} timeout, please check")
    }
}  

def pingNE(String addr,String sshKey, String sshUerName, String remoteIp) {
    //ping NE address in NOM
    if (!fileExists(SshKeyFile)){
        genSshKeyFile(sshKey)
    }
    def isIPv4IP = Utils.isIPv4(addr)
    def isIPv6IP = Utils.isIPv6(addr)
    def isIPv4DNS = isIPv4DNS(addr,SshKeyFile,sshUerName,remoteIp)
    def isIPv6DNS = isIPv6DNS(addr,SshKeyFile,sshUerName,remoteIp)
    echo "isIPv4IP is $isIPv4IP,isIPv6IP is $isIPv6IP,isIPv4DNS is $isIPv4DNS,isIPv6DNS is $isIPv6DNS"
    //def pingIPv4Res,pingIPv6Res

    if (!isIPv4IP && !isIPv6IP && !isIPv4DNS && !isIPv6DNS){
        error("NE_HOST:${addr} is neigther IP nor DNS.")
    }

    if (isIPv4IP || isIPv4DNS){
        res = sh script: "ssh -i ${SshKeyFile} ${sshUerName}@${remoteIp} ping -c3 ${addr}", returnStatus: true, label: "Ping ipv4 address"
        if(res!=0){
            error("Ping NE address ${addr} timeout, please check")
        }
    }

    if (isIPv6IP || isIPv6DNS){
        rtIface = Utils.shCmd("ssh -i ${SshKeyFile} ${sshUerName}@${remoteIp} netstat -rn | grep '^0.0.0.0' | rev | cut -d ' '  -f1 | rev").trim().replaceAll("(\\r|\\n)","")
        res = sh script: "ssh -i ${SshKeyFile} ${sshUerName}@${remoteIp} ping6 -I ${rtIface} -c3 ${addr}", returnStatus: true, label: "Ping ipv6 address"
        if(res!=0){
            error("Ping NE address ${addr} timeout, please check")
        }
    }
}

def genSshKeyFile(String sshKey){
    writeFile file: SshKeyFile, text: sshKey
    if(fileExists(SshKeyFile)){
        Utils.shCmd("chmod 400 ${SshKeyFile}","Set ssh key file as read-only")
    }else{
        error("Generate ssh key file failed")
    }
}

def delSshKeyFile(){
    Utils.shCmd("rm -rf ${SshKeyFile}","Delete ssh key file.")
}

def isIPv4DNS(String fqdn,String sshKeyFile, String sshUerName, String remoteIp){
    def iPv4DNS = sh script:"ssh -i ${sshKeyFile} ${sshUerName}@${remoteIp} host ${fqdn} |grep -i 'has address'",returnStatus:true
    if (Utils.isIPv4(fqdn) || Utils.isIPv6(fqdn)){
        return false
    }
    return iPv4DNS == 0
}

def isIPv6DNS(String fqdn,String sshKeyFile, String sshUerName, String remoteIp){
    def iPv6DNS = sh script: "ssh -i ${sshKeyFile} ${sshUerName}@${remoteIp} host ${fqdn} |grep -i 'has IPv6 address'",returnStatus:true
    if (Utils.isIPv4(fqdn) || Utils.isIPv6(fqdn)){
        return false
    }
    return iPv6DNS == 0
}

def createParamOptionalMap(String customIntegrationParams) {
    def paramOptionalMap = [:]
    if (customIntegrationParams) {
        customIntegrationParams.split(',').each { item ->
            def entry = item.split('=', 2)
            if (entry.size() != 2) {
                println entry
                error("Parameter CUSTOM_INTEGRATION_PARAMS is not a valid format")
//              throw new Exception("Parameter CUSTOM_INTEGRATION_PARAMS is not a valid format")
            } else {
                paramOptionalMap.put(entry[0].trim(), entry[1].trim())
            }
        }
    }
    return paramOptionalMap
}

def validateAndGenPlan(paramMap, paramOptionalMap, conf, fpPackageLink, NE_CERTIFICATES) {
    def allParametersFilled = paramMap.every { _, v ->
        v != null && "${v}".trim() != ""
    }
    if (!allParametersFilled) {
        error('All mandatory parameters (like NE_*) must be filled')
    }

    def moClass = paramMap["class"]
    def dn = paramMap["distName"]
    if ("com.nokia.nms.integration:MRBTSINT".equals(moClass)) {
        def dnSuffix = "/MRBTSINT-AUTO"
        if (!dn.endsWith(dnSuffix)) {
            paramMap["distName"] = dn + dnSuffix
        }
    } else if ("com.nokia.nms.integration:NE3SINT".equals(moClass)) {
        def dnSuffix = "/NE3SINT-PREDEFINED"
        if (!dn.endsWith(dnSuffix)) {
            paramMap["distName"] = dn + dnSuffix
        }
    }

    paramMap["version"] = "1.0"

    def sbiNetConnectedTo = "sbi-default-dns"
    def host = paramMap["host"]
    if (Utils.isIPv4(host)) {
        sbiNetConnectedTo = "sbi-default-ipv4"
    } else if (Utils.isIPv6(host)) {
        sbiNetConnectedTo = "sbi-default-ipv6"
    }
    paramMap["sbiNetConnectedTo"] = sbiNetConnectedTo


    if (NE_CERTIFICATES != '') {
        paramMap["tlsHostNameCheck"]="False"
        paramMap["tlsCA"] = "<![CDATA[${NE_CERTIFICATES}]]>"
    }

    println 'integration plan is generated from jenkins parameters'
    return genPlan(paramMap, paramOptionalMap)
}

def genPlan(Map<String, String> paramMap, Map<String, String> paramOptionalMap) {
    def param = paramMap.findAll { !(it.key in ['class', 'distName', 'version']) }
    def validPo = paramOptionalMap.findAll { it.value != null }
    println param
    println validPo

    def dn = "${paramMap.distName}".split('/')[0]
    def planName = "chengdu-${dn}-integration-plan"
    def logTime = Utils.getDate()
    // linearized xml
    def raml = "<raml xmlns=\"raml21.xsd\" version=\"2.1\">" +
            "<cmData type=\"plan\" scope=\"all\" name=\"${planName}\">" +
            "<header>" +
            "<log dateTime=\"${logTime}\" action=\"created\" />" +
            "</header>" +
            "<managedObject class=\"${paramMap.class}\" distName=\"${paramMap.distName}\" operation=\"create\" version=\"${paramMap.version}\">"

    param.each { k, v ->
        raml += "<p name=\"${k}\">${v}</p>"
    }
    validPo.each { k, v ->
        raml += "<p name=\"${k}\">${v}</p>"
    }

    raml += "</managedObject></cmData></raml>"

    println raml
    return [planName, raml]
}

def getNeFromPool(neType, neRelease, conf) {
    for (ne in conf.NE) {
        if (ne.TYPE == neType && ne.RELEASE == neRelease) {
            return ne
        }
    }
    return null
}

def getNeTypeReleaseFromPackageProperties(String packageLink) {
    def getPropertiesCmd = "curl -ksX GET ${packageLink}?properties"
    def propertiesJson = Utils.shCmd(getPropertiesCmd, "Get Fp package properties");
    echo "$propertiesJson"
    def neInfo = Utils.parseJson(propertiesJson).properties.neInfo;
    def neType = ""
    def neRelease = ""
    if (neInfo) {
        def neInfoStrs = neInfo[0].split(',')
        for (info in neInfoStrs) {
            if (info.startsWith('neTypeShort')) {
                neType = info.split(':')[1]
            } else if (info.startsWith('neRelease')) {
                neRelease = info.split(':')[1]
            }
        }
    }
    return [neType, neRelease]
}

def checkCCTF(CCTF) {
    def baseUrl = "https://${CCTF.FQDN}/cctf/api"
    def cctfHealthCheckApi = "curl -sk ${baseUrl}/system/healthCheck --connect-timeout 10 -m 30 --retry 3 --retry-delay 5"
    def healthCheckResult = Utils.shCmd(cctfHealthCheckApi, "Check CCTF status")
    def healthCheckResultJson = Utils.parseJson(healthCheckResult)
    if (healthCheckResultJson.status != "Normal operation") {
        error("CCTF health check failed with status: ${healthCheckResultJson.status}")
    }
    echo "CCTF healthCheck status:${healthCheckResultJson.status}"
}

//}
return this
