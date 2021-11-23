import os
import numpy as np
import json
from google.protobuf.json_format import MessageToJson
from google.protobuf.json_format import Parse
import base64

from google.protobuf.message import DecodeError

import olivepy.messaging.msgutil
import olivepy.messaging.msgutil as msgutil
import olivepy.messaging.response as response

# from olivepy.messaging.olive_pb2 import *
from olivepy.messaging.olive_pb2 import (Audio, Text, WorkflowDefinition,
                                         WorkflowAnalysisRequest, WorkflowActualizeRequest,
                                         WorkflowDataRequest, WorkflowClassStatusRequest,
                                         WorkflowEnrollRequest, WorkflowUnenrollRequest, MessageType,
                                         WORKFLOW_ANALYSIS_TYPE, WORKFLOW_ENROLLMENT_TYPE,
                                         WORKFLOW_UNENROLLMENT_TYPE, AUDIO, TEXT, VIDEO, IMAGE, BinaryMedia)
from olivepy.utils import utils

from olivepy.api.olive_async_client import AsyncOliveClient

from typing import (
    List, Dict, AnyStr, Tuple
)


class WorkflowException(Exception):
    """This exception means that an error occurred handling a Workflow"""
    pass


class OliveWorkflowDefinition(object):
    """
    Used to load a Workflow Definition from a file.
    """

    def __init__(self, filename: str):
        """
        Create an OliveWorkflowDefinition to access a workflow definition file

        :param filename: the path/filename of a workflow definition file to load
        """
        # First, make sure the workflow definition (WD) file exists
        filename = os.path.expanduser(filename)
        if not os.path.exists(filename):
            raise IOError("Workflow definition file '{}' does not exists".format(filename))

        # Load the WD, then submit to the server

        # Read the workflow - either a workflow or a text file
        try:
            with open(filename, 'rb') as f:
                self.wd = WorkflowDefinition()
                self.wd.ParseFromString(f.read())
        except IOError as e:
            raise IOError("Workflow definition file '{}' does not exist".format(filename))
        except DecodeError as de:
            self.wd = WorkflowDefinition()
            # Try parsing as text file (will fail for a protobuf file)
            with open(filename, 'r') as f:
                # First load as json
                json_input = json.loads(f.read())
                # Next, we need to convert message data in task(s) to byte strings
                for element in json_input:
                    if element == 'order':
                        for job in json_input[element]:
                            # print("Job: {}".format(job))
                            for job_def in job['job_definition']:
                                for task in job_def['tasks']:
                                    task_type = task['message_type']
                                    # Covert 'messageData' into a protobuf and save the byte string in the json
                                    # so it can be correctly deserialized
                                    tmp_json = task['message_data']
                                    msg = msgutil.type_class_map[MessageType.Value(task_type)]()
                                    Parse(json.dumps(tmp_json), msg)
                                    # now serialized msg as messageData
                                    data = base64.b64encode(msg.SerializeToString()).decode('utf-8')
                                    task['message_data'] = data

            # Now we should be able to create a WorkflowDefinition from the json data
            Parse(json.dumps(json_input), self.wd)

        # Create JSON formatted output from the the Workflow?

    def get_json(self, indent=1):
        """
        Create a JSON structure of the Workflow

        :param indent:  if a non-negative integer, then JSON array elements and object members will be pretty-printed with that indent level. An indent level of 0 will only insert newlines. None is the most compact representation. A negative value will return the JSON document

        :return: A JSON (dictionary) representation of the Workflow Definition
        """
        analysis_task = []
        job_names = set()

        workflow_analysis_order_msg = None
        for order in self.wd.order:
            if order.workflow_type == WORKFLOW_ANALYSIS_TYPE:
                workflow_analysis_order_msg = order
                break

        if workflow_analysis_order_msg is None:
            # no analysis results
            return analysis_task

        # for job in self._response.job_result:
        for job in workflow_analysis_order_msg.job_definition:
            # create a dictionary for each job result
            job_dict = dict()
            job_name = job.job_name
            job_names.add(job_name)

            # get data handling info for this job
            data_prop = job.data_properties
            job_dict['Data Input'] = json.loads(MessageToJson(data_prop, preserving_proto_field_name=True))
            # if data_prop.mode == SPLIT:
            #     # Hack to make split/mulit-channel mode more clear
            #     job_dict['data']['mode'] = 'SPLIT: Process each channel as a job'

            # and a dictionary of tasks:
            # add to our results - in most cases we will have just one job
            analysis_task.append(job_dict)

            for task in job.tasks:
                task_result_dict = json.loads(MessageToJson(task, preserving_proto_field_name=True))

                # Deserialize message_data, and replace it in the task_result_dict
                task_type_msg = self._extract_serialized_message(task.message_type, task.message_data)
                task_result_dict['job_name'] = job_name
                task_result_dict['analysis'] = json.loads(
                    MessageToJson(task_type_msg, preserving_proto_field_name=True))
                del task_result_dict['message_data']

                job_dict[task.consumer_result_label] = task_result_dict

        return json.dumps(analysis_task, indent=indent)

    def _save_as_json(self, filename):
        """
        Save workflow as JSON structure of the Workflow output

        :param filename: save to this filename

        """

        filename = os.path.expanduser(filename)
        # json_str_output = MessageToJson(self.wd)
        #
        # json_output = json.loads(json_str_output)
        # for element in json_output:
        #     if element == 'order':
        #         for job in json_output[element]:
        #             # print("Job: {}".format(job))
        #             for job_def in job['jobDefinition']:
        #                 for task in job_def['tasks']:
        #                     task_type = task['messageType']
        #                     data = base64.b64decode(task['messageData'])
        #                     msg = self._extract_serialized_message(MessageType.Value(task_type), data)
        #                     task['messageData'] = json.loads(MessageToJson(msg))
        #                     # print("Task: {}".format(task))

        json_output = self.to_json(indent=-1)
        with open(filename, 'w') as file:
            # file.write(json_output)
            # json.dump(json_output, file, indent=1)
            json.dump(json_output, file, indent=1)

    def to_json(self, indent=None):
        """
        Generate the workflow as a JSON string

        :param indent:  if a non-negative integer, then JSON array elements and object members will be pretty-printed with \
        that indent level. An indent level of 0 will only insert newlines. ``None`` is the most compact  \
        representation. A negative value will return the JSON document

        :return: the Workflow Definition as as JSON string:
        """
        json_str_output = MessageToJson(self.wd, preserving_proto_field_name=True)

        json_output = json.loads(json_str_output)
        for element in json_output:
            if element == 'order':
                for job in json_output[element]:
                    # print("Job: {}".format(job))
                    for job_def in job['job_definition']:
                        for task in job_def['tasks']:
                            task_type = task['message_type']
                            data = base64.b64decode(task['message_data'])
                            msg = self._extract_serialized_message(MessageType.Value(task_type), data)
                            task['message_data'] = json.loads(MessageToJson(msg, preserving_proto_field_name=True))
                            # print("Task: {}".format(task))

        if indent and indent < 0:
            return json_output
        return json.dumps(json_output, indent=indent)

    def _save_as_binary(self, filename):
        """
        Save workflow as a binary Workflow file for cross platform use.

        :return:
        """

        filename = os.path.expanduser(filename)

        with open(filename, 'wb') as file:
            file.write(self.wd.SerializeToString())

    def _extract_serialized_message(self, msg_type, msg_data):
        msg = msgutil.type_class_map[msg_type]()
        msg.ParseFromString(msg_data)

        return msg

    def create_workflow(self, client: olivepy.api.olive_async_client.AsyncOliveClient):
        """
        Create a new, executable (actualized), Workflow, which can be used to make OLIVE analysis, or enrollment requests

        :param client: an open client connection to an OLIVE server

        :return: a new OliveWorkflow object, which  has been actualized (activated) by the olive server

        """

        if not client.is_connected():
            raise IOError("No connection to the Olive server")

        # Create a workflow request
        request = WorkflowActualizeRequest()
        request.workflow_definition.CopyFrom(self.wd)

        workflow_result = client.sync_request(request, response.OliveWorkflowActualizedResponse())
        if workflow_result.is_error():
            raise msgutil.ExceptionFromServer(workflow_result.get_error())
        # if msg:
        #     raise msgutil.ExceptionFromServer(msg)

        return OliveWorkflow(client, workflow_result)

        # todo send WD to server, return an OliveWorklow to the user


