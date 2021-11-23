import os, json
import configparser
import olivepy.messaging.olive_pb2 as olivepb
from olivepy.utils.pem import Pem


DUMMY_SECTION="dummy_section"

def open_config(filename):

    if not os.path.exists(filename):
        raise Exception("Failed to parse config file '{}', because the file does not exist!".format(filename))

    # Hack to add a dummy section to the config file so we don't have to have section headings
    with open(filename) as f:
        file_content = '[' + DUMMY_SECTION + ']\n' + f.read()

    config = configparser.RawConfigParser()
    config.read_string(file_content)

    return config

def parse_file_options(config):

    # Read from the config as a standard properties file (no section headers)
    return dict(config.items(DUMMY_SECTION))


def parse_json_options(option_str):
    """
    Parse options from a json string.  Intended to be used for workflow options that may be grouped by one or more
    tasks. Options can be passed in a couple of different structures. In the more complicated case they can be a list
    of dictionaries, that specify the task/job name these options are used for, for example: '[{"task":"SAD", "options":{"filter_length":99, "interpolate":1.0}}]'
    They can also be passed in a simple dictionary, like:  '{"filter_length":99, "interpolate":1.0, "name":"midge"}'.
    In the former example, options are only passed to the job/task specified.  In the latter case, these options are
    passed to all tasks.  In both cases, OLIVE will only pass options to a task if the task supports that option name

    :param option_str: the options to parse

    :return a list of OptionValue objects created from the JSON option input
    """

    # Examples of inputs to handle:
    # [{"task":"SAD", "options":{"filter_length":99, "interpolate":1.0}}]'
    # '{"filter_length":99, "interpolate":1.0, "name":"midge"}'
    # '[{"task":"LID", "options": {"filter_length":99, "interpolate":11.0, "test_name":"midge"}}]'
    # '[{"job":"SAD LID Job", "task":"LID", "options": {"filter_length":99, "interpolate":11.0, "test_name":"midge"}}]'

    # Parse options
    json_opts = json.loads(option_str)
    out_opts = []

    # Options can be a list of task specific options
    # currently we don't support task specific options so just create one dictionary of name/value options
    if isinstance(json_opts, list):
        for item in json_opts:
            in_opts = item['options']
            for opt in in_opts:
                # print("\t{} = {}, value type: {}".format(opt, in_opts[opt], type(in_opts[opt])))
                opt_msg = olivepb.OptionValue()
                opt_msg.name = opt
                opt_msg.value = str(in_opts[opt])
                # optionally check if this option is restricted to a job/task:
                if 'task' in item:
                    opt_msg.task_filter_name = item['task']
                if 'job' in item:
                    opt_msg.job_filter_name = item['job']
                out_opts.append(opt_msg)
    else:
        # or options that are applied to each task, which is just a simple dictionary
        # like: {"filter_length":99, "interpolate":1.0}
        # OLIVE wil internally ignore these options if the keyname does not match one of the option name
        # a plugin supports for the requested trait (i.e. plugin.get_region_scoring_opts()
        for opt in json_opts:
            opt_msg = olivepb.OptionValue()
            opt_msg.name = opt
            opt_msg.value = str(json_opts[opt])
            out_opts.append(opt_msg)
            # print("\t{} = {}, value type: {}".format(opt, json_opts[opt], type(json_opts[opt])))

    print("Final json options: {}".format(out_opts))
    return out_opts


def parse_json_options_as_dict(option_str):
    """
    Parse options from a json string.  Intended to be used for workflow options that may be grouped by one or more
    tasks. Options can be passed in a couple of different structures. In the more complicated case they can be a list
    of dictionaries, that specify the task/job name these options are used for, for example: '[{"task":"SAD", "options":{"filter_length":99, "interpolate":1.0}}]'
    They can also be passed in a simple dictionary, like:  '{"filter_length":99, "interpolate":1.0, "name":"midge"}'.
    In the former example, options are only passed to the job/task specified.  In the latter case, these options are
    passed to all tasks.  In both cases, OLIVE will only pass options to a task if the task supports that option name

    :param option_str: the options to parse

    :return: a dictionary of options name/value pairs
    """

    # Parse options
    json_opts = json.loads(option_str)
    out_opts = dict()

    # Options can be a list of task specific options
    # currently we don't support task specific options so just create one dictionary of name/value options
    if isinstance(json_opts, list):
        for item in json_opts:
            in_opts = item['options']
            print("Found {} options for task: {}".format(len(in_opts), item['task']))
            out_opts.update(in_opts)
            for opt in in_opts:
                print("\t{} = {}, value type: {}".format(opt, in_opts[opt], type(in_opts[opt])))
    else:
        # or options that are applied to each task, which is just a simple dictionary
        # like: {"filter_length":99, "interpolate":1.0}
        # OLIVE wil internally ignore these options if the keyname does not match one of the option name
        # a plugin supports for the requested trait (i.e. plugin.get_region_scoring_opts()
        out_opts = json_opts
        for opt in json_opts:
            print("\t{} = {}, value type: {}".format(opt, json_opts[opt], type(json_opts[opt])))

    print("Final json options: {}".format(out_opts))
    return  out_opts


def parse_pem_file(data_lines):
    """
    Parse a PEM file, grouping the results by audio file and channel.

    :param data_lines: the data line to parse

    :return:  a dictionary of audio files to score and the channel region:
    # {'filename': {channel: {class_id : [(start_region, end_region, class_id)]} } }
    """
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
                    regions[audio_id][ch] = {}

                class_id = rec.label
                if class_id not in regions[audio_id][ch]:
                    regions[audio_id][ch][class_id] = []

                regions[audio_id][ch][class_id].append((rec.start_t, rec.end_t))

    return regions