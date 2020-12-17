import os
import json
import time
import requests
from datetime import datetime

import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

from logger import set_logger_log_level, LOGGER

API_ENDPOINTS = {
    "login": "/login/",
    "project": "/project/",
    "lab": "/project/instance/"
}

TEST_GROUP_LIST = ["LCO", "FUNCTIONAL", "REGRESSION", "PERFORMANCE", "OTHERS"]

class CCTFInternalResponseError(Exception):
    def __init__(self):
        Exception.__init__(self, 'Internal response error.')

class CCTFBadResponseError(Exception):
    def __init__(self, code):
        self.code = code
        Exception.__init__(self, 'Bad Response, status code is [%s].' % code)

class LocalTestPackageMissing(Exception):
    def __init__(self, package_location):
        self.package_location = package_location
        Exception.__init__(self, 'Local Package [%s] was not found.' % package_location)

class WaitExpired(Exception):
    def __init__(self, desired_status, wait_interval, total_attempts):
        self.desired_status = desired_status 
        self.wait_interval = wait_interval
        self.total_attempts = total_attempts
        Exception.__init__(self, "Status did not become [{}] after [{}] attempts with [{}] interval!".format(desired_status, str(total_attempts), str(wait_interval)))

class CCTFProject(requests.Session):

    def __init__(self, ip, user, password, project_name, log_level, max_tc_minute_duration=None):
        super(CCTFProject, self).__init__()
        if max_tc_minute_duration is None:
            max_tc_minute_duration = 10
        self.max_tc_minute_duration = max_tc_minute_duration

        set_logger_log_level(log_level)
        LOGGER.debug("CCTF client initializing...")
        self.base_url = "https://" + ip + "/cctf/api"
        self.auth_data = {'username':user, 'password':password}
        self.SSOloginUrl = self.base_url + API_ENDPOINTS["login"]
        self.session = self.__login()
        LOGGER.debug("CCTF client initialized...")
        self.__project_id = 0
        self.__lab_id = 0
        self.__lab_id_list = []
        self.__package_id = 0
        self.__package_id_list = []
        self.__job_id = 0
        self.__job_id_list = []
        self.__pkgName_labId_dict = {}
        self.tc_result_list = []
        cctf_versions = self.__get_cctf_version()
        self.cctf_chart_version = cctf_versions["ChartVersion"]
        self.cctf_version = cctf_versions["CCTFVersion"]
        LOGGER.info("CCTF Chart version is:[%s]", self.cctf_chart_version)
        LOGGER.info("CCTF Software version is:[%s]", self.cctf_version)
        self.__create_project(project_name)

    def __login(self):
        LOGGER.debug("CCTF client authenticating..")
        resp = self.post(self.SSOloginUrl, json=self.auth_data, verify=False)
        self.__check_response(resp)
        LOGGER.debug("CCTF client authenticated..")
        return resp

    def execute_test_case_by_test_title(self, test_title):
        self.tc_result_list = []
        self.__execute_test_case(self.__find_testcase_in_lab("title", test_title))

    def execute_test_case_by_test_id(self, test_id):
        self.tc_result_list = []
        self.__execute_test_case(self.__find_testcase_in_lab("testId", test_id))

    def execute_all_testcases_in_group(self, test_group):
        self.tc_result_list = []
        self.__execute_test_case(self.__find_all_testcases_in_group(test_group))
        #for test_case in self.__get_group_testcases(test_group):
        #    self.__execute_test_case_by_test_id_keep(test_case["testId"])

    def execute_all_testcases_in_package(self):
        self.tc_result_list = []
        for test_case in self.__get_all_testcases():
            self.__execute_test_case_by_test_id_keep(test_case["testId"])

    def execute_tagged_testcases_in_project(self, requested_tag=""):
        self.tc_result_list = []
        for lab_id in self.__lab_id_list:
            self.__lab_id = lab_id
            self.__execute_test_case(self.__find_testcases_by_tag(requested_tag))

    def create_lab(self, location_type, package_location, package_name, docker_image):
        if location_type == "local":
            self.__check_file(package_location)
            files = {'myfile':open(package_location, 'rb')}
            resp = self.post(self.base_url + "/file/", files=files, verify=False)
            self.__check_response(resp)
            resp_body = json.loads(resp.text)
            if not resp_body['res']:
                self.__create_remote_lab(resp_body['obj']['fileUrl'], package_name, docker_image)
            else:
                raise CCTFInternalResponseError()
        elif location_type == "remote":
            self.__create_remote_lab(package_location, package_name, docker_image)
        else:
            LOGGER.error("Unable to create lab. Location type must be either [local] or [remote].")
            raise RuntimeError("Unable to create lab. Location type must be either [local] or [remote].")
        self.__pkgName_labId_dict.update({package_name:self.__lab_id})

    def delete_project(self):
        self.__check_response_info(self.__delete_action(self.base_url + API_ENDPOINTS["project"] + str(self.__project_id)))
        self.__project_id = 0

    def delete_lab(self):
        self.__check_response_info(self.__delete_action(self.base_url + API_ENDPOINTS["lab"] + str(self.__lab_id)))
        self.__lab_id = 0
        self.__package_id = 0

    def delete_labs(self):
        for lab_id in self.__lab_id_list:
            self.__lab_id = lab_id
            self.__check_response_info(self.__delete_action(self.base_url + API_ENDPOINTS["lab"] + str(self.__lab_id)))
        self.__lab_id = 0
        self.__lab_id_list = []
        self.__package_id = 0
        self.__package_id_list = []

    def __get_cctf_version(self):
        return self.__check_response_info(self.__get_action(self.base_url + "/system/version/"))

    def __api_action(self, resp):
        response_info = {
            "status":False,
            "content":""
        }
        self.__check_response(resp)
        resp_body = json.loads(resp.text)
        LOGGER.debug(str(resp_body))
        if not resp_body['res']:
            response_info["status"] = True
            response_info["content"] = resp_body["obj"]
        return response_info

    def __get_action(self, url):
        return self.__api_action(self.get(url, verify=False))

    def __post_action(self, url, request_data):
        return self.__api_action(self.post(url, json=request_data, verify=False))

    def __delete_action(self, url):
        return self.__api_action(self.delete(url, verify=False))

    def __check_file(self, full_path):
        if not os.path.isfile(full_path):
            LOGGER.error("File not found:[%s]", full_path)
            raise LocalTestPackageMissing(full_path)

    def __check_response(self, resp):
        code = resp.status_code
        if not 200 <= code < 400:
            LOGGER.error("Response status code not OK.")
            LOGGER.error(str(resp))
            LOGGER.error("Response:")
            LOGGER.error(resp.json())
            raise CCTFBadResponseError(code)

    def __check_response_info(self, response_info):
        if not response_info["status"]:
            LOGGER.error("Internal response error.")
            raise CCTFInternalResponseError()
        return response_info["content"]

    def __find_testcase_in_lab(self, selector, match_value):
        for test_group in TEST_GROUP_LIST:
            request_data = None
            group_test_case_list = self.__get_group_testcases(test_group)
            for test_case in group_test_case_list:
                if match_value == test_case[selector]:
                    request_data = {
                        "lab":self.__lab_id,
                        "testGroup":test_group,
                        "testId":str(test_case["id"])
                    }
                    break
            if request_data is not None:
                LOGGER.debug("Testcase with %s:[%s] was found in the package used for lab:[%s].", selector, match_value, str(self.__lab_id))
                return request_data

        LOGGER.error("Testcase with %s:[%s] was not found in the package used for lab:[%s].", selector, match_value, str(self.__lab_id))
        return {} 

    def __find_all_testcases_in_group(self, test_group):
        request_data = {}
        group_test_case_list = self.__get_group_testcases(test_group)
        test_id = ""
        for test_case in group_test_case_list:
            test_id = test_id + "," + str(test_case["id"])
        test_id.lstrip()
        request_data = {
                "lab":self.__lab_id,
                "testGroup":test_group,
                "testId":test_id[1:]
                }
        LOGGER.debug("The test_id string is %s", test_id[1:])
        if request_data is not None:
            LOGGER.debug("Testcases under [%s] was found in the package used for lab:[%s].", test_group, str(self.__lab_id))
            return request_data

        LOGGER.error("Testcases under [%s] was not found in the package used for lab:[%s].", test_group, str(self.__lab_id))
        return request_data

    def __find_testcases_by_tag(self, requested_tag_string):
        if requested_tag_string == "":
            tagList = self.__get_all_tags_list()
        else:
            tagList = str(requested_tag_string).split(",")

        request_data = {
                "lab":self.__lab_id,
                "testGroup":"RUNBYTAG",
                "tagList":tagList
                }
        LOGGER.debug("The tags used in test are %s", ",".join(str(tag) for tag in tagList))
        if request_data is not None:
            LOGGER.debug("Testcases filtered by tags are found in the package:[%s] and lab:[%s].", self.__package_id, str(self.__lab_id))
        return request_data

    def __execute_test_case(self, request_data):
        content = self.__check_response_info(self.__post_action(self.base_url + "/job/build/", request_data))
        job_id = content["id"]
        self.__job_id = job_id
        self.__job_id_list.append(job_id)

        LOGGER.debug("Testcase is being executed.")
        self.__wait_until_job_status_is("SUCCESS", job_id)

        LOGGER.debug("Testcase finished execution.")

        self.tc_result_list += self.__get_testcase_result(job_id)

    def __execute_test_case_by_test_id_keep(self, test_id):
        self.__execute_test_case(self.__find_testcase_in_lab("testId", test_id))

    def get_execution_logs(self):
        for tc_result in self.tc_result_list:
            url = self.base_url + "/job/test/log/?jobId=" + str(tc_result["testcase"]["last_jobID"])
            if self.__is_target_downloadable(url):
                resp = self.get(url, verify=False, allow_redirects=True)
                self.__check_response(resp)
                filename = (tc_result["testcase"]["testId"] + "__" + tc_result["testcase"]["title"] + "__" + tc_result["result"] + "__" + str(datetime.now()) + ".zip").replace(" ", "-").replace('\n', "-")
                open(filename, 'wb').write(resp.content)
            else:
                LOGGER.error("Target [%s] is not downloadable.", url)

    def get_execution_logs_by_jobid(self, filename):
        for job_id in self.__job_id_list:
            url = self.base_url + "/job/test/log/?jobId=" + str(job_id)
            if self.__is_target_downloadable(url):
                resp = self.get(url, verify=False, allow_redirects=True)
                self.__check_response(resp)
                open(filename,'ab').write(resp.content)
            else:
                LOGGER.error("Target [%s] is not downloadable.", url)

    def __is_target_downloadable(self, url):
        header = requests.head(url, verify=False, allow_redirects=True).headers
        content_type = header.get('content-type')
        if 'text' in content_type.lower() or 'html' in content_type.lower():
            return False
        else:
            return True 

    def __get_all_testcases(self):
        test_case_list = []
        for test_group in TEST_GROUP_LIST:
            test_case_list += self.__get_group_testcases(test_group)
        return test_case_list

    def __get_tagged_testcases(self, tag):
        return self.__check_response_info(self.__get_action(
            self.base_url + "/test/list/?packageID=" + str(self.__package_id) + "&tag=" + tag))

    def __get_all_tags_list(self):
        tagList = []
        content = self.__check_response_info(self.__get_action(self.base_url + "/tag/?packageID=" + str(self.__package_id)))
        for item in content:
            tagList.append(item.get("name"))
        LOGGER.debug("All tags can be listed as %s",str(tagList))
        return tagList

    def __get_testcase_result(self, job_id):
        return self.__check_response_info(self.__get_action(self.base_url + "/job/test/result/?jobID=" + str(job_id)))

    def __get_group_testcases(self, test_group):
        return self.__check_response_info(self.__get_action(self.base_url + "/test/list/?packageID=" + str(self.__package_id) + "&testGroup=" + test_group))

    def __get_job(self, job_id):
        return self.__check_response_info(self.__get_action(self.base_url + "/job/detail/" + str(job_id) + "/"))

    def __get_lab(self):
        return self.__check_response_info(self.__get_action(self.base_url + API_ENDPOINTS["lab"] + str(self.__lab_id) + "/"))

    def __create_remote_lab(self, package_url, package_name, docker_image):
        request_data = {
            "lab":{
                "name":"AutoLab",
                "ip":"127.0.0.1"
            },
            "package":{
                "name":package_name,
                "url":package_url,
                "dockerimage":docker_image
            }
        }

        content = self.__check_response_info(self.__post_action(self.base_url + API_ENDPOINTS["project"] + str(self.__project_id) + "/instance/", request_data))
        self.__lab_id = content["id"]
        self.__wait_until_lab_status_is("success")
        self.__package_id = self.__get_lab()["package"]["id"]
        self.__lab_id_list.append(self.__lab_id)
        self.__package_id_list.append(self.__package_id)

    def __create_project(self, name):
        if self.__project_id:
            LOGGER.info("Cannot recreate project. Delete current project before creating a new one.")
            return
        content = self.__check_response_info(self.__post_action(self.base_url + API_ENDPOINTS["project"], {"name":name}))
        self.__project_id = content["id"]

    def __wait_until_job_status_is(self, desired_status, job_id):
        current_status = self.__get_job(job_id)["status"]
        wait_interval = 10
        total_attempts = self.max_tc_minute_duration * 60 / wait_interval 
        current_attempt = 0
        while current_attempt < total_attempts:
            LOGGER.debug("The current status for Job with ID: [%s] is: [%s]", str(job_id), current_status)
            LOGGER.debug("The desired state for Job with ID: [%s] is: [%s]", str(job_id), desired_status)
            LOGGER.debug("Status is not matching. Waiting for %s seconds to check again.", wait_interval)
            time.sleep(wait_interval)
            current_status = self.__get_job(job_id)["status"]
            current_attempt += 1
            if current_status == desired_status:
                time.sleep(wait_interval)
                return
        LOGGER.error("Job status did not become [%s] after [%s] attempts with [%s] interval!", desired_status, str(total_attempts), str(wait_interval))
        raise WaitExpired(desired_status, wait_interval, total_attempts)

    def __wait_until_lab_status_is(self, desired_status):
        current_status = self.__get_lab()["status"]
        wait_interval = 3
        total_attempts = 20
        current_attempt = 0
        while current_attempt < total_attempts:
            LOGGER.debug("The current status for Lab with ID: [%s] is: [%s]", str(self.__lab_id), current_status)
            LOGGER.debug("The desired state for Lab with ID: [%s] is: [%s]", str(self.__lab_id), desired_status)
            LOGGER.debug("Status is not matching. Waiting for %s seconds to check again.", wait_interval)
            time.sleep(wait_interval)
            lab = self.__get_lab()
            current_status = lab["status"]
            current_attempt += 1
            if current_status == "failed":
                LOGGER.error("Lab creation failed with reason:[%s]", lab["description"])
                raise CCTFInternalResponseError()
            elif current_status == desired_status:
                return
        LOGGER.error("Lab status did not become [%s] after [%s] attempts with [%s] interval!", desired_status, str(total_attempts), str(wait_interval))
        raise WaitExpired(desired_status, wait_interval, total_attempts)

    def update_package_parameters(self, package_name, parameters_in_json):
        self.__lab_id = self.__pkgName_labId_dict.get(package_name)
        final_parameters = {
            "json":self.__combine_package_parameters(parameters_in_json)
        }
        return self.__check_response_info(self.__post_action(self.base_url + "/lab/editparameter/?labID=" + str(self.__lab_id), final_parameters))

    def __get_package_parameters(self):
        return self.__check_response_info(self.__get_action(self.base_url + "/lab/editparameter/?labID=" + str(self.__lab_id)))

    def __combine_package_parameters(self, new_parameters_in_json):
        existing_parameters = self.__get_package_parameters()
        return self.__combine_json(existing_parameters, json.loads(new_parameters_in_json))

    def __combine_json(self, json_existing, json_addition):
        LOGGER.debug("existing: %s", json_existing)
        LOGGER.debug("add: %s", json_addition)
        try:
            existing_keys = json_existing.keys()
        except:
            return json_addition

        additional_keys = json_addition.keys()
        need_copy = [{item:json_addition.get(item)} for item in additional_keys if item not in existing_keys]
        need_keep = [{item:json_existing.get(item)} for item in existing_keys if item not in additional_keys]
        need_merge = [{item:self.__combine_json(json_existing.get(item), json_addition.get(item))} for item in additional_keys if item in existing_keys]
        result_list = need_copy + need_keep + need_merge
        result = result_list[0]
        for item in result_list[1:]:
            result.update(item)
        LOGGER.debug("final dict: %s", result)
        return result

