

def filePath = "C:\\NOM\\neo0033\\node.pem"
File file = new File(filePath)

def filePath2 = "C:\\NOM\\neo0033\\node2.pem"
File fileWriter = new File(filePath2)






def list = file.readLines()


file.eachLine {line ->
	fileWriter.append(line)
}


print fileWriter.text


