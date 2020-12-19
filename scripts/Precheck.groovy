import groovy.transform.Field

@Field private Utils = null
@Field private DnValidator = null
@Field private selectedNE = null
@Field private Conf = null
@Field private GET_LATEST_FP_PACKAGE = "%s/api/storage/netact-fast-pass-release-local/fast_pass_packages/%s?lastModified"

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

    ["NOM_BASE_DOMAIN": NOM.NOM_BASE_DOMAIN, "CCTF_FQDN": CCTF.FQDN,"NOM_EDGE_NODE_HOST":NOM.NOM_EDGE_NODE_HOST, "NOM_SSH_USERNAME":NOM.NOM_SSH_USERNAME,"NOM_SSH_KEY_FILE":NOM.NOM_SSH_KEY_FILE].each{k,v->
        if ( v == null | v.trim() == ""){
            error("Invalid conf,${k}=${v}")
        }
    }

    //def pingAddressData = ["NOM_BASE_DOMAIN": NOM.NOM_BASE_DOMAIN, "CCTF_FQDN": CCTF.FQDN]
    //ping NOM_BASE_DOMAIN and CCTF
    ["NOM_BASE_DOMAIN": NOM.NOM_BASE_DOMAIN, "CCTF_FQDN": CCTF.FQDN].each { _, v ->
        pingAddress(v)
        echo "Ping ${v} successfully"
    }

    //ping NE IP or DNS in NOM
    ["NE_HOST": neHost].each { k, v ->
        pingNE(v,genSshKeyFile("node.pem"),NOM.NOM_SSH_USERNAME,NOM.NOM_EDGE_NODE_HOST)
        echo "Ping ${k} successfully"
    }
}


def pingAddress(String address) {
    // execute ping in local server
    def res = sh script: "ping -c3 ${address}", returnStatus: true, label: "Ping address"
    if (res != 0) {
        error("ping address ${address} timeout, please check")
    }
}  

// def pingAddress(String address) {
//     // execute ping in local server
//     def res = ""
//     if ( Utils.isIPv4(address) || isIPv4DNS(address)){
//         res = sh script: "ping -c3 ${address}", returnStatus: true, label: "Ping ipv4 address"
//     }else if (Utils.isIPv6(address) || isIPv6DNS(address)){
//         def intf = Utils.shCmd("netstat -rn | grep '^0.0.0.0' | rev | cut -d ' '  -f1 | rev").trim().replaceAll("(\\r|\\n)", "")
//         res = sh script: "ping6 -I ${intf} -c3 ${address}", returnStatus: true, label: "Ping ipv6 address"
//     } else {
//         error("Address is neigther IP or FQDN: ${address}")
//     }
//     if (res != 0) {
//         error("ping address ${address} timeout, please check")
//     }
// }

def pingNE(String address, String sshKey, String sshUerName, String remoteIp) {
    //login in and ping address in remote server, e.g, ping NE_HOST in NOM
    def isIPv4Addr = Utils.isIPv4(address)
    def isIPv6Addr = Utils.isIPv6(address)
    def isIPv4Dns = isDnsIPv4(address,sshKey,sshUerName,remoteIp)
    def isIPv6Dns = isDnsIPv6(address,sshKey,sshUerName,remoteIp)
    def pingIPv4Res,pingIPv6Res

    if (!isIPv4Addr && !isIPv6Addr && !isIPv4Dns && !isIPv6Dns){
        error("NE_HOST is neigther IP nor DNS: ${address}")
    }

    if (isIPv4Addr || isIPv4Dns){
        pingIPv4Res = sh script: "ssh -i ${sshKey} ${sshUerName}@${remoteIp} ping -c3 ${address}", returnStatus: true, label: "Ping ipv4 address"
        if(pingIPv4Res!=0){
            error("Ping NE address ${address} timeout, please check")
        }
    }

    if (isIPv6Addr || isIPv6Dns){
        //intf = Utils.shCmd("netstat -rn | grep '^0.0.0.0' | rev | cut -d ' '  -f1 | rev").trim().replaceAll("(\\r|\\n)", "")
        routeIface = Util.shCmd("ssh -i ${sshKey} ${sshUerName}@${remoteIp} netstat -rn | grep '^0.0.0.0' | rev | cut -d ' '  -f1 | rev")
        echo "******************routeIface is $routeIface****************************"
        pingIPv6Res = sh script: "ssh -i ${sshKey} ${sshUerName}@${remoteIp} ping6 -I ${routeIface} -c3 ${address}", returnStatus: true, label: "Ping ipv6 address"
        if(pingIPv6Res!=0){
            error("Ping NE address ${address} timeout, please check")
        }

    }
}

def genSshKeyFile(String fileName){
    Utils.shCmd("rm -rf node.pem")
    writeFile file: "node.pem", text: "${Conf.NOM[0].NOM_SSH_KEY_FILE}"
    if(!fileExists("node.pem")){
        error("Generate ssh key file failed")
    }else{
        Utils.shCmd("chmod 400 node.pem","Set ssh key file as read-only permission")
        return fileName
    }
}

// def isIPv4DNS(String fqdn){
//     def dnsIPv4Cfg = sh script: "host ${fqdn} |grep -i 'has address'",returnStatus:true
//     if (Utils.isIPv4(fqdn) || Utils.isIPv6(fqdn)){
//         return false
//     }
//     return dnsIPv4Cfg == 0 
// }


def isDnsIPv4(String fqdn,String sshKey, String sshUerName, String remoteIp){
    def dnsIPv4Cfg = sh script:"ssh -i ${sshKey} ${sshUerName}@${remoteIp} host ${fqdn} |grep -i 'has address'",returnStatus:true
    if (Utils.isIPv4(fqdn) || Utils.isIPv6(fqdn)){
        return false
    }
    return dnsIPv4Cfg == 0
}


// def isIPv6DNS(String fqdn){
//     def dnsIPv6Cfg = sh script: "host ${fqdn} |grep -i 'has IPv6 Ipaddress'",returnStatus:true
//     if (Utils.isIPv4(fqdn) || Utils.isIPv6(fqdn)){
//         return false
//     }
//     return dnsIPv6Cfg == 0
// }

def isDnsIPv6(String fqdn,String sshKey, String sshUerName, String remoteIp){
    def dnsIPv6Cfg = sh script: "ssh -i ${sshKey} ${sshUerName}@${remoteIp} host ${fqdn} |grep -i 'has IPv6 address'",returnStatus:true
    if (Utils.isIPv4(fqdn) || Utils.isIPv6(fqdn)){
        return false
    }
    return dnsIPv6Cfg == 0
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

def CCTFHealth(CCTF) {
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
