
from google.protobuf.json_format import MessageToJson
import olivepy.messaging.msgutil as msgutil

from olivepy.messaging.olive_pb2 import (DATA_OUTPUT_TRANSFORMER_RESULT, SCORE_OUTPUT_TRANSFORMER_RESULT, TEXT, GLOBAL_SCORER_RESULT, CLASS_MODIFICATION_REQUEST, CLASS_REMOVAL_REQUEST)
from olivepy.messaging.workflow_pb2 import (WORKFLOW_ANALYSIS_TYPE, WORKFLOW_ENROLLMENT_TYPE, WORKFLOW_UNENROLLMENT_TYPE, WORKFLOW_ADAPT_TYPE,
                                            WorkflowAnalysisRequest, WorkflowEnrollRequest, WorkflowAdaptRequest,
                                            WorkflowUnenrollRequest, WorkflowActualizeRequest, WorkflowTextResult)
import json

class OliveServerResponse(object):
    """
    The default container/wrapper for responses from an OLIVE server (when using the AsyncOliveClient).  This is
    intended to make it easier for clients to handle the traditional (original) protobuf message results (such as
    RegionScorerResult) returned from the server.

    """

    def __init__(self):
        self._issuccessful = False
        self._iserror = False
        # self.isallowable_error = False
        self._request = None
        self._response = None
        self._message = None


    def parse_from_response(self, request, response, message):
        """
        Create this response from the
        :param request:
        :param response:
        :param message:
        :return:
        """
        self._request = request
        if message:
            # No results due to error
            self._iserror = True
            self._message = message
            # self._request = request
        if response is not None:
            try:
                if response.HasField("error"):
                    self._iserror = True
                    self._message = response.error
                else:
                    # we assume no errors
                    self._issuccessful = True
            except:
                # Some messages have no error field
                self._issuccessful = True
            self._response = response

    def get_workflow_type(self):
        """
        Return the type of workflow done in this response (analysis, enrollment, adaptation)

        :return: A WorkflowType: WORKFLOW_ANALYSIS_TYPE, WORKFLOW_ENROLLMENT_TYPE, WORKFLOW_ADAPT_TYPE or an Exception if an non-workflow response message was wrapped
        """

        if not self._response:
            raise Exception("No valid response")

        if isinstance(self._request, WorkflowAnalysisRequest):
            return WORKFLOW_ANALYSIS_TYPE
        elif isinstance(self._request, WorkflowEnrollRequest):
            return WORKFLOW_ENROLLMENT_TYPE
        elif isinstance(self._request, WorkflowAdaptRequest):
            return WORKFLOW_ADAPT_TYPE
        elif isinstance(self._request, WorkflowUnenrollRequest):
            return WORKFLOW_UNENROLLMENT_TYPE

        raise Exception("Unknown Workflow Message: {}".format(type(self._request)))

    def is_successful(self):
        return self._issuccessful

    def is_error(self):
        return self._iserror

    def get_response(self):
        """
        The Protobuf message returned from the OLIVE server
        :return:
        """
        # todo exception if none?
        return self._response

    def get_error(self):
        return self._message

    def to_json(self, indent=None):
        """
           Generate the response as a JSON string
           :param indent:  if a non-negative integer, then JSON array elements and object members will be pretty-printed with \
           that indent level. An indent level of 0 will only insert newlines. ``None`` is the most compact  \
           representation. A negative value will return the JSON document

           :return: the response as as JSON string:
        """
        #consider setting preserving_proto_field_name to true
        if indent and indent < 0:
            return json.loads(MessageToJson(self._response, preserving_proto_field_name=True))
        return json.dumps(json.loads(MessageToJson(self._response, preserving_proto_field_name=True)), indent=indent, ensure_ascii=False)

    def get_response_as_json(self):
        return MessageToJson(self._response)
        # json_WFD = MessageToJson(self.response)
        # print("Print json  : {}".format(json_WFD))

    def _extract_serialized_message(self, msg_type, msg_data):
        msg = msgutil.type_class_map[msg_type]()
        msg.ParseFromString(msg_data)

        return msg

    def _extract_debug_serialized_message(self, msg_type, msg_data):
        msg = msgutil.type_class_map[msg_type]()
        msg.ParseFromString(msg_data)

        return msg


