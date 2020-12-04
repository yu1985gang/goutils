import groovy.transform.Field

@Field private Utils = null

echo "Loaded class Precheck.groovy"

Utils = load "${env.WORKSPACE}/scripts/Utils.groovy"
//class Precheck {

def validateHost(paramMap, NE, CCTF) {
    //Ping NOM_BASE_DOMAIN, NE_HOST and CCTF_FQDN
    def neHost = NE.FQDN
    if (paramMap['host']) {
        neHost = paramMap['host']
        echo "Got ne host from parameter: NE_HOST=${neHost}"
    }
    def addressMap = ["NOM_BASE_DOMAIN": NOM.NOM_BASE_DOMAIN, "NE_HOST": neHost, "CCTF_FQDN": CCTF.FQDN]
    addressMap.each { k, v ->
        if (v != null && "${v}".trim() != "") {
            pingAddress(v)
            echo "Ping ${k} successfully"
        } else {
            error("Invalid conf, ${k}=${v}")
        }
    }
}

def pingAddress(String address,int account = 3){
    def rc = sh script:"ping -c ${account} ${address}", returnStatus:true,label:"Ping address"
    if(rc != 0){
        error("ping ${address} timeout, please check")
    }
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

def validateAndGenPlan(paramMap, paramOptionalMap, conf, fpPackageLink) {
    def allParametersFilled = paramMap.every { _, v ->
        v != null && "${v}".trim() != ""
    }
    def allParametersNotFilled = paramMap.every { _, v ->
        v == null || "${v}".trim() == ""
    }
    if (allParametersFilled) {
        println 'integration plan is generated from jenkins parameters'
        return genPlan(paramMap, paramOptionalMap)
    } else if (allParametersNotFilled) {
        (neType, neRelease) = getNeTypeReleaseFromPackageProperties(fpPackageLink)
        if (!neType) {
            error("Cannot get NE type from Fast Pass package properties for picking up NE from pool.")
        }
        if (!neRelease) {
            error("Cannot get NE release from Fast Pass package properties for picking up NE from pool.")
        }
        def ne = getNeFromPool(neType, neRelease, conf)
        if (!ne) {
            error("Cannot find NE from pool based on given NE type: ${neType}, NE release: ${neRelease}")
        }

        paramMap = ne.INTEGRATION_PARAMS.mandatory
        paramOptionalMap = ne.INTEGRATION_PARAMS.optional
        def allParametersConfigured = paramMap.every { _, v ->
            v != null && "${v}".trim() != ""
        }
        if (!allParametersConfigured) {
            error('All mandatory parameters must be configured: ' + paramMap)
//          throw new Exception("All mandatory parameters must be configured: ${paramMap}")
        }
        println 'integration plan is generated from conf'
        return genPlan(paramMap, paramOptionalMap)
    } else {
        error('All mandatory parameters (like NE_*) must be filled')
//      throw new Exception("All mandatory parameters (like NE_*) must be filled")
    }
}

def genPlan(Map<String, String> paramMap, Map<String, String> paramOptionalMap) {
    def param = paramMap.findAll { !(it.key in ['class', 'distName', 'version']) }
    def validPo = paramOptionalMap.findAll { it.value != null }
    println param
    println validPo

    def dn = "${paramMap.distName}".split('/')[0]
    def planName = "chengdu-${dn}-integration-plan"
//        INTEGRATION_PLAN_NAME = planName
    def logTime = Utils.getDate()
    // formatted xml
/***
 def raml = "<raml xmlns=\"raml21.xsd\" version=\"2.1\">\n" +
 "  <cmData type=\"plan\" scope=\"all\" name=\"${planName}\">\n" +
 "    <header>\n" +
 "      <log dateTime=\"${logTime}\" action=\"created\" />\n" +
 "    </header>\n" +
 "    <managedObject class=\"${paramMap.class}\" distName=\"${paramMap.distName}\" operation=\"create\" version=\"${paramMap.version}\">\n"

 param.each { k, v ->
 raml += "      <p name=\"${k}\">${v}</p>\n"}validPo.each { k, v ->
 raml += "      <p name=\"${k}\">${v}</p>\n"}raml += "    </managedObject>\n" +
 "  </cmData>\n" +
 "</raml>\n"
 ***/
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
//    println "serialized: \n" + groovy.xml.XmlUtil.serialize(raml)
//        INTEGRATION_PLAN = raml
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

def checkCCTF(CCTF){
    def baseUrl = "https://${CCTF.FQDN}/cctf/api"
    def cctfHealthCheckApi = "curl -sk ${baseUrl}/system/healthCheck --connect-timeout 10 -m 30 --retry 3 --retry-delay 5"
    def healthCheckResult = Utils.shCmd(cctfHealthCheckApi,"Check CCTF status")
    def healthCheckResultJson = Utils.parseJson(healthCheckResult)
    echo "healthCheckResultJson.status is ${healthCheckResultJson.status}"
    if (healthCheckResultJson.status != "Normal operation" ){
            error("CCTF Health check status: ${healthCheckResultJson.status}")
    }
}

//}
return this
