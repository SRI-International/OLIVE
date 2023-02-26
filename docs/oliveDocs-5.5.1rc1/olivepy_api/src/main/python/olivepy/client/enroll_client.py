

import argparse
import logging
import os
import olivepy.client.client_common as client_com
from olivepy.messaging.msgutil import OliveInputDataType
from olivepy.messaging.msgutil import AudioTransferType, InputTransferType
from olivepy.messaging.msgutil import  BOUNDING_BOX_SCORER

import olivepy.api.oliveclient

# Todo:
#   * Should we accept both enroll and unenroll in the same command line.


# This is the main function of the script.  It does two things:
#   1. Gather up the command line arguments.
#   2. Connect to the server, do the enrollment, and disconnect from the server.

if __name__ == '__main__':
    
    parser = argparse.ArgumentParser(prog='olivepyenroll')
    
    parser.add_argument('-C', '--client-id', action='store', default='oliveenroll',
                        help='Experimental: the client_id to use')

    parser.add_argument('-D', '--debug', action='store_true',
                        help='The domain to use')

    parser.add_argument('-p', '--plugin', action='store',
                        help='The plugin to use.')
    parser.add_argument('-d', '--domain', action='store',
                        help='The domain to use')

    parser.add_argument('-e', '--enroll', action='store',
                        help='Enroll with this name.')
    parser.add_argument('-u', '--unenroll', action='store',
                        help='Uneroll with this name.')

    parser.add_argument('-s', '--server', action='store', default='localhost',
                        help='The machine the server is running on. Defaults to %(default)s.')
    parser.add_argument('-P', '--port', type=int, action='store', default=5588,
                        help='The port to use.')    

    parser.add_argument('-t', '--timeout', type=int, action='store', default=10,
                        help='The timeout to use')

    # parser.add_argument('-a', '--audio', action='store',
    #                     help='The audio file to enroll. Required for --enroll command.')
    parser.add_argument('-i', '--input', action='store',
                        help='The data input to analyze.  Either a pathname to an audio/image/video file or a string for text input.  For text input, also specify the --text flag')
    parser.add_argument('--input_list', action='store',
                        help='A list of files to analyze. One file per line.')
    parser.add_argument('--path', action='store_true',
                        help='Send the path of the audio instead of a buffer.  '
                             'Server and client must share a filesystem to use this option')
    
    args_bad = False
    args = parser.parse_args()
    
    # Get into debug mode as soon as possible.
    if args.debug:
        logging.basicConfig(level=logging.DEBUG, force=True)
    
    if args.plugin is None or args.domain is None:
        print('No plugin or domain is specified and one of each is required.')
        args_bad = True
        
    if args.enroll is None and args.unenroll is None:
        print('No command has been given.  Either enroll or unenroll must be given or nothing will be done.')
        args_bad = True

    require_data = False
    if args.enroll is not None:
        require_data = True
        
    if args.unenroll is not None and args.input is not None:
        print('Warning: The unenroll command does not use a input file, so that argument will be ignored.')

    if args.path:
        # validate paths in our list?
        # print('Sending audio as a path name')
        audio_mode = AudioTransferType.AUDIO_PATH
        print("send as path")
    else:
        # print('Sending audio as a serialized buffer')
        audio_mode = AudioTransferType.AUDIO_SERIALIZED

    if args.client_id is None or args.server is None or args.port is None or args.timeout is None:
        print('Internal error: a required variable is not set.')
        quit(2)

    client = olivepy.api.oliveclient.OliveClient(args.client_id, args.server, args.port, args.timeout)
    client.connect()

    plug, domain = client.request_plugins(args.plugin, args.domain)
    expected_data_type = OliveInputDataType.AUDIO_DATA_TYPE
    # send as audio data, unless plugin is a bounding box scorer then send as binary media
    for trait in plug.trait:
        if BOUNDING_BOX_SCORER  == trait.type:
            expected_data_type = OliveInputDataType.BINARY_DATA_TYPE
            break

    # if args.text:
    #     # special case of handling text data
    #     expected_data_type = OliveInputDataType.TEXT_DATA_TYPE
    # elif args.box:
    #     expected_data_type = OliveInputDataType.BINARY_DATA_TYPE
    # else:
    #     expected_data_type = OliveInputDataType.AUDIO_DATA_TYPE

    data_input, transfer_mode, send_pathname = client_com.extract_input_data(args, expected_data_type=expected_data_type, fail_if_no_data=require_data, has_class_ids=True)

    if args_bad:
        print('Run the command with --help or -h to see all the command line options.')
        quit(1)



    try:
        if args.enroll is not None:
            for class_id in data_input.keys():
                for input in data_input[class_id]:
                    success = client.enroll(args.plugin, args.domain, class_id, None, input)
                    #fixme: this output should be better
                    if success:
                        print('Successfully enrolled {} in {} {}'.format(class_id, args.plugin, args.domain))
                    else:
                        print('Failed to enroll {} in {} {}'.format(class_id, args.plugin, args.domain))

        if args.unenroll is not None:
            success = client.unenroll(args.plugin, args.domain, args.unenroll)
            if success:
                print('Successfully unenrolled {} from {} {}'.format(args.unenroll, args.plugin, args.domain))
            else:
                print('Failed to unenroll {} from {} {}'.format(args.unenroll, args.plugin, args.domain))
    except Exception as e:
        print("Enrollment failed with error: {}".format(e))
    finally:
        client.disconnect()