def get_workflow_jobs(workflow_definition, workflow_type):
    """ parse a workflow definition, returning a dictionary  indexed job (definition) name (job_name) and a list of \
     WorkflowTask elements.

     :param workflow_definition: find jobs in this workflow definition
     :param workflow_type: the type of workflow order (analysis, enrollment, unenrollment)

     return {job_name: [WorkflowTask]} for the requested workflow_type
    """
    rtn_jobs = dict()
    if workflow_definition is not None:
        for order in workflow_definition.order:
            if order.workflow_type == workflow_type:
                for job in order.job_definition:
                    rtn_jobs[job.job_name] = job.tasks

    return rtn_jobs

def get_workflow_job_names(workflow_definition, workflow_type):
    """ parse a workflow definition, returning a list of job definition name  (job_name)

     return [job_name] for the requested workflow_type
    """
    rtn_job_names = list()
    if workflow_definition is not None:
        for order in workflow_definition.order:
            if order.workflow_type == workflow_type:
                for job in order.job_definition:
                    rtn_job_names.append(job.job_name)

    return rtn_job_names


# Kyes used to create a job result dictionary: {KEY_JOB_NAME:job_name, KEY_TASkS:{}, KEY_DATA:[], KEY_ERROR:error_msg}
KEY_JOB_NAME = 'job_name'
KEY_TASkS   = 'tasks'
KEY_DATA    = 'data'
KEY_ERROR   = 'error'


