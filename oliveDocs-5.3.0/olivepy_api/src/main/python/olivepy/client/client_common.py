
import argparse, os, json, sys
import logging
from olivepy.utils import utils
from olivepy.messaging.msgutil import AudioTransferType, InputTransferType, OliveInputDataType
import olivepy.messaging.msgutil as msgutil
from olivepy.messaging.olive_pb2 import (Audio, BinaryMedia, Text)



def extract_input_data(args, expected_data_type=OliveInputDataType.AUDIO_DATA_TYPE, fail_if_no_data=True, has_class_ids=False):
    """
    Helper util to use the standard CLI arguments to package input (audio, video, image, text) for processing by
    the OLIVE server

    :param args:
    :param fail_if_no_data:
    :param has_class_ids:
    :return:
    """

    send_pathname = True if args.path else False
    send_serialized = not send_pathname

    data_input = []
    if send_pathname:
        # fixme more generic for image and video?
        transfer_mode = InputTransferType.PATH
    else:
        transfer_mode = InputTransferType.SERIALIZED

    audio = text = image = video = False

    if args.input_list:
        # parse input list to make sure we actually have one or more files...
        data_input = parse_data_list2(args.input_list, transfer_mode, expected_data_type, has_class_ids)
        # data_intpu --> [{filename: DATA_MSG, {channel: [start, end, class]}] , filename: Audio,  {-1: (None, None, None)}
        if len(data_input) == 0:
            args_bad = True
            print("Data input list '{}' contains no valid files".format(args.list))

    elif args.input:
        # Do we want to support sending a path that is not local?  In case they want to specify a path that is a
        # available for the server

        if has_class_ids:
            # data_input.append( (convert_filename_to_data(args.input, transfer_mode, expected_data_type), args.enroll))
            data_input = {args.enroll: [convert_filename_to_data(args.input, transfer_mode, expected_data_type)]}
        else:
            data_input.append( convert_filename_to_data(args.input, transfer_mode, expected_data_type) )


    # do some basic validation
    # If mo data input supplied, make sure that is okay with the other options provided
    if not data_input and fail_if_no_data:
        print('The command requires data input(s).')
        exit(1)

    return data_input, transfer_mode, send_pathname

def convert_filename_to_data(input_in, transfer_mode, expected_data_type=OliveInputDataType.AUDIO_DATA_TYPE, check_path=True, selected_channel=None, annotations=None,):

    if expected_data_type != OliveInputDataType.TEXT_DATA_TYPE:
        if check_path and not os.path.exists(os.path.expanduser(input_in)):
            print('Your input file ({}) was not found.'.format(input_in))
            exit(1)

    if expected_data_type == OliveInputDataType.AUDIO_DATA_TYPE:
        audio = Audio()
        filename = os.path.expanduser(input_in)
        msgutil.package_audio(audio, filename, mode=transfer_mode, label=os.path.basename(filename), selected_channel=selected_channel, annotations=annotations)
        return audio
    elif expected_data_type == OliveInputDataType.TEXT_DATA_TYPE:
        text_msg = Text()
        text_msg.text.append(input_in)
        # label for text input?
        return text_msg
    else:
        # default type, new as of OLIVE 5.3
        media_msg = BinaryMedia()
        filename = os.path.expanduser(input_in)
        msgutil.package_binary_media(media_msg, filename, mode=transfer_mode, label=os.path.basename(filename), selected_channel=selected_channel, annotations=annotations)
        return  media_msg


def extract_input_data_type(args, fail_if_no_data=True, has_class_ids=False):

    send_pathname = True if args.path else False
    send_serialized = not send_pathname
    data_input = []
    if send_pathname:
        # fixme more generic for image and video?
        audio_mode = AudioTransferType.AUDIO_PATH
    else:
        audio_mode = AudioTransferType.AUDIO_SERIALIZED

    audio = text = image = video = False

    if args.list:
        # parse input list to make sure we actually have one or more files...
        data_input = parse_data_list(args.list, has_class_ids)
        if len(data_input) == 0:
            args_bad = True
            print("Data input list '{}' contains no valid files".format(args.list))

    if args.audio:
        if not data_input:
            # do we want to support sending a path that is not local?
            if not os.path.exists(os.path.expanduser(args.audio)):
                print('Your audio input file ({}) was not found.'.format(args.audio))
                exit(1)
            data_input = [os.path.expanduser(args.audio)]
        audio = True
    if args.text:
        if not data_input:
            data_input.append(args.text)
            # print("sending text input {}".format(text_input))
        text = True
    if args.video:
        if not data_input:
            # do we want to support sending a path that is not local?
            if not os.path.exists(os.path.expanduser(args.video)):
                print('Your video input file ({}) was not found.'.format(args.video))
                exit(1)
            data_input.append(args.video)
        # print("Using video file: {}".format(video_input))
        video = True
    if args.image:
        if not data_input:
            # do we want to support sending a path that is not local?
            if not os.path.exists(os.path.expanduser(args.image)):
                print('Your impage input file ({}) was not found.'.format(args.image))
                exit(1)
            data_input.append(args.image)
        # print("Using image file: {}".format(image_input))
        image = True

    # do some basic validation
    # If mo data input supplied, make sure that is okay with the other options provided
    if not (audio or text or image or video) and fail_if_no_data:
        print('The command requires data (audio, text, image, or video) input.')
        exit(1)
    if audio and (text or image or video):
        print('Arguments audio, text, image, and video are mutually exclusive.  Pick one and run again')
        exit(1)
    if text and (audio or image or video):
        print('Arguments audio, text, image, and video are mutually exclusive.  Pick one and run again')
        exit(1)
    if image and (audio or text or video):
        print('Arguments audio, text, image, and video are mutually exclusive.  Pick one and run again')
        exit(1)
    if video and (audio or text or image):
        print('Arguments audio, text, image, and video are mutually exclusive.  Pick one and run again')
        exit(1)

    #     # no input provided, make sure this is a status request and not an analysis task
    #     if not (args.tasks or args.classids or args.print_actualized or args.print_workflow):
    #         args_bad = True
    #

    # todo validate using multiple data flags

    # I don't know if we need to print this...
    # if audio or video:
    #     if send_pathname:
    #         print('Sending files as a path name')
    #     else:
    #         print('Sending files as a serialized buffer')

    return data_input, audio_mode, send_pathname, audio, text, image, video


