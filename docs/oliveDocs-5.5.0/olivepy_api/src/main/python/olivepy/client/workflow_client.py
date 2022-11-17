
import argparse, os, json, sys
import logging
import olivepy.api.olive_async_client as oc
import olivepy.client.client_common as client_com
import olivepy.api.workflow as ow
from olivepy.utils.pem import Pem
from olivepy.utils import utils
from olivepy.messaging.msgutil import AudioTransferType
from olivepy.messaging.msgutil import OliveInputDataType
import olivepy.messaging.olive_pb2 as olive_pb2



def heartbeat_notification(heatbeat):
    """
    Callback method, notified by the async client that a heartbeat message has been received from the OLIVE server
    """

    if heatbeat:
        if heatbeat.HasField("stats"):
            stats = heatbeat.stats
            print("System CPU Used:    %02.01f%%" % stats.cpu_percent)
            print("System CPU Average: %02.01f%%" % stats.cpu_average)
            print("System MEM Used:    %02.01f%%" % stats.mem_percent)
            print("System MEM Max:     %02.01f%%" % stats.max_mem_percent)
            print("System SWAP Used:   %02.01f%%" % stats.swap_percent)
            print("System SWAP Max:    %02.01f%%" % stats.max_swap_percent)
            print("Number active jobs: " + str(stats.pool_busy))
            print("Number pending jobs: " + str(stats.pool_pending))
            print("Number finished jobs: " + str(stats.pool_finished))
            print("Max number jobs: " + str(stats.max_num_jobs))
            print("Server version: " + str(stats.server_version))
            print("\n")
    else:
        print("Too long since a heatbeat message was received.  Olive server or connection down")