class OliveWorkflowAnalysisResponse(OliveServerResponse):
    """
    The default container/wrapper for responses from an OLIVE server (when using the AsyncOliveClient).  This is
    intended to make it easier for clients to handle the traditional (original) protobuf message results (such as
    RegionScorerResult) returned from the server.

    """
    #
    def __init__(self):
        OliveServerResponse.__init__(self)
        self._isallowable_error = False
        self._job_names = set()
        self._json_result = None

        # A dictionary of jobs that completed with an allowable error, inexed by job_name: {job_name:[task_name]}
        self._allowable_failed_job_tasks = dict()
        # todo this needs to parse result values

    def parse_from_response(self, request, response, message):
        OliveServerResponse.parse_from_response(self, request, response, message)
        # Now create a JSON representation from the response
        # we will walk the tree and deserialize any encoded messages

        if self.is_error():
            self._json_result={}
            self._json_result['error'] = self.get_error()
            return

        # make a new message for this type...
        if isinstance(self._request, WorkflowActualizeRequest):
            # we received an actualized workflow, so se don't need to convert it to json...
            return

        # this should only contain an analysis request... but check just in case:
        wk_type = self.get_workflow_type()
        if wk_type == WORKFLOW_ANALYSIS_TYPE or wk_type == WORKFLOW_ENROLLMENT_TYPE or wk_type == WORKFLOW_UNENROLLMENT_TYPE:
            # analysis is a list of dictionary elements, which looks like: [ {job_name: X, data: [], tasks: {}} ]
            # there is a dictionary for each job, but due to the way jobs work in OLIVE for mulit-channel data we
            # consider a jobs to be unique by a combination of job_name plus data, so multiples dictionary elements may
            # have the same job_name, but will have different data properties (channel numbers)

            # get the analysis job order from the original request:

            analysis_result = []  # or enrollment/unenrollment result
            # analysis_result['jobs'] = []
            # analysis_result['data inputs'] = []
            job_requests = get_workflow_jobs(self._request.workflow_definition, wk_type)
            for job in self._response.job_result:
                # create a dictionary for each job result
                job_dict = dict()
                job_name = job.job_name
                self._job_names.add(job_name)


                # job_dict[job_name] = dict()
                job_dict[KEY_JOB_NAME] = job_name

                if job.error:
                    job_dict['error'] = job.error

                # we have a list of data items:
                job_dict[KEY_DATA] = []
                # and a dictionary of tasks: (although note  it is possible to have multiple tasks with the same name, so a task has a list of results)
                job_dict[KEY_TASkS] = {}
                # add to our results - in most cases we will have just one job
                analysis_result.append(job_dict)

                # get the tasks for the current (and likely, only) job:
                task_requests = get_workflow_job_tasks(job_requests, job_name)
                # task_result_dict = dict()
                # I don't think this can happen yet:
                # if job.HasField('error'):
                #     Allowable job error
                #

                for task in job.task_results:
                    task_result_dict = json.loads(MessageToJson(task, preserving_proto_field_name=True))

                    # check if this task failed with an error
                    if task.HasField('error'):
                        # Allowable error
                        if KEY_ERROR in job_dict:
                            job_dict[KEY_ERROR] = job_dict[KEY_ERROR] + "," + task.error
                        else:
                            job_dict[KEY_ERROR] = task.error
                        self._isallowable_error = True
                        # should have an empty message data;
                        del task_result_dict['message_data']
                        if job_name not in self._allowable_failed_job_tasks:
                            self._allowable_failed_job_tasks[job_name] = []
                        self._allowable_failed_job_tasks[job_name].append(task.task_name)
                    else:
                        # Deserialize message_data, and replace it in the task_result_dict
                        if task.message_type in msgutil.debug_message_map:
                            # Get the pimiento message (debug only - these messages are not guaranteed to be supported
                            print("CLG special msg type: {}".format(msgutil.MessageType.Name(task.message_type)))
                            pimiento_msg = self._extract_serialized_message(task.message_type, task.message_data)
                            if task.message_type == DATA_OUTPUT_TRANSFORMER_RESULT:
                                if pimiento_msg.data_type == TEXT:
                                    # only supported type for now...
                                    pie_data_msg = WorkflowTextResult()
                                    pie_data_msg.ParseFromString(pimiento_msg.message_data)
                                    task_result_dict['analysis'] = json.loads(
                                        MessageToJson(pie_data_msg, preserving_proto_field_name=True))
                                else:
                                    print("Unsupported debug message type: {}".format(msgutil.InputDataType.Name(pimiento_msg.data_type)))
                            elif task.message_type == SCORE_OUTPUT_TRANSFORMER_RESULT:
                                # these should be standard trait message
                                pie_score_msg = self._extract_serialized_message(pimiento_msg.message_type, pimiento_msg.message_data)
                                task_result_dict['analysis'] = json.loads(
                                    MessageToJson(pie_score_msg, preserving_proto_field_name=True))
                        else:
                            task_type_msg = self._extract_serialized_message(task.message_type, task.message_data)
                            task_result_dict['analysis'] = json.loads(
                                MessageToJson(task_type_msg, preserving_proto_field_name=True))


                        # Should we create special handlers for analysis results, like we sort global score results,
                        # but what should be do with an AUDIO_MODIFICATION_RESULT?
                        if task.message_type == GLOBAL_SCORER_RESULT:
                            # Sort region scores
                            task_result_dict['analysis']['score'] = sorted(task_result_dict['analysis']['score'], key=sort_global_scores, reverse=True)

                        # messageData has been replaced with the actual task
                        del task_result_dict['message_data']
                        # taskName is the key, so remove it:
                        del task_result_dict['task_name']

                    # check if we need to add the plugin/domain name
                    if task.task_name in task_requests:
                        orig_task = task_requests[task.task_name]
                        if orig_task.message_type in msgutil.plugin_message_map:
                            # Get the original task
                            task_req_msg = self._extract_serialized_message(orig_task.message_type, orig_task.message_data)
                            task_result_dict['plugin'] = task_req_msg.plugin
                            task_result_dict['domain'] = task_req_msg.domain
                            if orig_task.message_type == CLASS_MODIFICATION_REQUEST or orig_task.message_type == CLASS_REMOVAL_REQUEST:
                                # add class ID
                                task_result_dict['class_id'] = self._request.class_id

                    # print("adding {} as {}".format(task_result_dict, task.task_name))
                    # fixme: there can be multiple taks with the same name if conditions in a workflow caused the task to be ran twice
                    if task.task_name not in job_dict[KEY_TASkS]:
                        job_dict[KEY_TASkS][task.task_name] = []

                    job_dict[KEY_TASkS][task.task_name].append(task_result_dict)
                    #job_dict[KEY_TASkS].append({task.task_name: task_result_dict})

                # A job usually has one data input/output, but we handle as if there are multiple
                for data_result in job.data_results:
                    data_result_dict = json.loads(MessageToJson(data_result, preserving_proto_field_name=True))
                    # Deserialize the data portion
                    data_type_msg = self._extract_serialized_message(data_result.msg_type, data_result.result_data)
                    del data_result_dict['result_data']
                    # del data_result_dict['dataId'] # redundant into, used as the key
                    # data_result_dict['data'] = json.loads(MessageToJson(data_type_msg))
                    data_result_dict.update(json.loads(MessageToJson(data_type_msg, preserving_proto_field_name=True)))
                    job_dict[KEY_DATA].append(data_result_dict)
                    # analysis_result['data inputs'].append(data_result_dict)

            self._json_result = analysis_result

    def is_allowable_error(self):
        """
        :return: true if this response failed with an allowable error
        """
        return self._isallowable_error

    def _get_default_job_name(self, multiple_jobs_allowed=False):
        """
        Find and return the first job name.  Since most requests only have one job, this is a helper function that
        lets quick access the one and only job name.

        :param multiple_jobs_allowed:  if False (default) then an exception is thrown if there are multiple jobs in
        this analysis request.

        :return: the default job_name, assuming there is one and only one job in this analysis workflow
        """

        # We assume we have at least one job
        job_name = list(self._job_names)[0]
        if len(self._job_names) == 1:
            # there is one and only one job, so no problem here:
             return job_name

        if not multiple_jobs_allowed:
            raise Exception("Workflow Analysis contains multiple jobs in a Workflow")

        return job_name

    def get_failed_tasks(self, job_name=None):
        if self.is_allowable_error():
            if job_name is None:
                job_name = self._get_default_job_name()
            if (job_name) in self._allowable_failed_job_tasks:
                return self._allowable_failed_job_tasks[job_name]

        return []

    def get_analysis_jobs(self):
        """
        Return the names of analysis jobs.  Typically a workflow has just one job with multiple tasks, the most likely
        reason to have multiple jobs is for workflows using multi-channel audio so there may be a set of job tasks for
        each channel of audio submitted.

        :return: a list of job names in the analysis
        """
        return [job_dict[KEY_JOB_NAME] for job_dict in self._json_result]


        #todo get analysis job name(s)

    def get_analysis_tasks(self, job_name=None):
        if job_name is None:
            job_name = self._get_default_job_name()

        rtn_list = set()
        for job_dict in self._json_result:
            if job_name == job_dict[KEY_JOB_NAME]:
                rtn_list.update(job_dict[KEY_TASkS].keys())

        # return as list? return job_name?
        return rtn_list

    def get_analysis_task_result(self, job_name, task_name):
        """
        Get the result(s) for the specified job_name and task_name, also include the data used for this task.  If
        the workflow analyzes each channel in multi-channel data then there can be multiple jobs with the
        same name.

        :param job_name: for convenience can be None, since there is normally only one job.  But if the workflow has multiple jobs then a valid name must be specified.
        :param task_name: the name to the task

        :return: a list of dictionaries, where each dictionary in the list includes the results for the specified task and a list of the data analyzed by this task, such as [ {task_name:{}, data:[] }]
        """
        if job_name is None:
            job_name = self._get_default_job_name()

        results = []
        for job_dict in self._json_result:
            if job_name == job_dict[KEY_JOB_NAME]:
                task_dict = dict()
                # there may be one or more result for task_name
                task_dict[task_name] = job_dict[KEY_TASkS][task_name]
                task_dict[KEY_DATA] = job_dict[KEY_DATA]
                results.append(task_dict)

        return results

    def get_analysis_job_result(self, job_name=None):
        f"""
        Get the result for the specified job_name and task_name, also include the data used for this task.  If
        the workflow analyzes each channel in multi-channel data then there can be multiple jobs with the
        same name.

        :param job_name: for convenience can be None, since there is normally only one job.  But if the workflow has\
        multiple jobs then a valid name must be specified.


        :return: a list of dictionaries, where each dictionary in the list includes the results for the specified\
        job, a list of the data analyzed by this job's tasks and the tasks,
        """
        if job_name is None:
            job_name = self._get_default_job_name()

        results = []
        for job_dict in self._json_result:
            if job_name == job_dict[KEY_JOB_NAME]:
                results.append(job_dict)
                # task_dict = dict()
                # task_dict[task_name] = job_dict[KEY_TASkS][task_name]
                # task_dict[KEY_DATA] = job_dict[KEY_DATA]
                # results.append(task_dict)

        return results


    # todo make private???
    def get_request_jobs(self, workflow_type):
        """
        return the jobs in the original request for the specified analysis type

        :param workflow_type: the type of workflow (i.e. WORKFLOW_ANALYSIS_TYPE)

        :return: the list of jobs for this type
        """
        if self._request is not None:
            return get_workflow_jobs(self._request.workflow_definition, workflow_type)

        raise Exception("No jobs for the requested workflow type: {}".format(workflow_type))

    def to_json(self, indent=None):
        """
           Generate the workflow as a JSON string
           :indent:  if a non-negative integer, then JSON array elements and object members will be pretty-printed with \
           that indent level. An indent level of 0 will only insert newlines. ``None`` is the most compact  \
           representation. A negative value will return the JSON document

           :return: the Workflow Definition as as JSON string:
        """
        # return json.dumps(self._json_result, indent=indent, ensure_ascii=False)

        if indent and indent < 0:
            return json.loads(MessageToJson(self._json_result, preserving_proto_field_name=True))
        # return json.dumps(json.loads(MessageToJson(self._json_result, preserving_proto_field_name=True)), indent=indent, ensure_ascii=False)
        return json.dumps(self._json_result, indent=indent, ensure_ascii=False)

    def get_response_as_json(self):
        if self.is_error():
            raise Exception(self._message)

        # support other formats/schemas?
        return self._json_result


