import groovy.transform.Field

@Field private Utils = null

echo "Loaded class DownloadFP.groovy"

def checkFpLink(String fpPackageLink) {
    echo "Check FP package link"
    if (!fpPackageLink) {
        error('FP package link is empty.')
    }
}

def downloadFp(String fpPackageLink) {
    echo "DownloadFP FP package"
    timeout(time:15, unit: 'MINUTES') {
        sh script:"curl -x '' ${fpPackageLink} -OJ"
    }
}

def validateFp(String fpPath) {
    echo "Check whether the Fast Pass package is a valid zip"
    def isZip = unzip(zipFile:fpPath, test:true)
    if (!isZip) {
        error("Invalid Fass Pass ZIP package: ${fpPath}")
    }
}

def removeFp(String fpPath) {
    if (fpPath.trim() != "") {
        def rc = sh script: "test -f ${fpPath}", returnStatus: true
        if (rc == 0) {
            echo "Remove downloaded FP package: ${fpPath}"
            sh script: "rm -f ${fpPath}"
        }
    }
}

return this
