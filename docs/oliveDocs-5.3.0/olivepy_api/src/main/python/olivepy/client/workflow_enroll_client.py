
import argparse, os, json, sys

import olivepy.api.olive_async_client as oc
import olivepy.api.workflow as ow
from olivepy.utils.pem import Pem
import olivepy.client.client_common as client_com
from olivepy.messaging.msgutil import OliveInputDataType

# TODO  in OLIVE 5.2 support un-enrollment

def main():
    """

    :return:
    """
    parser = argparse.ArgumentParser(prog='olivepyworkflowenroll', description="Perform OLIVE enrollment using a Workflow "
                                                                         "Definition file")
    
    # parser.add_argument('-C', '--client-id', action='store', default='olivepy_',
    #                     help='Experimental: the client_id to use')

    # Required positional option
    parser.add_argument('workflow', action='store',
                        help='The workflow definition to use.')

    parser.add_argument('--print_jobs', action='store_true',
                        help='Print the supported workflow enrollment jobs.')

    parser.add_argument('--job', action='store',
                        help='Enroll/Unenroll an Class ID for a job(s) in the specified workflow. If not specified enroll or unenroll for ALL enrollment/unenrollment jobst')

    parser.add_argument('--enroll', action='store',
                        help='Enroll using this (class) name.  Should be used with the job argument to specify a target job to enroll with (if there are more than one enrollment jobs) ')

    parser.add_argument('--unenroll', action='store',
                        help='Enroll using this (class) name.  Should be used with the job argument to specify a job to unenroll (if there are more than one unenrollment jobs)')

    parser.add_argument('-i', '--input', action='store',
                        help='The data input to enroll.  Either a pathname to an audio/image/video file or a string for text input')
    parser.add_argument('--input_list', action='store',
                        help='A list of files to enroll. One file per line plus the class id to enroll.')

    parser.add_argument('--path', action='store_true',
                        help='Send the path of the audio instead of a buffer.  '
                             'Server and client must share a filesystem to use this option')

    # Connection arguments
    parser.add_argument('-s', '--server', action='store', default='localhost',
                        help='The machine the server is running on. Defaults to %(default)s.')
    parser.add_argument('-P', '--port', type=int, action='store', default=5588,
                        help='The port to use.')
    parser.add_argument('-t', '--timeout', type=int, action='store', default=10,
                        help='The timeout (in seconds) to wait for a response from the server ')


    # not supporting batch enrollments:
    # parser.add_argument('--audio_list', action='store',
    #                     help='A list of audio files to analyze. One file per line')
    
    args_bad = False
    args = parser.parse_args()
    
    if args.workflow is None :
        print('No workflow definition is specified.')
        args_bad = True

    require_data = True
    if args.unenroll or args.print_jobs:
        require_data = False

    expected_data_type = OliveInputDataType.BINARY_DATA_TYPE
    data_input, transfer_mode, send_pathname = client_com.extract_input_data(args, expected_data_type=expected_data_type, fail_if_no_data=require_data, has_class_ids=True)

    # if args.enroll:
    #     # there must be only one input
    #     if len(data_input) > 1:
    #         args_bad = True
    #         print("The enroll and audio_list argument are mutually exclusive. Pick one and run again")
    #     else:
    #         data_input = [(data_input[0], args.enroll)]

    print("enrolling {} files".format(len(data_input)))
    # if len(data_input) > 1 and not audio:
    #     args_bad = True
    #     print("Non-audio files can not be enrolled from an input list")

    # support other data types....
    # audios = []
    using_pem = False


