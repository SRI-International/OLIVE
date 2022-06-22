

import argparse
import os, time
import logging

import olivepy.api.oliveclient
import olivepy.utils.utils as utils
import olivepy.client.client_common as client_com
from olivepy.messaging.msgutil import OliveInputDataType
from olivepy.messaging.msgutil import AudioTransferType, InputTransferType


from olivepy.api.olive_async_client import AsyncOliveClient
#from olive.plugin.audio import Audio

# Todo:
#   * Should we accept multiple commands (ie. global and regions?).


def heartbeat_notification(heatbeat):
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
        print("Server version: " + str(stats.server_version))

# This is the main function of the script.  It does two things:
#   1. Gather up the command line arguments.
#   2. Connect to the server, do the analysis, and disconnect from the server.

if __name__ == '__main__':
    
    parser = argparse.ArgumentParser(prog='olivepyanalyze')
    
    parser.add_argument('-C', '--client-id', action='store', default='olivepy_analyze',
                        help='Experimental: the client_id to use')

    parser.add_argument('-p', '--plugin', action='store',
                        help='The plugin to use.')
    parser.add_argument('-d', '--domain', action='store',
                        help='The domain to use')

    parser.add_argument('-G', '--guess', action='store_true',
                        help='Experimental: guess the type of analysis to use based on the plugin/domain.')

    # ANALYSIS OPTIONS
    parser.add_argument('-e', '--enhance', action='store_true',
                        help='Enhance the audio of a wave file, which must be passed in with the --wav option.')
    parser.add_argument('-f', '--frame', action='store_true',
                        help='Do frame based analysis of a wave file, which must be passed in with the --wav option.')
    # Because global is a keyword, we need to access this with getattr(args, 'global')
    parser.add_argument('-g', '--global', action='store_true',
                        help='Do global analysis of a wave file, which must be passed in with the --wav option.')
    parser.add_argument('-r', '--region', action='store_true',
                        help='Do region based analysis of a wave file, which must be passed in with the --wav option.')
    parser.add_argument('-b', '--box', action='store_true',
                        help='Do bounding box based analysis of an input file, which must be passed in with the --wav option.')


    parser.add_argument('-P', '--port', type=int, action='store', default=5588,
                        help='The port to use.') 
    parser.add_argument('-s', '--server', action='store', default='localhost',
                        help='The machine the server is running on. Defaults to %(default)s.')
    parser.add_argument('-t', '--timeout', type=int, action='store', default=10,
                        help='The timeout to use')  

    parser.add_argument('-i', '--input', action='store',
                        help='The data input to analyze.  Either a pathname to an audio/image/video file or a string for text input.  For text input, also specify the --text flag')
    parser.add_argument('--input_list', action='store',
                        help='A list of files to analyze. One file per line.')
    parser.add_argument('--text', action='store_true',
                        help='Indicates that input (or input list) is a literal text string to send in the analysis request.')

    # parser.add_argument('-a', '--audio', action='store',
    #                     help='The audio file to analyze.')
    # parser.add_argument('--audio_list', action='store',
    #                     help='A file containing a list of audio files to analyze. One file per line')

    parser.add_argument('--options', action='store',
                        help='Optional file containing plugin properties ans name/value pairs.')
    parser.add_argument('--class_ids', action='store',
                        help='Optional file containing plugin properties ans name/value pairs.')
    parser.add_argument('--debug', action='store_true',
                        help='Debug mode ')
    parser.add_argument('--path', action='store_true',
                        help='Send the path of the audio instead of a buffer.  '
                             'Server and client must share a filesystem to use this option')

    # this arguments do not require a plugin/domain and audio input
    parser.add_argument('--print', action='store_true',
                        help='Print all available plugins and domains')

    args_bad = False
    args = parser.parse_args()

    # Simple logging config
    if args.debug:
        log_level = logging.DEBUG
    else:
        log_level = logging.INFO
        # log_level = logging.WARN
    logging.basicConfig(level=log_level)

    if args.plugin is None or args.domain is None:
        print('No plugin or domain is specified and one of each is required.')
        args_bad = True

    if not args.enhance and not args.frame and not getattr(args, 'global') and not args.region  and not args.box:
        print('No command has been given.  One of enhance, frame, global, region, or box must be given or nothing will be done.')
        args_bad = True

    # we could look this up based on the plugin type, but that makes setup more complicated, so pushing to the client,
    # who should easily know if they are dealing with a file or text input to translate
    if args.text:
        # special case of handling text data
        expected_data_type = OliveInputDataType.TEXT_DATA_TYPE
    elif args.box:
        expected_data_type = OliveInputDataType.BINARY_DATA_TYPE
    else:
        expected_data_type = OliveInputDataType.AUDIO_DATA_TYPE

    #TODO IF PLUGIN IS A BOUND BOX THEN USE BINARY INPUT?
    data_input, transfer_mode, send_pathname = client_com.extract_input_data(args, expected_data_type=expected_data_type, fail_if_no_data=True)

    # audios = []

    # if args.audio_list:
    #     try:
    #         audios = []
    #         audios.extend([line.strip() for line in open(args.audio_list).readlines()])
    #
    #         if len(audios) < 1:
    #             print("Input file '{}' contains no audio files!".format(args.audio_list))
    #             quit(1)
    #
    #         cols = len(audios[0].split())
    #         # parse the file based on the number of columns, where 1 column is the original input format and
    #         # 5 columns is assumed to a PEM formatted file, while two columns is an audio compare format
    #         if cols == 1:
    #             # Normal mode
    #             audios = [os.path.expandvars(a.strip()) for a in audios]
    #         elif cols == 5:
    #             using_pem = True
    #             audios = utils.parse_pem_file(audios)
    #             # should look something like ->  {audio_file:  {channel : [regions] }}, where regions are one or more (start, end) regions
    #         else:
    #             print("Audio input file is not well-formed")
    #             quit(1)
    #
    #     except Exception as e:
    #         print("Failed to parse the input file: {}".format(e))
    #         quit(1)
    #
    # if args.audio is None and not audios:
    #     print('The command requires a wav argument.')
    #     args_bad = True
    # elif args.audio:
    #     tmp_path = os.path.expanduser(args.audio)
    #     if not os.path.exists(tmp_path):
    #         print('Your audio file ({}) is not found.'.format(tmp_path))
    #         args_bad = True
    #     audios.append(tmp_path)


    if args.options:
        #fixme get options from file or as JSON string?

        plugin_config = utils.open_config(os.path.expanduser(args.options))
        plugin_options = utils.parse_file_options(plugin_config)
        # plugin_options = utils.parse_file_options(args.options)
    else:
        plugin_options = None

    class_ids = None
    if args.class_ids:
        with open(args.class_ids) as f:
            class_ids = [line.strip() for line in f.readlines()]


    if args_bad:
        print('Run the command with --help or -h to see all the command line options.')
        quit(1)

    if args.client_id is None or args.server is None or args.port is None or args.timeout is None:
        print('Internal error: a required variable is not set.')
        quit(2)
    
    client = olivepy.api.oliveclient.OliveClient(args.client_id, args.server, args.port, args.timeout)
    # client = AsyncOliveClient(args.client_id, args.server, args.port, args.timeout)
    client.connect()



    file_mode = 'w'
    try:
        for input in data_input:
            try:
                if args.enhance:

                    success, result = client.audio_modification(args.plugin, args.domain, None, data_msg=input)
                    if success:
                        print('The enhancement succeeded. {}'.format(result.message))
                    else:
                        print('The enhancement failed. {}'.format(result.message))

                    # TODO: check that new audio is same size as old audio, not just > 0.
                    # If that is OK.  Is enhanced audio always the same size as original audio?
                    # print(len(result.audio.data))
                    # print(str(type(result.audio.data)))
                    print("Audio sample rate: {}, {}".format(result.audio.rate, result.audio.bit_depth))
                    if len(result.audio.data) > 0:
                        # dir = os.path.dirname(wav_path)
                        # base = os.path.basename(wav_path)
                        out_path = os.path.join('OUTPUT', 'audio')
                        os.makedirs(out_path, exist_ok=True)
                        enhanced_file_name = os.path.join(out_path, input.label)

                        # Output should be PCM-16
                        import soundfile as sf
                        with sf.SoundFile(enhanced_file_name, 'w', samplerate=result.audio.rate, channels=result.audio.channels, subtype='PCM_16') as enhanced_file:
                            import numpy as np
                            enhanced_file.write(np.frombuffer(result.audio.data, dtype=np.int16))
                    else:
                        print('The enhancement did not return any audio, so no file will be written.')

                if args.frame:
                    frames = client.analyze_frames(args.plugin, args.domain, None, data_msg=input, opts=plugin_options, classes=class_ids)

                    if len(frames) == 0:
                        print('No frames were returned.')
                    else:
                        print('Received {} frames scores.'.format(len(frames)))

                    # save output
                    base = os.path.basename(input.label)
                    out_path = os.path.join('OUTPUT')
                    os.makedirs(out_path, exist_ok=True)
                    with open(os.path.join(out_path, base+".scores"), 'w') as f:
                        for frame in frames:
                            f.write(("%.5f" % round(frame, 5)) + "\n")

                if getattr(args, 'global'):
                    results = client.analyze_global(args.plugin, args.domain, None, data_msg=input, opts=plugin_options, classes=class_ids)
                    if len(results) == 0:
                        print('No results were returned.')
                    else:
                        print("Received {} global score results for '{}'".format(len(results), input.label))
                    with open('output.txt', file_mode) as f:
                        for result in results:
                            f.write("{} {} {:.8f}\n".format(input.label, result.class_id, result.score))
                            print("{} = {}".format(result.class_id, result.score))
                            # print('{} = {}'.format(result.class_id, result.score))

                if args.region:
                    regions = client.analyze_regions(args.plugin, args.domain, None, data_msg=input, opts=plugin_options,
                                                     classes=class_ids)
                    if len(regions) == 0:
                        print('No regions were returned.')
                    else:
                        print('Received {} region score results for {}:'.format(len(regions), input.label))
                        for result in regions:
                            print("'{}' from: {:.2f} to {:.2f}".format(result.class_id.capitalize(), result.start_t, result.end_t))


                    with open('output.txt', file_mode) as f:
                        for region in regions:
                            f.write(
                                "{} {:.2f} {:.2f} {} {:.8f}\n".format(input.label, region.start_t, region.end_t, region.class_id, region.score))

                if args.box:
                    regions = client.analyze_bounding_box(args.plugin, args.domain, None, data_msg=input, opts=plugin_options,
                                                     classes=class_ids)
                    if len(regions) == 0:
                        print('No bounding box regions were returned for ()'.format(input.label))
                    else:
                        print('Received {} bounding box score results for {}:'.format(len(regions), input.label))

                    with open('output.txt', file_mode) as f:
                        for region in regions:
                            if region:
                                if region.HasField('time_region'):
                                    # start/end region
                                    bbox = region.bbox
                                    tregion = region.time_region

                                    f.write(
                                        "{} {} {:.2f} ({}, {}, {}, {}) ({:.2f}, {:.2f})\n"
                                            .format(input.label, region.class_id, region.score, bbox.x1,  bbox.y1, bbox.x2,
                                                    bbox.y2, tregion.start_t, tregion.end_t))

                                else:
                                    # no time regions
                                    bbox = region.bbox
                                    f.write(
                                        "{} {} {} ({}, {}, {}, {})\n".format(input.label, region.class_id, region.score,
                                                                             bbox.x1, bbox.y1, bbox.x2,
                                                                             bbox.y2))


                            ####
                            # f.write(
                            #     "{} {:.2f} {:.2f} {} {:.8f}\n".format(input.label, region.start_t, region.end_t, region.class_id, region.score))
            except Exception as e:
                print("Analysis failed with error: {}".format(e))

            # now switch to appending data
            file_mode = 'a'
    finally:
        client.disconnect()

    print('Exiting...')