def format_test_parameters(parameter_string):
    parameter_list = str(parameter_string).split(",")
    parameter_json = json.loads('{"test_package_info":{"test_tool_arg":"-V neo-syve/parameters.yaml"}}')
    tp_parameters = {}
    ne_parameters = {}

    for para_pair in parameter_list:
        name = para_pair.split("=")[0]
        value = para_pair.split("=")[1]
        if name.startswith("TP_") or name.startswith("tp_"):
            tp_parameters.update({name[3:]:value})
        else:
            ne_parameters.update({name:value})

    parameter_json.update({"tp_parameters":tp_parameters, "ne_parameters":ne_parameters})
    return json.dumps(parameter_json)

    # Expected json-format parameter should be like:
    # parameter_json = {
    #     "tp_parameters": {
    #         "access_point": "test",
    #         "user_name": "test",
    #         "user_password":"test"
    #     },
    #     "ne_parameters": {
    #         "NE_ID": "test",
    #         "NE_TYPE":"SBTS",
    #         "AGENT_IP":"10.10.1.1",
    #     }
    # }

if __name__ == '__main__':
    test = CCTFProject("neo0033vip.netact.nsn-rdnet.net", "admin", "cctf@Pipe", "SmartRun", "DEBUG")
    test.create_lab("remote", "https://repo.lab.pl.alcatel-lucent.com/neo-generic-candidates/CCTF/neo-syve-0.0.3.tgz", "robot1", "neo-docker-candidates.repo.lab.pl.alcatel-lucent.com/neo-syve:latest")
    test.create_lab("remote",
                    "https://repo.lab.pl.alcatel-lucent.com/neo-generic-candidates/CCTF/neo-sat-poc-0.0.174.tgz",
                    "robot2", "neo-docker-candidates.repo.lab.pl.alcatel-lucent.com/neo-sat-poc:latest")

    test.update_package_parameters("robot1", format_test_parameters("TP_BASE_DOMAIN=neo0033.dyn.nesc.nokia.net,TP_NOM_OPEN_API_GW=apigw.neo0033.dyn.nesc.nokia.net,TP_NOM_OPEN_API_USERNAME=neonetics,TP_NOM_OPEN_API_PASSWORD=Nextgen#123,NE_ID=739,NE_TYPE=MRBTS,INTEGRATED_ID=MRBTS-1813,FM_SPECIFIC_PROBLEM=7220,METADATA_FASTPASS_PACKAGE_LINK=https://esisoj70.emea.nsn-net.net/artifactory/netact-fast-pass-release-local/fast_pass_packages/SBTS20C_ENB_9999_200602_000006/20200615T052532/SBTS20C_ENB_9999_200602_000006_20200615T052532.zip"))
    test.update_package_parameters("robot2", format_test_parameters("TP_BASE_DOMAIN=neo_fake.dyn.nesc.nokia.net,TP_NOM_OPEN_API_GW=apigw.neo_fake.dyn.nesc.nokia.net,TP_NOM_OPEN_API_USERNAME=neonetics,TP_NOM_OPEN_API_PASSWORD=Nextgen#123,NE_ID=666,NE_TYPE=MRBTS,INTEGRATED_ID=MRBTS-666,FM_SPECIFIC_PROBLEM=666,METADATA_FASTPASS_PACKAGE_LINK=https://esisoj70.emea.nsn-net.net/artifactory/netact-fast-pass-release-local/fast_pass_packages/SBTS20C_ENB_9999_200602_000006/20200615T052532/SBTS20C_ENB_9999_200602_000006_20200615T052532.zip"))

    test.execute_tagged_testcases_in_project("")
    test.get_execution_logs_by_jobid("robot.zip")

    # test.execute_all_testcases_in_group("FUNCTIONAL")
    # test.execute_test_case_by_test_id("")
    test.delete_labs()
    test.delete_project()