def parse_data_list(list_filename, has_class_ids=False):
    try:
        files = []
        files.extend([line.strip() for line in open(list_filename).readlines()])

        if len(files) < 1:
            print("Input file '{}' contains no data files!".format(list_filename))
            quit(1)

        cols = len(files[0].split())
        # parse the file based on the number of columns, where 1 column is the original input format and
        # 5 columns is assumed to a PEM formatted file, while two columns is an audio compare format
        if cols == 1:
            # Normal mode
            if has_class_ids:
                print("Input file is not well-formed.  Must include class ID(s)")
                quit(1)
            files = [os.path.expandvars(a.strip()) for a in files]
        elif cols == 2:
            if not has_class_ids:
                print("Input file is not well-formed.  File can not include class IDs")
                quit(1)
            files = [(a.rstrip().split()) for a in files]
            # Convert to a dict, indexed by class id
            rtn = dict()
            for filename, class_id in files:
                if class_id not in rtn:
                    rtn[class_id] = []
                rtn[class_id].append(filename)

            return rtn


        elif cols == 5:
            using_pem = True
            files = utils.parse_pem_file(files)
            # should look something like ->  {audio_file:  {channel : [(regions, class_id)] }}, where each region is (start, end)

        else:
            print("Data input file is not well-formed")
            quit(1)


    except Exception as e:
        print("Failed to parse the input file: {}".format(e))
        quit(1)

    return files


def parse_data_list2(list_filename, transfer_mode, expected_data_type=OliveInputDataType.AUDIO_DATA_TYPE, has_class_ids=False):
    try:
        files = []
        files.extend([line.strip() for line in open(list_filename).readlines()])

        if len(files) < 1:
            print("Input file '{}' contains no data files!".format(list_filename))
            quit(1)

        cols = len(files[0].split())
        # parse the file based on the number of columns, where 1 column is the original input format and
        # 5 columns is assumed to a PEM formatted file, while two columns is an audio compare format
        if cols == 1:
            # Normal mode
            if has_class_ids:
                print("Input file is not well-formed.  Must include class ID(s)")
                quit(1)
            # files = [os.path.expandvars(a.strip()) for a in files]
            return [convert_filename_to_data(os.path.expandvars(a.strip()), transfer_mode, expected_data_type) for a in files]

        elif cols == 2:
            if not has_class_ids:
                print("Input file is not well-formed.  File can not include class IDs")
                quit(1)
            files = [(a.rstrip().split()) for a in files]
            # Convert to a dict, indexed by class id
            rtn = dict()
            for filename, class_id in files:
                if class_id not in rtn:
                    rtn[class_id] = []
                rtn[class_id].append(convert_filename_to_data(os.path.expandvars(filename), transfer_mode, expected_data_type))


            #fixme not sure about this yet....
            return rtn

        elif cols == 5:
            pem_data = utils.parse_pem_file(files)
            if has_class_ids:
                msgs = dict()
            else:
                msgs = []
            for filename in  pem_data:
                for channel in pem_data[filename]:
                    # regions = []
                    if channel == 0:
                        selected_channel = None
                    else:
                        selected_channel = channel

                    for class_id in pem_data[filename][channel]:
                        regions = pem_data[filename][channel][class_id]
                        # start_t = pem_data[filename][channel][class_id][0]
                        # end_t = pem_data[filename][channel][class_id][1]

                        print("CLG using regions for file '{}' : {}".format(filename, regions))

                        if has_class_ids:
                            if class_id not in msgs:
                                msgs[class_id] = []
                            msgs[class_id].append(convert_filename_to_data(filename, transfer_mode, expected_data_type, selected_channel=selected_channel, annotations=regions))

                    # if are not using the class ID then we create a data message for this filename/channel
                    # do we want to do anything with the selected channel?
                    # selected_channel = channel
                    # if channel <= 0:
                    #     # By default merge:
                    #     selected_channel = -1


                        if not has_class_ids:
                            msgs.append(convert_filename_to_data(filename, transfer_mode, expected_data_type, selected_channel=selected_channel, annotations=regions))

                    # todo pass annotations (optionally task annots), selected channel



            return msgs


            # should look something like ->  {audio_file:  {channel : class_id: [(regions)] }}}, where each region is (start, end)

        else:
            print("Data input file is not well-formed")
            quit(1)


    except Exception as e:
        print("Failed to parse the input file: {}".format(e))
        quit(1)

    return files
