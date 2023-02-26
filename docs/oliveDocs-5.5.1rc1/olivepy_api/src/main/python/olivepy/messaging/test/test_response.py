import unittest
import os
import json

from olivepy.test.test_utils import TestUtils

import olivepy.messaging.response as msg_response
from olivepy.messaging.olive_pb2 import *


TEST_SAD_LID_ABSTRACT_WORKFLOW = "sad-lid_abstract.workflow"
TEST_SAD_LID_ANALYSIS_REQUEST = "sad-lid_analysis_request.workflow"
TEST_SAD_LID_ANALYSIS_RESULT = "sad-lid_analysis_result.workflow"

TEST_SAD_LID_SID_ABSTRACT_WORKFLOW = "sad-lid-sid_abstract.workflow"
TEST_SAD_LID_SID_ANALYSIS_REQUEST = "sad-lid-sid_analysis_request.workflow"
TEST_SAD_LID_SID_ANALYSIS_RESULT = "sad-lid-sid_analysis_result.workflow"

# In these results LID failed (domain missing field) but LID was not an essential task so the workflow is successful
# with an error for the LID task results:
TEST_SAD_NON_ESSENTIAL_LID_ANALYSIS_REQUEST = "sad-lid-ERROR_analysis_request.workflow"
TEST_SAD_NON_ESSENTIAL_LID_ANALYSIS_RESULT = "sad-lid-ERROR_analysis_result.workflow"

class Test_Response(unittest.TestCase):
    """
    These the wrappers used around Server protobuf results when using the Async Olive Client.
    """

    def setUp(self):
        test_data_root = os.path.join(TestUtils.test_data_root)

        # Confirm set
        if not test_data_root:
            self.skipTest("Environment variable 'TEST_DATA_ROOT' not set, skipping workflow tests")

        # confirm we have our expected test data
        self.test_abstract_workflow_file = TestUtils.check_resource(
            os.path.join(test_data_root, "testSuite", "workflows", TEST_SAD_LID_ABSTRACT_WORKFLOW))
        self.test_workflow_analysis_request_file = TestUtils.check_resource(
            os.path.join(test_data_root, "testSuite", "workflows", TEST_SAD_LID_ANALYSIS_REQUEST))
        self.test_workflow_analysis_result_file = TestUtils.check_resource(
            os.path.join(test_data_root, "testSuite", "workflows", TEST_SAD_LID_ANALYSIS_RESULT))

        self.test2_workflow_file = TestUtils.check_resource(
            os.path.join(test_data_root, "testSuite", "workflows", TEST_SAD_LID_SID_ABSTRACT_WORKFLOW))
        self.test2_request_file = TestUtils.check_resource(
            os.path.join(test_data_root, "testSuite", "workflows", TEST_SAD_LID_SID_ANALYSIS_REQUEST))
        self.test2_result_file = TestUtils.check_resource(
            os.path.join(test_data_root, "testSuite", "workflows", TEST_SAD_LID_SID_ANALYSIS_RESULT))
        self.test_non_essential_lid_req = TestUtils.check_resource(
            os.path.join(test_data_root, "testSuite", "workflows", TEST_SAD_NON_ESSENTIAL_LID_ANALYSIS_REQUEST))
        self.test_non_essential_lid = TestUtils.check_resource(
            os.path.join(test_data_root, "testSuite", "workflows", TEST_SAD_NON_ESSENTIAL_LID_ANALYSIS_RESULT))

    # sad_lid_abstract.workflow


    def test_valid_response(self):

        # load the protobufs
        with open(self.test_workflow_analysis_request_file, 'rb') as f:
            workflow_analysis_request = WorkflowAnalysisRequest()
            workflow_analysis_request.ParseFromString(f.read())

        with open(self.test_workflow_analysis_result_file, 'rb') as f:
            workflow_analysis_result = WorkflowAnalysisResult()
            workflow_analysis_result.ParseFromString(f.read())


        owr = msg_response.OliveWorkflowAnalysisResponse()
        owr.parse_from_response(workflow_analysis_request, workflow_analysis_result, None)
        self.assertTrue(owr.is_successful())
        self.assertFalse(owr.is_error())
        self.assertFalse(owr.is_allowable_error())

        print("JSON results:")
        print("{}".format(owr.get_response_as_json()))

        # print("PROTOBUF results: {}".format(owr.get_response()))



        self.assertEqual(True, True)
