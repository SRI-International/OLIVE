
import argparse, json, sys, traceback
from os import sep
import logging
from pathlib import Path
from datetime import datetime
import olivepy.api.olive_async_client as oc
import olivepy.client.client_common as client_com
import olivepy.api.workflow as ow
from olivepy.utils.pem import Pem
from olivepy.utils import utils
import olivepy.messaging.response as response
from olivepy.messaging.msgutil import AudioTransferType
from olivepy.messaging.msgutil import OliveInputDataType
import olivepy.messaging.olive_pb2 as olive_pb2
import olivepy.messaging.workflow_pb2 as workflow_pb2


responses = []


def stream_result_notification(response: response.OliveWorkflowAnalysisResponse):
    resp_as_json = response.get_response_as_json()
    for r in resp_as_json:
        print(json.dumps(r, indent=2))
    responses.append(response)


def main():
    """
    Client for interacting with the streaming API
    """
    parser = argparse.ArgumentParser(prog='olivepystream', description="Perform OLIVE streaming using a Workflow "
                                                                         "Definition file")
    
    parser.add_argument('-s', '--server', action='store', default='localhost',
                        help='The machine the server is running on. Defaults to %(default)s.')
    parser.add_argument('-p', '--port', type=int, action='store', default=5588,
                        help='The port to use.')

    parser.add_argument('--workflow', action='store', type=str, help='The workflow definition to use.')
    parser.add_argument('--output', action='store', type=str, help='The directory to write any output')
    parser.add_argument('--shutdown', action='store_true',
                        help='Stop the OLIVE server (including any active streaming sessions).')
    parser.add_argument('--stop', action='store_true',
                        help='Stop all active streaming sessions.')  # fixme allow session id so a specific session can be stopped
    parser.add_argument('-f', '--flush', action='store', type=str, help='Flush streaming session ID.')
    parser.add_argument('--path', action='store', type=str)
    parser.add_argument('-t', '--timeout', type=int, action='store', default=10,
                        help='The timeout (in seconds) to wait for a response from the server ')
    parser.add_argument('-i', '--input', action='store',
                        help='The data input to analyze.  Either a pathname to an audio/image/video file or a string for text input.  For text input, also specify the --text flag')
    parser.add_argument('--input_list', action='store',
                        help='A list of files to analyze. One file per line.')
    parser.add_argument('--client', action='store', default='olivepystream',
                        help='The name of this streaming client.  Defaults to %(default)s.')
    # parser.add_argument('--options', action='store',
    #                     help='A JSON formatted string of workflow options such as '
    #                          '[{"task":"SAD", "options":{"filter_length":99, "interpolate":1.0}] or '
    #                          '{"filter_length":99, "interpolate":1.0, "name":"midge"}, where the former '
    #                          'options are only applied to the SAD task, and the later are applied to all tasks ')

    args_bad = False
    args = parser.parse_args()

    log_level = logging.INFO
    logging.basicConfig(level=log_level)

    client_id = args.client

    if (args.stop or args.flush or args.shutdown):
        data_required = False
    elif args.workflow is None:
            print('No workflow definition is specified.')
            args_bad = True

    outputDirName = ''
    if args.output:
        Path(args.output).mkdir(parents=True, exist_ok=True)
        outputDirName = args.output

    # we are only handling audio data for now
    # expected_data_type = OliveInputDataType.BINARY_DATA_TYPE
    expected_data_type = OliveInputDataType.AUDIO_DATA_TYPE
    if args.workflow:
        data_required = True
        data_input, transfer_mode, send_pathname = client_com.extract_input_data(args, expected_data_type=expected_data_type, fail_if_no_data=data_required)


    # json_opts = None
    # if args.options:
    #     json_opts = args.options
    #     print("Options: {}".format(json_opts))

    if args_bad:
        print('Run the command with --help or -h to see all the command line options.')
        quit(1)

    enable_status_socket =  False
    # Create the connection to the OLIVE server
    client = oc.AsyncOliveClient("olivepy_stream", args.server, args.port, args.timeout)
    client.connect(monitor_status=enable_status_socket)
    stream = None

    if args.shutdown:
        client.request_shutdown()
        client.disconnect()
        quit(0)

    if args.stop:
        client.request_stop_stream(None)
        client.disconnect()
        quit(0)

    if args.flush:
        client.request_flush_stream(args.flush)

    try:
        if not data_required:
            print('Exiting...')
        else:
            still_streaming = True
            workflow_def = ow.OliveWorkflowDefinition(args.workflow)
            # Submit that workflow definition to the client for actualization (instantiation):
            workflow = workflow_def.create_workflow(client)

            # only one input..
            buffers = []
            # for input in data_input:
            #     print("Adding input type: {}".format(type(input)))
            #     buffers.append(workflow.package_workflow_input(input, expected_data_type))

            # todo configure sample rate
            session_id, stream_port = workflow.stream(client_id, 16000)
            print("Started a new streaming session '{}', using port {}".format(session_id, stream_port))
            stream = oc.StreamOliveClient(client_id, stream_port, address=args.server)
            stream.add_streaming_callback(client_id, stream_result_notification)
            stream.connect()

            for i in data_input:
                stream.enqueue_data(i)
                print('Sent audio....')
            
            while still_streaming:
                c = input("Press f to flush the stream, r to send audio, q to quit the streaming session, w to write to disk")
                if c == 'f':
                    client.request_flush_stream(session_id)
                elif c == 'q':
                    client.request_stop_stream(session_id)
                    still_streaming = False
                elif c == 'r':
                    for i in data_input:
                        stream.enqueue_data(i)
                        print('Sent audio....')
                elif c == 'w':
                    outputFilename = write_results(outputDirName, responses)
                    print(f'Wrote output report to: {outputFilename}')

            print('Streaming finished')

    except Exception as e:
        print("Workflow failed with error: {}".format(e))
        traceback.print_exc()
    finally:
        client.disconnect()
        if stream:
            stream.disconnect()