class OliveWorkflowEnrollmentResponse(OliveServerResponse):
    """
    The  container/wrapper for responses from an OLIVE server (when using the AsyncOliveClient) for enrollment.  This is
    intended to make it easier for clients to handle the traditional (original) protobuf message results (such as
    RegionScorerResult) returned from the server.

    """
    #
    def __init__(self):
        OliveServerResponse.__init__(self)
        self._isallowable_error = False
        self._job_names = set()
        self._json_result = None

        # A dictionary of jobs that completed with an allowable error, inexed by job_name: {job_name:[task_name]}
        self._allowable_failed_job_tasks = dict()
        # todo this needs to parse result values

    def parse_from_response(self, request, response, message):
        OliveServerResponse.parse_from_response(self, request, response, message)

        # Now create a JSON representation from the response
        # we will  the tree and deserialize any encoded messages

        if self.is_error():
            # todo provide error info in JSON
            return

        # make a new message for this type...
        if isinstance(self._request, WorkflowActualizeRequest):
            # we received an actualized workflow, so se don't need to convert it to json...
            return

        # this should only contain an analysis request... but check just in case:
        type = self.get_workflow_type()
        if type == WORKFLOW_ENROLLMENT_TYPE:
            # not much info in an enrollment response...

            enroll_result = dict()
            if self._response.HasField('error'):
                enroll_result['error'] = self._response.error
            else:
                enroll_result['successful'] = True

            self._json_result = enroll_result


    def is_allowable_error(self):
        """

        :return: true if this message failed with an allowable error
        """
        return self._isallowable_error

    def _get_default_job_name(self, multiple_jobs_allowed=False):
        """
        Find and return the first job name.  Since most requests only have one job, this is a helper function that
        lets quick access the one and only job name.

        :param multiple_jobs_allowed:  if False (default) then an exception is thrown if there are multiple jobs in
        this analysis request.

        :return: the default job_name, assuming there is one and only one job in this analysis workflow
        """

        # We assume we have at least one job
        job_name = list(self._job_names)[0]
        if len(self._job_names) == 1:
            # there is one and only one job, so no problem here:
             return job_name

        if not multiple_jobs_allowed:
            raise Exception("Workflow Analysis contains multiple jobs in a Workflow")

        return job_name

    def to_json(self, indent=None):
        """
           Generate the response as a JSON string
           :param indent:  if a non-negative integer, then JSON array elements and object members will be pretty-printed with \
           that indent level. An indent level of 0 will only insert newlines. ``None`` is the most compact  \
           representation. A negative value will return the JSON document

           :return: the Workflow response as as JSON string:
        """
        #consider setting preserving_proto_field_name to true
        # return json.dumps(json.loads(MessageToJson(self._json_result, preserving_proto_field_name=True)), indent=indent)

        if indent and indent < 0:
            return json.loads(MessageToJson(self._json_result, preserving_proto_field_name=True))
        return json.dumps(self._json_result, indent=indent, ensure_ascii=False)


    def get_response_as_json(self):
        if self.is_error():
            raise Exception(self._message)

        # format?
        return self._json_result