#         Try creating
        #
    def test_non_essential_response(self):

        # load the protobufs
        with open(self.test_non_essential_lid_req, 'rb') as f:
            workflow_analysis_request = WorkflowAnalysisRequest()
            workflow_analysis_request.ParseFromString(f.read())

        with open(self.test_non_essential_lid, 'rb') as f:
            workflow_analysis_result = WorkflowAnalysisResult()
            workflow_analysis_result.ParseFromString(f.read())

        owr = msg_response.OliveWorkflowAnalysisResponse()
        owr.parse_from_response(workflow_analysis_request, workflow_analysis_result, None)
        self.assertTrue(owr.is_successful())
        self.assertFalse(owr.is_error())
        self.assertTrue(owr.is_allowable_error())

        # We should have one failed (allowable) task:
        failed_tasks = owr.get_failed_tasks()
        self.assertTrue("LID" in failed_tasks)
        # We should have one analysis job:
        job_names = owr.get_analysis_jobs()
        self.assertEqual(len(job_names), 1)
        self.assertEqual(job_names[0], 'job_sad_lid_error')

        # check tasks (we should have 2, even though LID failed)
        task_names = owr.get_analysis_tasks()
        self.assertEqual(2, len(task_names))
        self.assertTrue('SAD' in task_names)
        self.assertTrue('LID' in task_names)

        # get the actual task results
        task_result = owr.get_analysis_task_result(None, 'SAD')
        self.assertEqual(1, len(task_result))
        self.assertEqual('SAD', task_result[0]['SAD']['taskType'])
        # should also have data
        self.assertEqual(1, len(task_result[0]['data']))
        print("SAD task result: {}".format(json.dumps(task_result, indent=1)))


        task_result = owr.get_analysis_task_result(None, 'LID')
        self.assertEqual(1, len(task_result))
        self.assertEqual('LID', task_result[0]['LID']['taskType'])
        self.assertTrue('error' in task_result[0]['LID'])

        job_results = owr.get_analysis_job_result()
        self.assertEqual(1, len(job_results))
        jr = job_results[0]
        self.assertTrue('job_sad_lid_error' == jr['job_name'])
        self.assertEqual(2, len(jr['tasks']))
        self.assertEqual(1, len(jr['data']))
        self.assertEqual('sad_smoke.wav', jr['data'][0]['dataId'])
        print("sad, bad lid job results: {}".format(json.dumps(job_results, indent=1)))




        print("JSON results:")
        print("{}".format(json.dumps(owr.get_response_as_json(), indent=1) ))

        # print("PROTOBUF results: {}".format(owr.get_response()))



        self.assertEqual(True, True)
#         Try creating


    def test_valid_sad_lid_sid_response(self):

        # load the protobufs
        with open(self.test2_request_file, 'rb') as f:
            workflow_analysis_request = WorkflowAnalysisRequest()
            workflow_analysis_request.ParseFromString(f.read())

        with open(self.test2_result_file, 'rb') as f:
            workflow_analysis_result = WorkflowAnalysisResult()
            workflow_analysis_result.ParseFromString(f.read())

        owr = msg_response.OliveWorkflowAnalysisResponse()
        owr.parse_from_response(workflow_analysis_request, workflow_analysis_result, None)
        self.assertTrue(owr.is_successful())
        self.assertFalse(owr.is_error())
        self.assertFalse(owr.is_allowable_error())

        # Verify we
        analysis_request_jobs = owr.get_request_jobs(WORKFLOW_ANALYSIS_TYPE)
        self.assertEqual(len(analysis_request_jobs), 1)

        # We should have one failed (allowable) task:
        failed_tasks = owr.get_failed_tasks()
        self.assertEqual(len(failed_tasks), 0)
        # We should have 1 analysis jobs:
        job_names = owr.get_analysis_jobs()
        self.assertEqual(len(job_names), 1)
        self.assertEqual(job_names[0], 'job_sad_lid_sid')

        # get the tasks for our one and only job:
        request_tasks = msg_response.get_workflow_job_tasks(analysis_request_jobs)
        # there should be 3 tasks:
        self.assertEqual(len(request_tasks), 3)


        print("JSON SAD, LID, SID results {}:".format(json.dumps(owr.get_response_as_json(), indent=1)))

        # print("PROTOBUF results: {}".format(owr.get_response()))

        self.assertEqual(True, True)
#         Try creating



if __name__ == '__main__':
    unittest.main()
