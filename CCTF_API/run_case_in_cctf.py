from test_with_cctf import CCTFProject,format_test_parameters
def run_case_in_cctf(CCTF_FQDN, CCTF_USER_NAME,CCTF_PASSWORD,test_product_name,COMMON_TC_PACKAGE_URL,COMMON_TC_DOCKER_IMAGE, \
                     TP_BASE_DOMAIN,TP_NOM_OPEN_API_GW, TP_NOM_OPEN_API_USERNAME,TP_NOM_OPEN_API_PASSWORD, ne_protocol,INTEGRATED_ID, NE_METADATA_FASTPASS_PACKAGE_LINK, \
                     NE_TC_PACKAGE_URL, NE_TC_DOCKER_IMAGE_URL, NE_TC_PARAMETERS, test_tag,test_log_file_name):
    test = CCTFProject(CCTF_FQDN, CCTF_USER_NAME, CCTF_PASSWORD, test_product_name,'DEBUG')
    test.create_lab('remote', COMMON_TC_PACKAGE_URL,'Common_Test_Cases',COMMON_TC_DOCKER_IMAGE)
    common_test_parameters="TP_BASE_DOMAIN="+TP_BASE_DOMAIN+ \
                    ",TP_NOM_OPEN_API_GW="+TP_NOM_OPEN_API_GW+ \
                    ",TP_NOM_OPEN_API_USERNAME="+TP_NOM_OPEN_API_USERNAME+ \
                    ",TP_NOM_OPEN_API_PASSWORD="+TP_NOM_OPEN_API_PASSWORD+ \
                    ",PROTOCOL=" + ne_protocol + \
                    ",INTEGRATED_ID="+INTEGRATED_ID+ \
                    ",METADATA_FASTPASS_PACKAGE_LINK=" + NE_METADATA_FASTPASS_PACKAGE_LINK
    test.update_package_parameters('Common_Test_Cases', format_test_parameters(common_test_parameters))
    if NE_TC_PACKAGE_URL.strip() != '' and NE_TC_DOCKER_IMAGE_URL.strip() != '':
        test.create_lab('remote', NE_TC_PACKAGE_URL,'Customized_Test_Cases',NE_TC_DOCKER_IMAGE_URL)
        if NE_TC_PARAMETERS.strip() != '':
            ne_test_parameters = common_test_parameters + "," + NE_TC_PARAMETERS.strip()
        else:
            ne_test_parameters = common_test_parameters
        test.update_package_parameters('Customized_Test_Cases', format_test_parameters(ne_test_parameters))
    test.execute_tagged_testcases_in_project(test_tag)
    test.get_execution_logs_by_jobid(test_log_file_name)
    test.delete_labs()
    test.delete_project()