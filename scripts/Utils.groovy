echo "Loaded class Utils.groovy"

def shCmd(String cmd, String label = "") {
    return sh(script: cmd, returnStdout: true)
}

def aCurl() {
    return """curl -k -x "" --connect-timeout 10 -m 30 --retry 3 --retry-delay 5 """
}

def httpCurl() {
    return """curl -s -o /dev/null -w "%{http_code}" -k -x "" --connect-timeout 10 -m 30 --retry 3 --retry-delay 5 """
}

def parseJson(String json) {
    try {
        return readJSON(text: json)
    } catch (Exception e) {
        error("Fail to parse data to json: ${json}\nException: ${e}")
        return ""
    }
}

def getDate() {
    //dateTime="2020-11-04T10:22:37+03:00"
    return new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", TimeZone.getTimeZone("GMT+08:00"))

}

def isIPv4(String addr) {
    echo "isIPv4 called"
    if (addr.contains(".")) {
        String[] items = addr.split("\\.")
        try {
            for (String item : items) {
                Integer.parseInt(item)
            }
            return true
        } catch (NumberFormatException e) {
            // do nothing
        }
    }
    return false
}

def isIPv6(String addr) {
    return addr.contains(":")
}


def isIPv4Fqdn(String fqdn, String sshKey="", String sshUerName ="", String remoteIp = ""){
    if (isIPv6(addr) || isIPv4(addr){
        return false
    }
    def cmd=""
    if ((sshUerName.trim() == "") || (remoteIp.trim() == "") ) {
        cmd = "host ${fqdn} |grep -i -c 'has address'"
    } else{
        cmd = "ssh -i ${sshKey} ${sshUerName}@${remoteIp} host ${fqdn} |grep -i -c 'has address' "
    }
    def rt = shCmd(cmd).trim().replaceAll("(\\r|\\n)", "")
    print "rt type is ${rt.getClass()}"
    print "rt value is ${rt}"
    try{
        rc = Integer.parseInt(rt)
    }catch (NumberFormatException e) {
            // do nothing
        }
    //rt = sh script: cmd,returnStdout: true
    //rt = rt.trim().replaceAll("(\\r|\\n)", "")
    print rt==1
    return rt == 1
}

def isIPv6Fqdn(String fqdn, String sshKey="", String sshUerName ="", String remoteIp = ""){
    if (isIPv6(addr) || isIPv4(addr){
        return false
    }
    echo "===> isIPv6Fqdn"
    def cmd = ""
    if ((sshUerName.trim() == "") && (remoteIp.trim() == "") ) {
        cmd = "host ${fqdn} |grep -i -c 'has address'"
    } else{
        cmd = "ssh -i ${sshKey} ${sshUerName}@${remoteIp} host ${fqdn} |grep -i -c 'has IPv6 address' "
    }
    def rt = shCmd(cmd).trim().replaceAll("(\\r|\\n)", "")
    //rt = sh script: cmd,returnStdout: true
    //rt = rt.trim().replaceAll("(\\r|\\n)", "")
    try{
        rt = Integer.parseInt(rt)
    }catch (NumberFormatException e){
        // do nothing
    }
    return rt == 1
}

return this