class OliveClassStatusResponse(OliveServerResponse):
    """
    The  container/wrapper for WorkflowClassStatusResult from an OLIVE server (when using the AsyncOliveClient).  This is
    intended to make it easier for clients to handle the traditional (original) protobuf message results (such as
    RegionScorerResult) returned from the server.

    """
    #
    def __init__(self):
        OliveServerResponse.__init__(self)
        self._isallowable_error = False
        self._job_names = set()
        self._json_result = None

        # A dictionary of jobs that completed with an allowable error, inexed by job_name: {job_name:[task_name]}
        self._allowable_failed_job_tasks = dict()
        # todo this needs to parse result values

    def get_workflow_type(self):
        return WORKFLOW_ANALYSIS_TYPE

    def parse_from_response(self, request, response, message):
        OliveServerResponse.parse_from_response(self, request, response, message)

        if self.is_error():
            return

        status_result = []
        for jc in self._response.job_class:
            job_name = jc.job_name
            job_dict = dict()
            self._job_names.add(job_name)

            job_dict[KEY_JOB_NAME] = job_name
            # we have a list of data items:
            job_dict[KEY_TASkS] = {}
            status_result.append(job_dict)

            for task_class in jc.task:
                task_class_dict = json.loads(MessageToJson(task_class, preserving_proto_field_name=True))
                del task_class_dict['task_name']
                if task_class.task_name not in job_dict[KEY_TASkS]:
                    job_dict[KEY_TASkS][task_class.task_name] = []
                job_dict[KEY_TASkS][task_class.task_name].append(task_class_dict)
                #job_dict[KEY_TASkS].append({task_class.task_name: task_class_dict})

        self._json_result = status_result

    def to_json(self, indent=None):
        """
           Generate the response as a JSON string
           :param indent:  if a non-negative integer, then JSON array elements and object members will be pretty-printed with \
           that indent level. An indent level of 0 will only insert newlines. ``None`` is the most compact  \
           representation. A negative value will return the JSON document

           :return: the Workflow response as as JSON string:
        """
        # consider setting preserving_proto_field_name to true
        # return json.dumps(json.loads(MessageToJson(self._response, preserving_proto_field_name=True)), indent=indent)

        if self.is_error():
            return self.get_error()
        if indent and indent < 0:
            return json.loads(MessageToJson(self._response, preserving_proto_field_name=True))
        return json.dumps(json.loads(MessageToJson(self._response, preserving_proto_field_name=True)), indent=indent, ensure_ascii=False)

    def get_response_as_json(self):
        if self.is_error():
            raise Exception(self._message)

        # support other formats/schemas?
        return self._json_result


