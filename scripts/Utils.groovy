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


def readNodePem(){
    def pemFile  = "${env.WORKSPACE}/configuration/node.pem"
    def invertoryFilePath  = "${env.WORKSPACE}/configuration/inventory.yaml"

    def readInvertoryFile()
    read


    
}

def yamlFile(string file){
    def datas=""
    if(file.endWith("yml") || file.endWith("yaml")){
        datas = readYaml file : yaml_file
    }else{

    }
}


return this