def write_results(path, responses):
    errors = {}
    processed_results = {}
    for response in responses:
        tasks = response.get_analysis_tasks()
        for t in tasks:
            result = response.get_analysis_task_result(job_name=None, task_name=t)
            for res_arr in result:
                for r in res_arr[t]:
                    # TODO convert these to comparing to constants
                    #... but it looks like everything is converted away from
                    # protobufs (and into dicts) before this point?
                    if 'error' in r:
                        errors[t] = r['error'] + '\n'
                    elif r['message_type'] == 'REGION_SCORER_STREAMING_RESULT':
                        if 'REGION_SCORER_STREAMING_RESULT' not in processed_results:
                            processed_results['REGION_SCORER_STREAMING_RESULT'] = {}
                        if t not in processed_results['REGION_SCORER_STREAMING_RESULT']:
                            processed_results['REGION_SCORER_STREAMING_RESULT'][t] = []
                        processed_results['REGION_SCORER_STREAMING_RESULT'][t].append("{}\t{}\t{}\t{}\t{}\t{}\t{}\t{}\n".format('Region Start', 'Region End', 'Class ID', 'Score', 'Stream Start', 'Stream End', 'Group_Label', 'Offset'))
                        for stream_region in r['analysis']['stream_region']:
                            offset = 0
                            if 'offset_t' in stream_region:
                                offset = stream_region['offset_t']
                            if 'region' in stream_region:
                                region_arr = stream_region['region']
                                for region in region_arr:
                                    processed_results['REGION_SCORER_STREAMING_RESULT'][t].append("{}\t{}\t{}\t{}\t{}\t{}\t{}\t{}\n".format(region['start_t'], region['end_t'], region['class_id'], region['score'], stream_region['start_t'], stream_region['end_t'], stream_region['label'], offset))

                    # TODO I can't find 'Stream Start', 'Stream End', 'Offset' in the python results but they are in OliveStream.java?
                    elif r['message_type'] == 'GLOBAL_SCORER_STREAMING_RESULT':
                        if 'GLOBAL_SCORER_STREAMING_RESULT' not in processed_results:
                            processed_results['GLOBAL_SCORER_STREAMING_RESULT'] = {}
                        if t not in processed_results['GLOBAL_SCORER_STREAMING_RESULT']:
                            processed_results['GLOBAL_SCORER_STREAMING_RESULT'][t] = []
                        processed_results['GLOBAL_SCORER_STREAMING_RESULT'][t].append("{}\t{}\n".format('ID', 'Score'))#, 'Stream Start', 'Stream End', 'Offset'))

                        for score in r['analysis']['score']:
                            offset = 0
                            if 'offset_t' in score:
                                offset = score['offset_t']
                            processed_results['GLOBAL_SCORER_STREAMING_RESULT'][t].append('{}\t{}\n'.format(score['class_id'], score['score']))#, r['analysis']['start_t'], r['analysis']['end_t'], offset))
                    elif r['message_type'] == 'TEXT_TRANSFORM_RESULT':
                        if 'TEXT_TRANSFORM_RESULT' not in processed_results:
                            processed_results['TEXT_TRANSFORM_RESULT'] = {}
                        if t not in processed_results['TEXT_TRANSFORM_RESULT']:
                            processed_results['TEXT_TRANSFORM_RESULT'][t] = []
                        processed_results['TEXT_TRANSFORM_RESULT'][t].append("{}\t{}\n".format('ID', 'Text'))
                        for text in r['analysis']['transformation']:
                            processed_results['TEXT_TRANSFORM_RESULT'][t].append("{}\t{}\n".format(text['class_id'], text['transformed_text']))
                    else:
                        print(f"Unsupported result message type: {r['message_type']}")


    filename = f'stream.output.{datetime.now().strftime("%Y-%m-%d_%H%M%s")}.txt'
    if path:
        filename = f'{path}/{filename}'

    with open(filename, 'w+') as f:
        for t in errors:
            f.write(f'{t} ERRORS(s)\n')
            f.writelines(errors[t])
            f.write('\n\n')
        for category in processed_results:
            for t in processed_results[category]:
                f.write(t + '\n')
                f.writelines(processed_results[category][t])
            f.write('\n\n')
        # for r in responses:
        #     resp_as_json = r.get_response_as_json()
        #     f.write(json.dumps(resp_as_json, indent=2))
    # responses = []
    return filename


if __name__ == '__main__':
    sys.exit(main())
