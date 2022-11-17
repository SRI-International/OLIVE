
import os
import unittest
import json

import olivepy.api.olive_async_client as oc
import olivepy.api.workflow as ow

# from .test_utils import TestUtils
from olivepy.messaging.olive_pb2 import *

# test:
from google.protobuf.json_format import MessageToJson
from google.protobuf import text_format
import olivepy.messaging.msgutil as msgutil
from olivepy.messaging.response import OliveWorkflowActualizedResponse

from olivepy.test.test_utils import TestUtils

# THESE TESTS EXPECT AN OLVER SERVER RUNNING WITH THE TEST (dummy) PLUGINS LOADED

# This workflow is created by the Python scenicserver_test.test_workflow_definition_request():
TEST_WORKFLOW_DEF = "sad_lid_python.workflow"

# And these workflows are created from Java:
TEST_WORKFLOW_W_ERRORS_DEF = "sad_exception_frames_workflow.wkf"
TEST_WORKFLOW_SAD_LID = "sad_lid_abstract.workflow"

# An actualized workflow:
TEST_SAD_LID_SID_ACTUALIZED_WORKFLOW = "sad-lid-sid_actualized_workflow"
TEST_SAD_SHL_ACTUALIZED_WORKFLOW = "sad-shl_actualized_workflow"

TEST_SAD_LID_SID_ENROLLMENT_WORKFLOW = "sad-lid-sid-eroll_abstract.workflow"


# TEST_SAD_LID_SID_ACTUALIZED_WORKFLOW = "sad-lid-sid_actualized_workflow"