# TODO GET CLASS IDS FROM ENROLLMENT FILE

    enroll = False
    unenroll = False
    if args.enroll:
        action_str = "Enrollment"
        enroll = True
        if args.unenroll:
            print("Enrollment and un-enrollment are mutually exclusive.  Pick one and run again")
            args_bad = True
    elif args.unenroll:
        action_str = "Unenrollment"
        unenroll = True
    elif len(data_input) > 1:
        enroll = True
    elif not args.print_jobs:
        args_bad = True
        print("Must use one of the options: --enroll, --unenroll, or --print_jobs ")
    action_str = ""

    # support enrollments from a file (list and/or PEM format)?
    # if not (audio or image or video):
    #     # no input provided, make sure this is a status request and not an analysis task
    #     if (enroll):
    #         args_bad = True
    #         print('The command requires data (audio, image, or video) input.')

    if args.job:
        jobs = []
        jobs.extend(str.split(args.job, ','))

    if args_bad:
        print('Run the command with --help or -h to see all the command line options.')
        quit(1)

    # Create the connection to the OLIVE server
    client = oc.AsyncOliveClient("olivepy_workflow", args.server, args.port, args.timeout)
    client.connect()
    try:
        # right now, we only support analysis, so that is what we do...

        # first, create the workflow definition from the workflow file:
        owd = ow.OliveWorkflowDefinition(args.workflow)

        # Submit that workflow definition to the client for actualization (instantiation):
        workflow = owd.create_workflow(client)

        if args.print_jobs:
            #  Print available jobs:
            print("Enrollment jobs '{}'".format(workflow.get_enrollment_job_names()))
            print("Un-Enrollment jobs '{}'".format(workflow.get_unenrollment_job_names()))
            # for enroll_job_name in workflow.get_enrollment_job_names():
                # print("Enrollment job '{}' has Tasks: {}".format(enroll_job_name, workflow.get_enrollment_tasks(enroll_job_name)))
            # for unenroll_job_name in workflow.get_unenrollment_job_names():
                # print("Unenrollment job '{}' has Tasks: {}".format(unenroll_job_name, workflow.get_unenrollment_tasks(unenroll_job_name)))

        if not args.job:
            if enroll:
                print("Enrolling for all jobs: {}".format(workflow.get_enrollment_job_names()))
            if unenroll:
                print("Unenrolling for all job: {}".format(workflow.get_unenrollment_job_names()))
            jobs = []

        if len(data_input) > 0:

            enroll_jobs = workflow.get_enrollment_job_names()
            if enroll_jobs is None:
                print("ERROR: This workflow has no jobs that support enrollment")
                quit(1)

            for t in jobs:
                if t not in enroll_jobs:
                    print(
                        "Error: Job '{}'  can not be enrolled via this workflow.  Only jobs(s) '{}' support enrollment.".format(
                            t, enroll_jobs))
                    quit(1)

            enroll_buffers = {}
            for classid in data_input.keys():
                for input_msg in data_input[classid]:
                    if classid not in enroll_buffers:
                        enroll_buffers[classid] = []
                    # buffers.append(workflow.package_workflow_input(input, expected_data_type))
                    enroll_buffers[classid].append(
                        workflow.package_workflow_input(input_msg, expected_data_type))

                # if audio:
                #     # NOT SUPPORTING PEM
                #
                #     # if using_pem:
                #     #     for filename, channel_dict in list(data_input.items()):
                #     #         for channel, regions in list(channel_dict.items()):
                #     #             try:
                #     #                 if channel is None:
                #     #                     ch_label = 0
                #     #                 else:
                #     #                     ch_label = int(channel)
                #     #
                #     #                 buffers.append(workflow.package_audio(filename, mode=audio_mode,  label=os.path.basename(filename),
                #     #                                                       annotations=regions, selected_channel=ch_label))
                #     #
                #     #             except Exception as e:
                #     #                 print("Failed to parse regions from (PEM) input file: {}".format(e))
                #     #                 quit(1)
                # elif text:
                #     print("Text enrollment not supported")
                # elif video:
                #     print("clg adding video file: {}".format(filename))
                #     enroll_buffers[classid].append(
                #         workflow.package_binary(filename, mode=audio_mode, label=os.path.basename(filename)))
                # elif image:
                #     enroll_buffers[classid].append(
                #         workflow.package_image(filename, mode=audio_mode, label=os.path.basename(filename)))

            print("Workflow {} results:".format(action_str.lower()))
            for classid in enroll_buffers.keys():
                buffers = enroll_buffers[classid]
                print("enrolling {} files for class: {}".format(len(buffers), classid))
                response = workflow.enroll(buffers, classid, jobs)
                print("{}".format(response.to_json(indent=1)))
        elif unenroll:
            # TODO use options
            unenroll_jobs = workflow.get_unenrollment_job_names()
            if unenroll_jobs is None:
                print("ERROR: This workflow has no job that support unenrollment")
                quit(1)

            for t in jobs:
                if t not in unenroll_jobs:
                    print(
                        "Error: Job '{}' can not be un-enrolled via this workflow.  Only job(s) '{}' support "
                        "un-enrollment.".format(t, unenroll_jobs))
                    quit(1)

            response = workflow.unenroll(args.unenroll, jobs)
            print("Workflow {} results:".format(action_str.lower()))
            print("{}".format(response.to_json(indent=1)))

    except Exception as e:
        print("Workflow failed with error: {}".format(e))
    finally:
        client.disconnect()


def parse_pem_file(data_lines):
    '''
    Parse a PEM file, grouping the results by audio file and channel
    :param data_lines:
    :return:  a dictionary of audio files to score and the channel region: {'filename': {channel: [(start_region, end_region)]} }
    '''
    #  We process by file and channel - the class/label is ignored
    regions = {}

    input_pem = Pem()
    input_pem.add_records_from_data_lines(data_lines)

    for id in input_pem.get_ids():
        audio_id = os.path.expandvars(id)
        # Create a dictionary of the regions specified for the the current file
        regions[audio_id] = {}
        for rec in input_pem.get_records(id):
            # channel could be a list...
            channels = []
            if type(rec.channel) is str:
                # convert to a list
                channels = map(int, str.split(rec.channel, ','))

            elif type(rec.channel) is int:
                channels.append(rec.channel)
            else:
                print("Unsupported channel value: {}".format(rec.channel))

            for ch in channels:
                if ch not in regions[audio_id]:
                    regions[audio_id][ch] = []

                regions[audio_id][ch].append((rec.start_t, rec.end_t))

    return regions

if __name__ == '__main__':
    sys.exit(main())
