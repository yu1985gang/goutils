import java.util.regex.Pattern

class DnValidator {
    // DN specification
    def dnMaxLength = 400
    def classAbbreviationMaxLength = 30
    def instanceIdMaxLength = 80
    //
    def alpha_char = "[a-zA-Z]"
    def alphanumeric_char = "[a-zA-Z]|\\d|_"
    //    def additional_char="[\\s~!@#\\$\\^\\&\\*\\(\\)\\-\\+_=\\[\\]\\{\\}\\|;:,\\.\\<\\>\\?\\\\]"
    def additional_char = "[\\\\s~!@#\\\\\$\\\\^\\\\&\\\\*\\\\(\\\\)\\\\-\\\\+_=\\\\[\\\\]\\\\{\\\\}\\\\|;:,\\\\.\\\\<\\\\>\\\\?\\\\\\\\]"
    def unicode_BMP_char = "[\\u00A0-\\uD7FF[\\uF900-\\uFFFD]]"
    def additional_unicode_BMP_char = "[\\u0020\\u0021\\u0023\\u0024\\u0026\\u0028\\u0029\\u002A\\u002B\\u002C\\u002D\\u002E\\u003A\\u003B\\u003D\\u003F\\u0040\\u005B\\u005C\\u005D\\u007B\\u007C\\u007D\\u007E]"
    //
    def classAbbreviation = "${alpha_char}(${alphanumeric_char})+"
    def objectInstance = "(${alphanumeric_char}|${additional_char}|${unicode_BMP_char}|${additional_unicode_BMP_char})+"

    def validateDistName(String distName) {
        println("start validate distName: ${distName}")
        if (distName == null) {
            return false
        }
        distName = distName.trim()
        if (distName.length() == 0 || distName.length() > dnMaxLength) {
            return false
        }
        // "RNC-1/WBTS-1/WCEL-1"
        def elements = distName.split("/")

        Pattern classAbbreviationPattern = Pattern.compile(classAbbreviation)
        Pattern objectInstancePattern = Pattern.compile(objectInstance)
        for (String e : elements) {
            def items = e.split("-", 2)
            if (items.length != 2) {
                return false
            }

            String classAbbr = items[0]
            if (classAbbr.length() > classAbbreviationMaxLength) {
                return false
            }
            if (!classAbbreviationPattern.matcher(classAbbr).matches()) {
                return false
            }

            String objectInst = items[1]
            if (objectInst.length() > instanceIdMaxLength) {
                return false
            }
            if (!objectInstancePattern.matcher(objectInst).matches()) {
                return false
            }
        }
        return true
    }
}

return new DnValidator()