def sort_global_scores(score):
    return score['score']

# Helper functions:
def get_workflow_job_tasks(jobs, job_name=None):
    """
    Fetch the tasks from a job

    :param jobs: a dictionary of WorkflowTasks, indexed by a job names
    :param job_name: find tasks that belong to a job having this name. This can be None if there is only one job
    :return: a dictionary of WorkflowTask indexed by the task's consumer_result_label for the specified job.  An exception is thrown if there are multiple jobs but no job_name was specified
    """

    if job_name is None:
        if len(jobs) == 1:
            # hack to make it easier to fetch data since most workflows only have one job
            job_name = list(jobs.keys())[0]
        else:
            raise Exception("Must specify a job name when there are multiple JobDefinitions in a Workflow")

    rtn_tasks = dict()
    #  and job_name is None:
    if job_name in jobs:
        for workflow_task in jobs[job_name]:
            rtn_tasks[workflow_task.consumer_result_label] = workflow_task
    # CLG: I don't think we need to generate a message/warning if a task name isn't in the original jobs since we can
    # have dynamic jobs and with streaming jobs are very dynamic
    # else:
    #     print("Job '{}' not one of the expected job names: {}".format(job_name, list(jobs.keys())))

    return rtn_tasks

# def get_workflow_job_task(workflow_definition, workflow_type, job_name, task_name):
#     rtn_jobs = dict()
#     if workflow_definition is not None:
#         for order in workflow_definition.order:
#             if order.workflow_type == workflow_type:
#                 for job in order.job_definition:
#                     rtn_jobs[job.job_name] = job.tasks
#
#     return  rtn_jobs