class TestWorkflow(unittest.TestCase):
    '''Tests for workflow functions'''
    
    def setUp(self):

        test_data_root = os.path.join(TestUtils.test_data_root)

        # Confirm set
        if not test_data_root:
            self.skipTest("Environment variable 'TEST_DATA_ROOT' not set, skipping workflow tests")

        # confirm we have our expected test data
        self.test_workflow_def_file = TestUtils.check_resource(os.path.join(test_data_root,
                                                                            "testSuite",
                                                                            "workflows",
                                                                            TEST_WORKFLOW_DEF))

        # loads a workflow definition that contains 3 tasks: SAD, SAD (generates an error), and an Olive Node
        # that converts SAD frames to regions:
        self.test_error_workflow_def_file = TestUtils.check_resource(os.path.join(test_data_root,
                                                                                  "testSuite",
                                                                                  "workflows",
                                                                                  TEST_WORKFLOW_W_ERRORS_DEF))

        self.test_actualized_workflow_def_file = TestUtils.check_resource(os.path.join(test_data_root,
                                                                                  "testSuite",
                                                                                  "workflows",
                                                                                  TEST_SAD_LID_SID_ACTUALIZED_WORKFLOW))

        self.test_shl_workflow_def_file = TestUtils.check_resource(os.path.join(test_data_root,
                                                                                  "testSuite",
                                                                                  "workflows",
                                                                                  TEST_SAD_SHL_ACTUALIZED_WORKFLOW))
        self.test_enroll_workflow_def_file = TestUtils.check_resource(os.path.join(test_data_root,
                                                                                  "testSuite",
                                                                                  "workflows",
                                                                                  TEST_SAD_LID_SID_ENROLLMENT_WORKFLOW))

        self.test_sad_lid_workflow_def_file = TestUtils.check_resource(os.path.join(test_data_root,
                                                                                  "testSuite",
                                                                                  "workflows",
                                                                                  TEST_WORKFLOW_SAD_LID))

        self.test_short_file = TestUtils.check_resource(
            os.path.join(test_data_root, "testSuite", "stress", "short_30ms_16k_1ch_16b.wav"))

    @classmethod
    def setUpClass(cls):

        # First make sure our test data exists

        cls.client = oc.AsyncOliveClient(cls.__class__.__name__, "localhost", 5588, 10)
        cls.client.connect()

    @classmethod
    def tearDownClass(cls):    
        cls.client.disconnect()


    def test_analysis_workflow_info(self):

        wd = WorkflowDefinition()
        with open(self.test_actualized_workflow_def_file, 'rb') as f:
            wd.ParseFromString(f.read())

        request = WorkflowActualizeRequest()
        result = WorkflowActualizeResult()
        result.workflow.CopyFrom(wd)

        response = OliveWorkflowActualizedResponse()
        response.parse_from_response(request, result, None)

        owork = ow.OliveWorkflow(self.client, response)

        analysis_tasks = owork.get_analysis_tasks()
        # we expect SAD,LID,SID:
        self.assertEqual(len(analysis_tasks), 3)
        self.assertTrue("SID" in analysis_tasks)

        # sid_task = analysis_tasks['SID']
        self.assertTrue('SID' in analysis_tasks)

        # Same thing but, specify via job name:
        analysis_tasks = owork.get_analysis_tasks('job_sad_lid_sid')
        # we expect SAD,LID,SID:
        self.assertEqual(len(analysis_tasks), 3)
        self.assertTrue("SID" in analysis_tasks)
        self.assertTrue("LID" in analysis_tasks)


        no_tasks = owork.get_analysis_tasks('job_sad_lid_sid_kws')
        self.assertIsNone(no_tasks)



    def test_text_workflow(self):

        fsr = FrameScorerRequest()
        fsr.plugin = "sad-dnn-v6-v2"
        fsr.domain = "tel-v1"
        # no audio since this is part of a workflow

        f = open("/tmp/test_fsr.txt", "w")
        f.write(text_format.MessageToString(fsr))
        f.close()

        wd = WorkflowDefinition()
        with open(self.test_actualized_workflow_def_file, 'rb') as f:
            wd.ParseFromString(f.read())

        workflow_definition = ow.OliveWorkflowDefinition("/tmp/wfd_as_text.txt")
        # wd_txt = WorkflowDefinition()
        # with open("/Users/e24652/dev/star/olive/api/src/main/python/wfd_as_text.txt", 'r') as f:
        # # with open(self.test_actualized_workflow_def_file, 'r') as f:
        #     # wd_txt.ParseFromString(f.read())
        #     # this doesn't work
        #     # txt = f.read()
        #     # text_format.Parse(txt)
        #
        #     # but we should be able to convert to JSON, so do  that
        #     json_input = json.loads(f.read())
        #     # Parse(json_input, wd_txt)
        #
        #     # Works!
        #     # Parse(f.read(), wd_txt)


        f = open("/tmp/test_wd.txt", "w")
        f.write(text_format.MessageToString(wd))
        f.close()

        workflow_filename = "/Users/e24652/dev/star/olive/api/src/main/python/test.txt"

        # wd = WorkflowDefinition()
        # with open(workflow_filename, 'r') as f:
        #     wd.ParseFromString(f.read())

        # workflow_definition = ow.OliveWorkflowDefinition(workflow_filename)
        print("CLG parsed wd: {}".format(wd))




    def test_example(self):

        workflow_definition = ow.OliveWorkflowDefinition("/Users/e24652/audio/testSuite/workflows/speech_analysis_abstract.workflow")

        workflow_definition._save_as_json('/Users/e24652/audio/testSuite/workflows/speech_analysis_abstract.txt')

        # First, create the connection to the OLIVE server:
        client = oc.AsyncOliveClient("test olive client")
        client.connect()

        # Load the workflow
        workflow_filename = "~/sad_lid_sid.workflow"
        workflow_filename = self.test_sad_lid_workflow_def_file
        workflow_filename = self.test_enroll_workflow_def_file
        workflow_definition = ow.OliveWorkflowDefinition(workflow_filename)

        workflow_definition._save_as_json('/tmp/wfd_as_text.txt')

        # Submit the workflow definition to the client for actualization (instantiation):
        workflow = workflow_definition.create_workflow(client)
        workflow.get_analysis_tasks()

        # Example audio file to submit (as a serialzied file)
        audio_filename = "~/audio/cgeorge_python.wav"
        buffers = []
        # send the file as a serialize file
        buffers.append(workflow.package_audio(audio_filename, label=os.path.basename(audio_filename)))

        response = workflow.analyze(buffers)

        json_output = response.get_response_as_json()

        # SAD with region score output
        sad_regions = json_output[0]['tasks']['SAD']['analysis']['region']
        lid_scores = json_output[0]['tasks']['LID']['analysis']['score']
        # frame_scores = json_output[0]['tasks']['SAD']['analysis']['result'][0]['score']

        # better:
        sad_regions = json_output[0]['tasks']['SAD']['analysis']['region']
        lid_scores = json_output[0]['tasks']['LID']['analysis']['score']
        # frame_scores = json_output[0]['tasks']['SAD']['analysis']['result'][0]['score']

        for region_score in sad_regions:
            print(region_score)

        print("Workflow results:")
        print("{}".format(json.dumps(response.get_response_as_json(), indent=1)))

        client.disconnect()

    def test_workflow_request(self):
        '''Test actualizing a basic workflow with a SAD and LID tasks .'''

        # Load our test Workflow Definition
        # test_workflow_def = WorkflowDefinition()
        # with open(self.test_workflow_def_file, "rb") as w_file:
        #     test_workflow_def.ParseFromString(w_file.read())

        owd = ow.OliveWorkflowDefinition(self.test_workflow_def_file)

        workflow = owd.create_workflow(self.client)
        # there should be one analysis order with two tasks, and one enrollment order with one tasks
        self.assertEqual(len(workflow.workflow_def.order), 1)
        self.assertEqual(len(workflow.workflow_def.order[0].job_definition), 1)
        self.assertEqual(len(workflow.workflow_def.order[0].job_definition[0].tasks), 2)

        analysis_order = None
        enroll_order = None
        for order in workflow.workflow_def.order:
            if order.workflow_type == WORKFLOW_ANALYSIS_TYPE:
                analysis_order = order
            elif  order.workflow_type == WORKFLOW_ENROLLMENT_TYPE:
                enroll_order = order

        self.assertIsNotNone(analysis_order)
        self.assertIsNone(enroll_order)


        # print("workflow: {}".format(workflow.workflow_def))
        # results = self.client.actualize_workflow(test_workflow_def)
        # print("Received actualized workflow : {}".format(workflow))

        # Example of converting to JSON
        json_WFD = MessageToJson(workflow.workflow_def)
        print("Print json actualized workflow : {}".format(json_WFD))


        # self.assertEqual(len(results), 19)
        # # For humans, just print out two values
        # for result in results:
        #     if result.class_id == 'fre':
        #         fre = result.score
        #         self.assertAlmostEqual(result.score, 6.6479, 3)
        #     else:
        #         key = result.class_id
        #         value = result.score
        #         self.assertLess(result.score, 0)
        # if TestUtils.verbose:
        #     print('Got {} results.  Rus is {} and {} is {}.'.format(len(results), fre, key, value))

        msg_data = workflow.workflow_def.order[0].job_definition[0].tasks[0].message_data
        msg_type = workflow.workflow_def.order[0].job_definition[0].tasks[0].message_type

        response_msg = msgutil.type_class_map[msg_type]()
        response_msg.ParseFromString(msg_data)

        print("Print SAD? task : {}".format(response_msg))

    def sad_lid_workflow_callback(self, request, response, err_msg):
        print("Received workflow result")

        if err_msg:
            print("Workflow request failed: {}".format(err_msg))
        else:
            # we should have a WorkflowAnalysisResult
            json_WFD = MessageToJson(response.job_result[0])
            print("Workflow result : {}".format(json_WFD))



    def test_workflow_with_regions(self):

        # client = oc.AsyncOliveClient("test_client", "localhost", 5588, 10)
        # client.connect()

        owd = ow.OliveWorkflowDefinition(self.test_shl_workflow_def_file)
        workflow = owd.create_workflow(self.client)

        # Create regions for SHL
        regions = [(0.3, 1.7), (2.4, 3.3)]
        shl_regions = {'SHL': {'speaker': [(5.5, 6.2), (8.2, 9.3)]}}

        audio = workflow.package_audio("/Users/e24652/audio/sad_smoke.wav", annotations=regions, task_annotations=shl_regions)

        response = workflow.analyze([audio])
        self.assertFalse(response.is_error())
        self.assertFalse(response.is_allowable_error())

        analysis_jobs = response.get_analysis_jobs

        if response.is_error():
            print("Workflow failed: {}".format(response.get_error()))
        else:
            print("Workflow JSON results: {}".format(json.dumps(response.get_response_as_json(), indent=1) ))


    def test_workflow_enrollment(self):
        """
        Test a workflow enrollment for our dummy lid plugin
        :return:
        """

        # client = oc.AsyncOliveClient("test_client", "localhost", 5588, 10)
        # client.connect()

        owd = ow.OliveWorkflowDefinition(self.test_enroll_workflow_def_file)
        workflow = owd.create_workflow(self.client)

        enroll_tasks = workflow.get_enrollment_tasks()

        # buffer = workflow.serialize_audio("/Users/e24652/audio/sad_smoke.wav")
        audio = workflow.package_audio("/Users/e24652/audio/sad_smoke.wav")

        response = workflow.enroll([audio], "smoke", None)
        self.assertFalse(response.is_error())
        self.assertFalse(response.is_allowable_error())

        # analysis_jobs = response.get_analysis_jobs

        if response.is_error():
            print("Workflow failed: {}".format(response.get_error()))
        else:
            print("Workflow JSON results: {}".format(json.dumps(response.get_response_as_json(), indent=1) ))



    # todo test:
    # not using an label for package_audio()

    def test_basic_workflow(self):

        client = oc.AsyncOliveClient("test_client", "localhost", 5588, 10)
        client.connect()

        owd = ow.OliveWorkflowDefinition("/Users/e24652/audio/testSuite/workflows/sad_lid_abstract.workflow")
        workflow = owd.create_workflow(self.client)

        audio = workflow.package_audio("/Users/e24652/audio/sad_smoke.wav")

        response = workflow.analyze([audio])

        if response.is_error():
            print("Workflow failed: {}".format(response.get_error()))
        else:
            print("Workflow JSON results: {}".format(json.dumps(response.get_response_as_json(), indent=1)))

        client.disconnect()


    def test_workflow_audio_as_file(self):

        client = oc.AsyncOliveClient("test_client", "localhost", 5588, 10)
        client.connect()

        owd = ow.OliveWorkflowDefinition("/Users/e24652/audio/testSuite/workflows/sad_lid_abstract.workflow")
        workflow = owd.create_workflow(self.client)

        # buffer = workflow.serialize_audio("/Users/e24652/audio/sad_smoke.wav")
        audio = workflow.package_audio("/Users/e24652/audio/sad_smoke.wav", mode=msgutil.AudioTransferType.AUDIO_PATH)

        response = workflow.analyze([audio])

        if response.is_error():
            print("Workflow failed: {}".format(response.get_error()))
        else:
            print("Workflow JSON results: {}".format(json.dumps(response.get_response_as_json(), indent=1)))

        client.disconnect()


    # todo test:
    # not using an label for package_audio()

    def test_workflow_analysis(self):
        '''Test actualizing a basic workflow with a SAD and LID tasks .'''

        # First, load our test Workflow Definition
        owd = ow.OliveWorkflowDefinition(self.test_workflow_def_file)
        workflow = owd.create_workflow(self.client)

        # check the tasks supported by this workflow:
        analysis_tasks = workflow.get_analysis_tasks()
        self.assertEqual(len(analysis_tasks), 2)
        self.assertTrue('LID Analysis' in analysis_tasks)
        self.assertTrue('SAD Analysis' in analysis_tasks)

        audio = workflow.package_audio(self.test_short_file, label=self.test_short_file)
        self.assertEqual(audio.data_id, self.test_short_file)
        self.assertEqual(audio.data_type, AUDIO)


        # workflow.analyze([audio], self.sad_lid_workflow_callback)
        # we need to wait for the callback:
        # time.sleep(8)

        # not using a callback:
        # _, response, err_msg = workflow.analyze([audio])
        response = workflow.analyze([audio])

        if response.is_error():
            print("Workflow request failed: {}".format(response.get_error()))
        else:
            # we should have a WorkflowAnalysisResult
            # json_WFD = MessageToJson(response.get_response().job_result[0])
            json_response = response.get_response_as_json()
            print("Workflow result: ")
            print("{}".format(json_response))
            print("Workflow result: dumps")
            print("{}".format(json.dumps(json_response, indent=1)))


        # there should be one analysis order with two tasks, and no enrollment orders
        self.assertEqual(len(workflow.workflow_def.order), 1)
        self.assertEqual(len(workflow.workflow_def.order[0].job_definition), 1)
        self.assertEqual(len(workflow.workflow_def.order[0].job_definition[0].tasks), 2)

        # print("workflow: {}".format(workflow.workflow_def))
        # results = self.client.actualize_workflow(test_workflow_def)
        # print("Received actualized workflow : {}".format(workflow))

        # Example of converting to JSON
        #


        # self.assertEqual(len(results), 19)
        # # For humans, just print out two values
        # for result in results:
        #     if result.class_id == 'fre':
        #         fre = result.score
        #         self.assertAlmostEqual(result.score, 6.6479, 3)
        #     else:
        #         key = result.class_id
        #         value = result.score
        #         self.assertLess(result.score, 0)
        # if TestUtils.verbose:
        #     print('Got {} results.  Rus is {} and {} is {}.'.format(len(results), fre, key, value))

        msg_data = workflow.workflow_def.order[0].job_definition[0].tasks[0].message_data
        msg_type = workflow.workflow_def.order[0].job_definition[0].tasks[0].message_type

        response_msg = msgutil.type_class_map[msg_type]()
        response_msg.ParseFromString(msg_data)

        print("Done with request...")
        # print("Print SAD? task : {}".format(response_msg))