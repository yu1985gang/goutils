def filePath = "C:\\git\\goutils\\configuration\\syve.yaml"
//def filePath2 = "C:\\NOM\\neo0033\\node2.pem"

print new File(filePath)

def datas  = readYaml(file: filePath)
print datas



// def list = fileReader.readLines()
// print list.join('\n')







// def filePath2 = "C:\\NOM\\neo0033\\node2.pem"
// File fileWriter = new File(filePath2)