class OliveWorkflowActualizedResponse(OliveServerResponse):
    """
    Extracts info from an actualized workflow definition

    """
    #
    def __init__(self):
        OliveServerResponse.__init__(self)
        self._isallowable_error = False
        self._job_names = set()
        self._json_result = None

        # A dictionary of jobs that completed with an allowable error, indexed by job_name: {job_name:[task_name]}
        self._allowable_failed_job_tasks = dict()
        # todo this needs to parse result values

    def parse_from_response(self, request, response, message):
        OliveServerResponse.parse_from_response(self, request, response, message)

        # Now create a JSON representation from the response
        # we will walk the tree and deserialize any encoded messages

        if self.is_error():
            # todo provide error info in JSON?
            return

            # make a new message for this type...
        if not isinstance(self._request, WorkflowActualizeRequest):
            # we received an some other workflow message so se don't need to convert it to json...
            return

        # we only parse the analyze part now

        analysis_task = []

        workflow_analysis_order_msg = None
        for order in self._response.workflow.order:
            if order.workflow_type == WORKFLOW_ANALYSIS_TYPE:
                workflow_analysis_order_msg = order
                break

        if workflow_analysis_order_msg is None:
            # no analysis results
            return

        # for job in self._response.job_result:
        for job in workflow_analysis_order_msg.job_definition:
            # create a dictionary for each job result
            job_dict = dict()
            job_name = job.job_name
            self._job_names.add(job_name)

            # and a dictionary of tasks:
            # job_dict[KEY_TASkS] = {}
            # add to our results - in most cases we will have just one job
            analysis_task.append(job_dict)

            # get data handling info for this job
            data_prop = job.data_properties
            job_dict['Data Input'] = json.loads(MessageToJson(data_prop, preserving_proto_field_name=True))
            # if data_prop.mode == SPLIT:
            #     # Hack to make split/mulit-channel mode more clear
            #     job_dict['data']['mode'] = 'SPLIT: Process each channel as a job'

            for task in job.tasks:
                task_result_dict = json.loads(MessageToJson(task, preserving_proto_field_name=True))

                # Deserialize message_data, and replace it in the task_result_dict
                task_type_msg = self._extract_serialized_message(task.message_type, task.message_data)
                task_result_dict[KEY_JOB_NAME] = job_name
                task_result_dict['analysis'] = json.loads(MessageToJson(task_type_msg, preserving_proto_field_name=True))
                del task_result_dict['message_data']

                job_dict[task.consumer_result_label] = task_result_dict

        self._json_result = analysis_task

    def is_allowable_error(self):
        """

        :return: true if this response failed with an allowable error
        """
        return self._isallowable_error

    def _get_default_job_name(self, multiple_jobs_allowed=False):
        """
        Find and return the first job name.  Since most requests only have one job, this is a helper function that
        lets quick access the one and only job name.

        :param multiple_jobs_allowed:  if False (default) then an exception is thrown if there are multiple jobs in
        this analysis request.

        :return: the default job_name, assuming there is one and only one job in this analysis workflow
        """

        # We assume we have at least one job
        job_name = list(self._job_names)[0]
        if len(self._job_names) == 1:
            # there is one and only one job, so no problem here:
             return job_name

        if not multiple_jobs_allowed:
            raise Exception("Workflow Analysis contains multiple jobs in a Workflow")

        return job_name

    def get_analysis_jobs(self):
        """
        Return the names of analysis jobs.  Typically a workflow has just one job with multiple tasks, the most likely
        reason to have multiple jobs is for workflows using multi-channel audio so there may be a set of job tasks for
        each channel of audio submitted.

        :return: a list of job names in the analysis
        """
        return [job_dict[KEY_JOB_NAME] for job_dict in self._json_result]


        #todo get analysis job name(s)

    def get_analysis_tasks(self, job_name=None):
        if job_name is None:
            job_name = self._get_default_job_name()

        rtn_list = set()
        for job_dict in self._json_result:
            if job_name == job_dict[KEY_JOB_NAME]:
                rtn_list.update(job_dict[KEY_TASkS].keys())

        # return as list? return job_name?
        return rtn_list

    #fixme get options for task
    # def get_supported_options(self, job_name=None):
    #     """Return options supported for each job/task in the workflolw """
    #     if job_name is None:
    #         job_name = self._get_default_job_name()
    #
    #     rtn_opts = dict()
    #     for job_dict in self._json_result:
    #         if job_name == job_dict[KEY_JOB_NAME]:
    #             rtn_list.update(job_dict[KEY_TASkS].keys())


    def get_workflow(self):
        return self._response.workflow

    # todo make private???
    def get_request_jobs(self, workflow_type):
        """
        return the jobs in the original request for the specified analysis type

        :param workflow_type: the type of workflow (i.e. WORKFLOW_ANALYSIS_TYPE)

        :return: the list of jobs for this type
        """
        if self._request is not None:
            return get_workflow_jobs(self._request.workflow_definition, workflow_type)

        raise Exception("No jobs for the requested workflow type: {}".format(workflow_type))

    def to_json(self, indent=None):
        """
           Generate the response as a JSON string
           :param indent:  if a non-negative integer, then JSON array elements and object members will be pretty-printed with \
           that indent level. An indent level of 0 will only insert newlines. ``None`` is the most compact  \
           representation. A negative value will return the JSON document

           :return: the Workflow response as as JSON string:
        """
        if indent and indent < 0:
            return json.loads(MessageToJson(self._json_result, preserving_proto_field_name=True))
        return json.dumps(self._json_result, indent=indent, ensure_ascii=False)
        # return json.dumps(json.loads(MessageToJson(self._json_result, preserving_proto_field_name=False)), indent=indent)

    def get_response_as_json(self):
        if self.is_error():
            raise Exception(self._message)

        # support other formats/schemas?
        return self._json_result