class OliveWorkflow(object):
    """
    An OliveWorkflow instance represents a Workflow Definition actualized by an OLIVE server.  Once actualized, an OliveWorkflow instance is used to make analysis,
    or enrollment/unenrollment requests.  An OliveWorkflow should be created using an OliveWorkflowDefinition's create_workflow() method.  All calls to the server include an optional callback.  When the callback is provided, the call does not block and the callback method is invoked when a response is received from the server.  A callback method has 3 arguments:  the original request, the response, and an  error message if the request failed.



    :raises WorkflowException: If the workflow was not actualized
    """

    def __init__(self, olive_async_client: AsyncOliveClient,
                 actualized_workflow: response.OliveWorkflowActualizedResponse):
        """
        :param olive_async_client:  the client connection to the OLIVE server
        :param actualized_workflow: An OliveWorkflowDefinition actualized by the server
        """

        self.client = olive_async_client
        self.workflow_response = actualized_workflow
        actualized_workflow_definition = actualized_workflow.get_workflow()
        # make sure an OLIvE server has actualized this workflow
        if not actualized_workflow_definition.actualized:
            raise WorkflowException("Error: Can not create an OliveWorkflow using  a Workflow Definition that has not "
                                    "been actualized by an OLIVE server")

        self.workflow_def = actualized_workflow_definition

        # note: enrollment and adapt should only have one task/job
        # but there could be multiple plugins/task that could support enrollment or adaptation.. so we focus on
        # analysis

    def get_analysis_job_names(self) -> List[str]:
        """
        The names of analysis jobs in this workflow (usually only one analysis job)

        :return: A list of analysis job names in this workflow
        """
        return response.get_workflow_job_names(self.workflow_def, WORKFLOW_ANALYSIS_TYPE)

    def get_enrollment_job_names(self) -> List[str]:
        """
        The names of enrollment jobs in this workflow.  There should be one enrollment job for each analysis tasks that supports class enrollment

        :return: A list of enrollment job names in this workflow
        """
        return response.get_workflow_job_names(self.workflow_def, WORKFLOW_ENROLLMENT_TYPE)

    def get_unenrollment_job_names(self) -> List[str]:
        """
        The names of un-enrollment jobs in this workflow.  There should be one un-enrollment job for each analysis task that supports class un-enrollment

        :return: A list of un-enrollment job names in this workflow
        """
        return response.get_workflow_job_names(self.workflow_def, WORKFLOW_UNENROLLMENT_TYPE)

    def get_analysis_tasks(self, job_name: str = None) -> List[str]:
        """
        Return a list of tasks supported by this workflow. These names are unique and can generally be assumed they are named after the task type (SAD, LID, SID, etc) they support but they could use alternate names if there are multiple tasks with the same task type in a workflow (for example a workflow could have a SAD task that does frame scoring and a SAD task that does regions scoring)

        :param job_name: filter the returned task names to those belonging to this job name.  Optional since most workflows only support one analysis job.

        :return: a list of task names
        """
        analysis_jobs = response.get_workflow_jobs(self.workflow_def, WORKFLOW_ANALYSIS_TYPE)

        # better to exception or empty dict????
        if len(analysis_jobs) == 0:
            return None

        if job_name is not None:
            if job_name not in analysis_jobs:
                return None
        else:
            # get the default job name
            job_name = list(analysis_jobs.keys())[0]

        return [task.consumer_result_label for task in analysis_jobs[job_name]]

    def get_enrollment_tasks(self, job_name: str = None, type=WORKFLOW_ENROLLMENT_TYPE) -> List[str]:
        """
        Return a list of tasks that support enrollment in this workflow.

        :param job_name: optionally the name of the enrollment job.  Optional since most workflows only support one job

        :return: a list of task names
        """
        enrollment_jobs = response.get_workflow_jobs(self.workflow_def, type)
        if len(enrollment_jobs) == 0:
            return None

        if job_name is not None:
            if job_name not in enrollment_jobs:
                return None

        # normally (and currently the only supported option) should be just one enrollment_job...
        return list(response.get_workflow_job_tasks(enrollment_jobs, job_name).keys())

    def get_unenrollment_tasks(self, job_name: str = None) -> List[str]:
        """
        Return a list of tasks that support UNenrollment in this workflow.

        :param job_name: optionally the name of the enrollment job.  Optional since most workflows only support one job

        :return: a list of task names
        """
        return self.get_enrollment_tasks(job_name, type=WORKFLOW_UNENROLLMENT_TYPE)

    def get_analysis_task_info(self) -> List[Dict[str, Dict]]:
        """
        A JSON like report of the tasks used for analysis from the actualized workflow.  When possible, this report \
        includes the plugins used in the workflow (although there can be cases when the final plugin/domain used is \
        not known until runtime)

        :return: JSON structured detailed information of analysis tasks used in this workflow
        """
        # return [task.consumer_result_label for task in analysis_jobs[job_name]]
        return self.workflow_response.to_json(indent=1)

    def to_json(self, indent=None):
        """
           Generate the workflow as a JSON string
           :param indent:  if a non-negative integer, then JSON array elements and object members will be pretty-printed with \
           that indent level. An indent level of 0 will only insert newlines. ``None`` is the most compact  \
           representation. A negative value will return the JSON document

           :return: the Workflow Definition as as JSON string:
        """
        return self.workflow_response.to_json(indent=indent)

    def serialize_audio(self, filename: str) -> AnyStr:
        """
        Helper function used to read in an audio file and output a serialized buffer.  Can be used with package_audio() \
        when using the AUDIO_SERIALIZED mode and the audio input has not already been serialized

        :param filename: the local path to the file to serialize

        :return: the contents of the file as a byte buffer, otherwise an exception if the file can not be opened.  This buffer contains the raw content of the file, it does NOT contain encoded samples
        """
        with open(os.path.expanduser(filename), 'rb') as f:
            serialized_buffer = f.read()

        # return the buffer
        return serialized_buffer

    # def package_audio(self, audio_input, selected_channel=None, annots=None):
    def package_audio(self, audio_data: AnyStr,
                      mode=olivepy.messaging.msgutil.InputTransferType.SERIALIZED,
                      annotations: List[Tuple[float, float]] = None,
                      task_annotations: Dict[str, Dict[str, List[Tuple[float, float]]]] = None,
                      selected_channel: int = None,
                      num_channels: int = None,
                      sample_rate: int = None,
                      num_samples: int = None,
                      validate_local_path: bool = True,
                      label=None) -> WorkflowDataRequest:
        """

        Creates an Audio object that can be submitted with a Workflow analysis, enrollment, or adapt request.

        :param audio_data: the input data is  a string (file path) if mode is 'AUDIO_PATH', otherwise the input data is a binary buffer.  Use serialize_audio() to serialize a file into a buffer, or pass in a list of PCM_16 encoded samples
        :param mode: specifies how the audio is sent to the server: either as (string) file path or as a binary buffer.  NOTE: if sending a path, the path must be valid for the server.
        :param annotations: optional regions (start/end regions in seconds) as a list of tuples (start_seconds, end_seconds)
        :param task_annotations: optional and more regions (start/end regions in seconds) targeted for a task and classifed by a lable (such as speech, non-speech, speaker).  For example: {'SHL': {'speaker'':[(0.5, 4.5), (6.8, 9.2)]}, are annotations for the 'SHL' task, which are labeled as class 'speaker' having regions 0.5 to 4.5, and 6.8 to 9.2. Use get_analysis_tasks() to get the name of workflow tasks .
        :param selected_channel: optional - the channel to process if using multi-channel audio
        :param num_channels: The number of channels if audio input is a list of decoded (PCM-16) samples, if not using a buffer of PCM-16 samples this is value is ignored
        :param sample_rate: The sample rate if audio input is a list of  decoded (PCM-16) samples, if not using a buffer of PCM-16 samples this is value is ignored
        :param num_samples: The number of samples if audio input is a list of decoded (PCM-16) samples, if not using a buffer of PCM-16 samples this is value is ignored
        :param validate_local_path: If sending audio as as a string path name, then check that the path exists on the local filesystem.  In some cases you may want to pass a path which is valid on the server but not this client so validation is not desired
        :param label: an optional name to use with the audio

        :return: A populated WorkflowDataRequest to use in a workflow activity
        """
        audio = Audio()
        msgutil.package_audio(audio, audio_data, annotations, selected_channel, mode, num_channels, sample_rate,
                              num_samples, validate_local_path)

        # Add any task specific regions:
        if task_annotations:
            for task_label in task_annotations.keys():
                ta = audio.task_annotations.add()
                ta.task_label = task_label
                # we only expect to have one set of annotations, so just one region_label
                for region_label in task_annotations[task_label]:
                    ta.region_label = region_label
                    for annots in task_annotations[task_label][region_label]:
                        region = ta.regions.add()
                        region.start_t = np.float(annots[0])
                        region.end_t = np.float(annots[1])

        wkf_data_request = WorkflowDataRequest()
        #fixme: this should be set based on the audio.label (filename) or given a unique name here...
        wkf_data_request.data_id = label if label else msgutil.get_uuid()
        wkf_data_request.data_type = AUDIO
        wkf_data_request.workflow_data = audio.SerializeToString()
        # consumer_data_label doesn't need to be set... use default
        # set job name?  Currently we assume one job per workflow so punting on this for now

        return wkf_data_request

    def package_text(self, text_input: str, optional_label:str =None, text_workflow_key: str = None) -> WorkflowDataRequest:
        """
        Used to package data for a workflow that accepts string (text) input

        :param text_input: a text input
        :param optional_label: an optional label, namoe or comment associated with this input
        :param text_workflow_key: the keyword used to identify this data in the workflow.  By default a value of 'text' is assumed and recommend

        :return: a WorkflowDataRequest populated with the text input
        """

        text_msg = Text()
        # not (yet?) supported multiple text inputs in a request
        text_msg.text.append(text_input)
        if optional_label:
            text_msg.label = optional_label

        wkf_data_request = WorkflowDataRequest()
        wkf_data_request.data_id = text_workflow_key if text_workflow_key else 'text'
        wkf_data_request.data_type = TEXT
        wkf_data_request.workflow_data = text_msg.SerializeToString()

        return wkf_data_request

    def package_image(self, image_input,
                      mode=olivepy.messaging.msgutil.InputTransferType.SERIALIZED,
                      validate_local_path: bool = True,
                      label=None)-> WorkflowDataRequest:
        """
        Not yet supported

        :param image_input: An image input

        :return: TBD
        """
        media = BinaryMedia()
        msgutil.package_binary_media(media, image_input, mode=mode, validate_local_path=validate_local_path)
        if label:
            media.label = label
        media.motion = False

        # todo if annotations...

        wkf_data_request = WorkflowDataRequest()
        wkf_data_request.data_id = label if label else msgutil.get_uuid()
        wkf_data_request.data_type = IMAGE
        wkf_data_request.workflow_data = media.SerializeToString()

        return wkf_data_request

    def package_workflow_input(self, input_msg,
                       expected_data_type = msgutil.OliveInputDataType.AUDIO_DATA_TYPE) -> WorkflowDataRequest:
        """
        :param input_msg: the OLIVE data message to package
        :param expected_data_type: the data type of the message (Binary
        :return: TBD
        """


        wkf_data_request = WorkflowDataRequest()
        wkf_data_request.data_id = input_msg.label if input_msg.label else msgutil.get_uuid()
        wkf_data_request.data_type = msgutil.data_type_class_map[expected_data_type]
        wkf_data_request.workflow_data = input_msg.SerializeToString()

        return wkf_data_request

    # todo support annotations
    def package_binary(self,
                       binary_input,
                       mode=olivepy.messaging.msgutil.InputTransferType.SERIALIZED,
                       annotations: List[Tuple[float, float]] = None,
                       validate_local_path: bool = True,
                       label=None) -> WorkflowDataRequest:
        """
        :param video_input: a video input

        :return: TBD
        """
        media = BinaryMedia()
        msgutil.package_binary_media(media, binary_input, mode=mode, validate_local_path=validate_local_path)
        if label:
            media.label = label



        wkf_data_request = WorkflowDataRequest()
        wkf_data_request.data_id = label if label else msgutil.get_uuid()
        wkf_data_request.data_type = VIDEO
        wkf_data_request.workflow_data = media.SerializeToString()

        return wkf_data_request

    def get_analysis_class_ids(self, type=WORKFLOW_ANALYSIS_TYPE, callback=None) -> response.OliveClassStatusResponse:
        """
        Query OLIVE for the current class IDs (i.e. speaker names for SID, keywords for QbE, etc).  For tasks that support enrollment, their class IDs can change over time.

        :param type the WorkflowOrder type (WORKFLOW_ANALYSIS_TYPE, WORKFLOW_ENROLLMENT_TYPE, or WORKFLOW_UNENROLLMENT_TYPE)
        :param callback: an optional callback method that accepts a OliveClassStatusResponse object.  Such as: my_callback(result : response.OliveClassStatusResponse)

        :return: an OliveClassStatusResponse object if no callback specified, otherwise the callback receives the OliveClassStatusResponse object when a response is received from the OLIVE server
        """

        class_request = WorkflowClassStatusRequest()
        class_request.workflow_definition.CopyFrom(self.workflow_def)
        if type:
            class_request.type = type

        if callback:
            self.client.enqueue_request(class_request, callback, response.OliveClassStatusResponse())
        else:
            return self.client.sync_request(class_request, response.OliveClassStatusResponse())

    def analyze(self, data_inputs: List[WorkflowDataRequest],
                callback=None,
                options: str = None) -> response.OliveWorkflowAnalysisResponse:
        """
        Perform a workflow analysis

        :param data_inputs:  a list of data inputs created using the package_audio(), package_text(), package_image(), or package_video() method.
        :param callback: an optional callback that is invoked with the workflow completes.  If not specified this method blocks, returning OliveWorkflowAnalysisResponse when done. Otherwise this method immediately returns and the callback method is invoked when the response is received.  The callback method signature requires 3 arguments: requst, result, error_mssage.
        :param options: a JSON string of name/value options to include with the analysis request such as '{"filter_length":99, "interpolate":1.0, "test_name":"midge"}'

        :return: an OliveWorkflowAnalysisResponse (if no callback provided)
        """

        # make call blocking if no callback or always assume it is async?
        analysis_request = WorkflowAnalysisRequest()
        for di in data_inputs:
            analysis_request.workflow_data_input.append(di)
        analysis_request.workflow_definition.CopyFrom(self.workflow_def)

        # Parse options (if any)
        if options:
            jopts = utils.parse_json_options(options)
            analysis_request.option.extend(jopts)

        if callback:
            self.client.enqueue_request(analysis_request, callback, response.OliveWorkflowAnalysisResponse())
        else:
            return self.client.sync_request(analysis_request, response.OliveWorkflowAnalysisResponse())

    def enroll(self, data_inputs: List[WorkflowDataRequest], class_id: str, job_names: List[str], callback=None,
               options=None):
        """
        Submit data for enrollment.

        :param data_inputs:  a list of data inputs created using the package_audio(), package_text(), package_image(), or package_video() method.
        :param class_id:  the name of the enrollment
        :param job_names: a list of job names, where the audio is enrolled with these jobs support enrollment.  This value can be None, in which case the data input(s) is enrolled for each job.
        :param callback: an optional callback that is invoked when the workflow completes.  If not specified this method blocks, returning an OliveWorkflowAnalysisResponse when the enrollment completes on the server.  Otherwise this method immediately returns and the callback method is invoked when the response is received.
        :param options: a dictionary of name/value option pairs to include with the enrollment request

        :return: server enrollment response if no callback provided
        """
        # # first, get the enrollment order
        # for order in self.workflow_def.order:
        #     if order.workflow_type == WORKFLOW_ENROLLMENT_TYPE:
        #         workflow_enrollment_order_msg = order
        #         break
        #
        # if workflow_enrollment_order_msg is None:
        #     raise Exception("This workflow does not contain any ")
        #
        #
        # for name in task_names:

        # make call blocking if no callback or always assume it is async?
        enroll_request = WorkflowEnrollRequest()
        for di in data_inputs:
            enroll_request.workflow_data_input.append(di)
        enroll_request.workflow_definition.CopyFrom(self.workflow_def)
        enroll_request.class_id = class_id

        for job_task in job_names:
            enroll_request.job_names.append(job_task)

        if options:
            jopts = utils.parse_json_options(options)
            enroll_request.option.extend(jopts)

        if callback:
            # self.client.enqueue_request(enroll_request, callback, response.OliveWorkflowEnrollmentResponse())
            self.client.enqueue_request(enroll_request, callback, response.OliveWorkflowAnalysisResponse())
        else:
            return self.client.sync_request(enroll_request, response.OliveWorkflowAnalysisResponse())

    def unenroll(self, class_id: str, job_names: List[str], callback=None, options=None):
        """
        Submit a class id (speaker name, language name, etc) for un-enrollment.

        :param class_id:  the name of the enrollment class to remove
        :param job_names: a list of job names, where the class is to be unenrolled.  Jobs must support class modification .  This value can be None, in which case the data input(s) is unenrolled for each job (which is likely dangerous).
        :param callback: an optional callback that is invoked when this workflow action completes.  If not specified this method blocks, returning an OliveWorkflowAnalysisResponse when the enrollment completes on the server.  Otherwise this method immediately returns and the callback method is invoked when the response is received.
        :param options: a dictionary of name/value option pairs to include with the enrollment request

        :return: server unenrollment response if no callback provided
        """

        # make call blocking if no callback or always assume it is async?
        unenroll_request = WorkflowUnenrollRequest()
        unenroll_request.workflow_definition.CopyFrom(self.workflow_def)
        unenroll_request.class_id = class_id

        for job_task in job_names:
            unenroll_request.job_names.append(job_task)

        if options:
            jopts = utils.parse_json_options(options)
            unenroll_request.option.extend(jopts)

        if callback:
            # self.client.enqueue_request(enroll_request, callback, response.OliveWorkflowEnrollmentResponse())
            self.client.enqueue_request(unenroll_request, callback, response.OliveWorkflowAnalysisResponse())
        else:
            return self.client.sync_request(unenroll_request, response.OliveWorkflowAnalysisResponse())

    def adapt(self, data_input, callback, options=None, finalize=True):
        """
        NOT YET SUPPORTED -- and not sure it will ever be supported via workflow

        :param data_input:
        :param callback:
        :param options:
        :param finalize:

        :return: not supported
        """
        raise Exception("Workflow adaption not supported")

# class OliveAnalysisResult(object):
