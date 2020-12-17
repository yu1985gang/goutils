def filePath = "C:\\NOM\\neo0033\\node.pem"
File fileReader = new File(filePath)


def list = fileReader.readLines()
print list.join('\n')







def filePath2 = "C:\\NOM\\neo0033\\node2.pem"
File fileWriter = new File(filePath2)


