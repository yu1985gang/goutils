import groovy.transform.Field

@Field private Utils = null
@Field private Set<String> ALL_SUPPORTED_FEATURES = ["CM", "FM", "PM"]

Utils = load "${env.WORKSPACE}/scripts/Utils.groovy"

//1. All features should be supported are defined in variable ALL_SUPPORTED_FEATURES
//2. Return supported features with "," as the splitter. For example:FM,PM
//3. Return empty string when no supported features found or exception happens.
def getSupportedFeaturesString(String fastPassPackagePath) {
    try {
        def supportedFeaturesString = getSupportedFeatures(fastPassPackagePath).join(",")
        echo "Get all supported features: ${supportedFeaturesString} from FP package ${fastPassPackagePath}"
        return supportedFeaturesString
    } catch (Exception e) {
        // Return empty string when exception happens to avoid breaking pipeline
        echo "Failed to get supported features from FP package ${fastPassPackagePath} due to exception: ${e}"
        return ""
    }
}

def getSupportedFeatures(String fastPassPackagePath) {
    if (isMultiplePackage(fastPassPackagePath)) {
        return getSupportedFeaturesFromMultiplePackage(fastPassPackagePath)
    } else {
        return getSupportedFeaturesFromSinglePackage(fastPassPackagePath)
    }
}

def isMultiplePackage(String fastPassPackagePath) {
    return Utils.shCmd("unzip -Z1 ${fastPassPackagePath}", "Get FP package structure")
                .contains("multiplePackages/")
}

def getSupportedFeaturesFromSinglePackage(String singlePackagePath) {
    echo "Start to get supported features from single FP package ${singlePackagePath}"
    def bomFileMap = unzip(zipFile: "${singlePackagePath}", glob: "files/BOM/FastPass/**/*.json", read: true)
    if (!containsSingleBomFile(bomFileMap)) {
        return []
    }

    def supportedFeatures = Utils.parseJson(bomFileMap.values().iterator().next()).fragments
        .collect { fragment -> fragment.fragmentId == null ? "" : fragment.fragmentId.toUpperCase() }
        .findAll { fragmentId -> ALL_SUPPORTED_FEATURES.contains(fragmentId) }
        .unique()
    echo "Find supported features: ${supportedFeatures} from single FP package ${singlePackagePath}"
    return supportedFeatures
}

def getSupportedFeaturesFromMultiplePackage(String multiplePackagePath) {
    echo "Start to get supported features from multiple FP package ${multiplePackagePath}"
    def baseDirName = nameWithRandomString(multiplePackagePath)
    def tmpBaseDir = "${env.WORKSPACE}/${baseDirName}"
    echo "Uncompress multiple FP package to ${tmpBaseDir}"
    try {
        unzip(zipFile: "${multiplePackagePath}", dir: "${tmpBaseDir}", glob: "multiplePackages/*.zip")
        return getSupportedFeaturesFromMultiplePackageDirectory("**/${baseDirName}/multiplePackages/*.zip")
    } finally {
        removeDirectory(tmpBaseDir)
    }
}

def getSupportedFeaturesFromMultiplePackageDirectory(def multiplePackageDirectoryGlob) {
    def supportedFeatures = []
    for (def singlePackage in findFiles(glob: multiplePackageDirectoryGlob)) {
        try {
            supportedFeatures.addAll(getSupportedFeaturesFromSinglePackage(singlePackage.path))
        } catch (Exception e) {
            echo "Skip parsing single package ${singlePackage.path} due to exception: ${e}"
        }
        if (supportedFeatures.containsAll(ALL_SUPPORTED_FEATURES)) {
            echo "FP package supports all features, skip reading left single FP packages"
            break
        }
    }
    return supportedFeatures.unique()
}

def containsSingleBomFile(def bomFileMap) {
    if (bomFileMap.size() == 0) {
        echo "BOM file not found"
        return false
    } else if (bomFileMap.size() > 1) {
        echo "More than one BOM file found"
        echo "${bomFileMap.keySet()}"
        return false
    }
    return true
}

def nameWithRandomString(String multiplePackage) {
    return multiplePackage.substring(0, multiplePackage.lastIndexOf(".")) + "_" + randomString()
}

def randomString() {
    return UUID.randomUUID().toString().substring(0,4)
}

def removeDirectory(String dir) {
    if (dir) {
        Utils.shCmd("rm -rf ${dir}", "Remove directory ${dir}")
    }
}

return this