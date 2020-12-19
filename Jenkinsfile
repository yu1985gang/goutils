pipeline {
    agent any
    options {
        timeout(time: 4, unit: 'HOURS')
        timestamps()
    }
    parameters {
        string(
            name: 'NE_SW_ID',
            description: '''NE software ID, for example, REGSTR_21.0.0_100025.''',
            trim: true)
        choice(name: 'NE_MO_CLASS_ID',
                choices: ['com.nokia.nms.integration:NE3SINT', 'com.nokia.nms.integration:MRBTSINT', 'com.nokia.nms.integration:SMMINT'],
                description: '''Managed object class ID.''')
        string(
            name: 'NE_DIST_NAME',
            defaultValue: '',
            description: '''Distinguished Name (DN) of the managed object, for example, REGSTR-3333.''',
            trim: true)
        string(
            name: 'NE_HOST',
            defaultValue: '',
            description: '''NE IP or FQDN.''',
            trim: true)
        string(
            name: "NE_PORT",
            defaultValue: '',
            description: '''NE port.''',
            trim: true)
        string(
            name: "NE_USER_NAME",
            defaultValue: '',
            description: '''NE user name''',
            trim: true)
        password(
            name: "NE_PASSWORD",
            description: '''NE password''')
        string(
            name: "CUSTOM_INTEGRATION_PARAMS",
            defaultValue: '',
            description: '(Optional) Key value pairs for custom integration parameters, for example, "elementManagerURL=abc.net,sbiSwDownloadEndpoint=def.net"',
            trim: true)
        text(name: 'NE_CERTIFICATES',
             defaultValue: '',
             description: 'if the NE has TLS enabled, this parameter is mandatory and contains the TLS CA certificate of NE which Nokia Network Operations Master needs to trust')
        string(
            name: "NE_TC_PACKAGE_URL",
            defaultValue: '',
            description: '(Optional) NE specific test cases package URL.',
            trim: true)
        string(
            name: "NE_TC_DOCKER_IMAGE_URL",
            defaultValue: '',
            description: '''\
(Optional) The docker image URL, for example, myregistry.local:5000/testing/test-image.
This image is used for running test cases.''',
            trim: true)
        string(
            name: "NE_TC_PARAMETERS",
            defaultValue: '',
            description: '(Optional) Key value pairs for NE specific test case parameters, for example, "myPara1=value1, myPara2=value2".',
            trim: true)
    }

    environment {
        PIPELINE = ""
    }

    stages {
        stage('Precheck') {
            steps {
                script {
                    PIPELINE = load "${env.WORKSPACE}/scripts/Pipeline.groovy"

                    PIPELINE.preCheck(NE_SW_ID, NE_MO_CLASS_ID, NE_DIST_NAME, NE_HOST, NE_PORT, NE_USER_NAME, NE_PASSWORD, CUSTOM_INTEGRATION_PARAMS,
                        NE_TC_PACKAGE_URL, NE_TC_DOCKER_IMAGE_URL, NE_TC_PARAMETERS, NE_CERTIFICATES)
                }
            }
        }
        stage('DownloadFP FP package'){
            steps {
                script {
                    PIPELINE.downloadFpPackage()
                }
            }
        }
        stage('Deploy FP package'){
            steps {
                script {
                    PIPELINE.deployPackage()
                }
            }
        }
        stage('Integrate NE'){
            steps {
                script {
                    PIPELINE.integrateNE()
                }
            }
        }
        stage('Run Test Case'){
            steps {
                script {
                    PIPELINE.exeTestCase()
                }
            }
        }
    }

    post {

    }
}