def main():
    """
    Client for interacting with the workflow API
    """
    parser = argparse.ArgumentParser(prog='olivepyworkflow', description="Perform OLIVE analysis using a Workflow "
                                                                         "Definition file")
    
    # Required positional option
    parser.add_argument('workflow', action='store',
                        help='The workflow definition to use.')

    parser.add_argument('--tasks', action='store_true',
                        help='Print the workflow analysis tasks.')

    parser.add_argument('--class_ids', action='store_true',
                        help='Print the class IDs available for analysis in the specified workflow.')

    parser.add_argument('--print_actualized', action='store_true',
                        help='Print the actualized workflow info.')

    parser.add_argument('--print_workflow', action='store_true',
                        help='Print the workflow definition file info (before it is actualized, if requested)')
    #
    # parser.add_argument('--print_options', action='store_true',
    #                     help='Print the options recognized for each task')

    parser.add_argument('-s', '--server', action='store', default='localhost',
                        help='The machine the server is running on. Defaults to %(default)s.')
    parser.add_argument('-P', '--port', type=int, action='store', default=5588,
                        help='The port to use.')
    parser.add_argument('-t', '--timeout', type=int, action='store', default=10,
                        help='The timeout (in seconds) to wait for a response from the server ')

    parser.add_argument('-i', '--input', action='store',
                        help='The data input to analyze.  Either a pathname to an audio/image/video file or a string for text input.  For text input, also specify the --text flag')
    parser.add_argument('--input_list', action='store',
                        help='A list of files to analyze. One file per line.')
    parser.add_argument('--text', action='store_true',
                        help='Indicates that input (or input list) is a literal text string to send in the analysis request.')


    parser.add_argument('--options', action='store',
                        help='A JSON formatted string of workflow options such as '
                             '[{"task":"SAD", "options":{"filter_length":99, "interpolate":1.0}] or '
                             '{"filter_length":99, "interpolate":1.0, "name":"midge"}, where the former '
                             'options are only applied to the SAD task, and the later are applied to all tasks ')
    parser.add_argument('--path', action='store_true',
                        help='Send the path of the audio instead of a buffer.  '
                             'Server and client must share a filesystem to use this option')
    #
    # parser.add_argument('--heartbeat', action='store_true',
    #                     help='Listen for server heartbeats ')
    # parser.add_argument('--status', action='store_true',
    #                     help='get server status')
    parser.add_argument('--debug', action='store_true',
                        help='Debug mode ')

    # Not supported since it needs an additional 3rd party lib:
    # parser.add_argument('--decoded', action='store_true',
    #                     help='Send audio file as decoded PCM16 samples instead of sending as serialized buffer. '
    #                          'Input file must be a wav file')

    
    args_bad = False
    args = parser.parse_args()

    # Simple logging config
    if args.debug:
        log_level = logging.DEBUG
    else:
        log_level = logging.INFO
        # log_level = logging.WARN
    logging.basicConfig(level=log_level)

    if args.workflow is None :
        print('No workflow definition is specified.')
        args_bad = True


    if (args.tasks or args.class_ids or args.print_actualized or args.print_workflow):
        data_required = False
    else:
        data_required = True

    # Our workflow should consume one of the 4 data types (but not a combination of types) ....
    # data_input, audio_mode, send_pathname, audio, text, image, video = client_com.extract_input_data_type(args, fail_if_no_data=data_required)

    if args.text:
        # special case of handling text data
        expected_data_type = OliveInputDataType.TEXT_DATA_TYPE
    else:
        expected_data_type = OliveInputDataType.BINARY_DATA_TYPE
        # expected_data_type = OliveInputDataType.AUDIO_DATA_TYPE  # if you only want to send audio
    data_input, transfer_mode, send_pathname = client_com.extract_input_data(args, expected_data_type=expected_data_type, fail_if_no_data=data_required)


    json_opts = None
    if args.options:
        json_opts = args.options
        print("Options: {}".format(json_opts))

    if args_bad:
        print('Run the command with --help or -h to see all the command line options.')
        quit(1)

    enable_status_socket =  False
    # Create the connection to the OLIVE server
    client = oc.AsyncOliveClient("olivepy_workflow", args.server, args.port, args.timeout)
    client.connect(monitor_status=enable_status_socket)
    try:
        # if args.heartbeat:
        #     # Register to be notified of heartbeats from the OLIVE server
        #     client.add_heartbeat_listener(heartbeat_notification)

        # if args.status:
        #     # Request the current server status
        #     server_status_response = client.get_status()
        #     if server_status_response.is_successful():
        #         print("OLIVE JSON Server status: {}".format(server_status_response.to_json(indent=10)))
        #         #
        #         # Or you can access the GetStatusResult protobuf:
        #         print("OLIVE Server status: pending: {}, busy: {}, finished: {}, version: {}"
        #               .format(server_status_response.get_response().num_pending,
        #                       server_status_response.get_response().num_busy,
        #                       server_status_response.get_response().num_finished,
        #                       server_status_response.get_response().version))


        # first, create the workflow definition from the workflow file:
        workflow_def = ow.OliveWorkflowDefinition(args.workflow)

        if args.print_workflow:
            wdef_json = workflow_def.to_json(indent=1)
            print("Workflow Definition: \n{}".format(wdef_json))
            print("")

        # Submit that workflow definition to the client for actualization (instantiation):
        workflow = workflow_def.create_workflow(client)

        if args.print_actualized:
            # tasks_json = workflow.get_analysis_task_info()
            print("Actualized Workflow: {}".format(workflow.to_json(indent=1)))
            print("")


        if args.tasks:
            #  Print the analysis tasks:
            print("Analysis Tasks: {}".format(workflow.get_analysis_tasks()))

            for enroll_job_name in workflow.get_enrollment_job_names():
                print("Enrollment job '{}' has Tasks: {}".format(enroll_job_name, workflow.get_enrollment_tasks(enroll_job_name)))
            for unenroll_job_name in workflow.get_unenrollment_job_names():
                print("Unenrollment job '{}' has Tasks: {}".format(unenroll_job_name, workflow.get_unenrollment_tasks(unenroll_job_name)))

        if args.class_ids:
            #  Print the class IDs available for the workflow tasks:
            # support other types: type=olive_pb2.WORKFLOW_ENROLLMENT_TYPE?
            class_status_response = workflow.get_analysis_class_ids()

            print("Class Info: {}".format(class_status_response.to_json(indent=1)))

        buffers = []
        for input in data_input:
            buffers.append(workflow.package_workflow_input(input, expected_data_type))

        if  (data_required):
            print("Sending analysis request...")
            response = workflow.analyze(buffers, options=json_opts)

            print("Workflow analysis results:")
            print("{}".format(response.to_json(indent=1)))


    except Exception as e:
        print("Workflow failed with error: {}".format(e))
    finally:
        client.disconnect()



if __name__ == '__main__':
    sys.exit(main())
