import logging
# import numpy as np  Not currently used.  maybe later...
from queue import Queue
import signal
import threading
import zmq
import os
# import time
# import numpy as np

from olivepy.messaging.msgutil import ExceptionFromServer, package_audio, AudioTransferType, InputTransferType, package_binary_media
from olivepy.messaging.msgutil import _wrap_message, _unwrap_reponse
from olivepy.messaging.olive_pb2 import *
import olivepy.messaging.msgutil as msgutil

# Future: add server, port, and request information to this exception?

class OliveClient(object):
    """
    This is a simplified version of network library used to contact the Olive server via python code.  All OLIVE calls
    below are synchronous, and block and until a response is received from the OLIVE server.  These example API calls
    are intended to make working with the OLIVE API clearer since all calls are blocking.  To make asynchronous requests
    to the OLIVE server use olivepy.api.olive_async_client.AsyncOliveClient for your enterprise application.
    """

    # These constants are used by the analyze functions
    # AUDIO_PATH = 1
    # AUDIO_DECODED = 2
    # AUDIO_SERIALIZED = 3

    def __init__(self, client_id, address='localhost', request_port=5588, timeout_second=10):
        """
        :param client_id: The unique name of this client.  Due to a ZMQ bug on some platforms this ID can not end in '1'
        :param address: the address of the olive server, such as localhost
        :param request_port: default olive port is 5588
        :param timeout_second:  time in seconds, to wait for a response from the server
        """

        self.client_id = client_id

        # due to a ZMQ bug the last character of the client ID can not be 1, so remove it
        if client_id[-1] == "1":
            self.client_id = client_id[:-1]
            logging.warning("Last character of the client ID can not be '1', removing to avoid a ZMQ bug")

        self.server_address = address
        self.server_request_port = request_port
        self.server_status_port = request_port+1

        self.timeout_seconds = timeout_second

        self.olive_connected = False
        self.info = self.fullobj = None

        OliveClient.setup_multithreading()

    def connect(self, monitor_server=False):
        """
        Connect this client to the server

        :param monitor_server: if true, start a thread to monitor the server connection (helpful if debugging connection issues)
        """
        # init the request and status socket
        request_addr = "tcp://" + self.server_address + ":" + str(self.server_request_port)
        status_addr = "tcp://" + self.server_address + ":" + str(self.server_status_port)

        context = zmq.Context()
        self.request_socket = context.socket(zmq.DEALER)
        self.status_socket = context.socket(zmq.SUB)

        self.request_socket.connect(request_addr)
        self.status_socket.connect(status_addr)

        # logging.debug("Starting Olive status monitor...")

        # Run this to get status about the server (helpful to confirm the server is connected and up)
        if(monitor_server):
            self.worker = ClientBrokerWorker(self.status_socket,  self.client_id)
            self.worker.start()
        else:
            self.worker = None

        self.olive_connected = True

        logging.debug("Olive client ready")


    def disconnect(self):
        if self.worker is not None:
            self.worker.stopWorker()
        self.request_socket.close()
        self.olive_connected = False


    def is_connected(self):
        return self.olive_connected

    @classmethod
    def setup_multithreading(cls):
        """This function is only needed for multithreaded programs.  For those programs,
           you must call this function from the main thread, so it can properly set up
           your signals so that control-C will work properly to exit your program.
        """
        # https://stackoverflow.com/questions/17174001/stop-pyzmq-receiver-by-keyboardinterrupt
        # https://stackoverflow.com/questions/23206787/check-if-current-thread-is-main-thread-in-python
        if threading.current_thread() is threading.main_thread():
            signal.signal(signal.SIGINT, signal.SIG_DFL)


    def request_plugins(self, plugin=None, domain=None):
        self.info = self.fullobj = None
        request = PluginDirectoryRequest()

        _, env = _wrap_message(self.client_id, request)
        # Now send the message
        logging.debug("Sending a global score request message")
        result = self._sync_request(env)
        if not plugin or not domain:
            return result.plugins

        # Find the matching plugin/domain

        plugin_response = result.plugins
        if plugin:
            # filter results by plugin
            matched = False
            for pd in result.plugins:
                if pd.id == plugin:
                    matched = True
                    if domain:
                        domains = pd.domain
                        filtered_domains = []
                        for dom in domains:
                            if dom.id == domain:
                                return pd, dom
            if not matched:
                print("Requested plugin '{}' not found".format(plugin))

        raise Exception("Plugin '{}', domain: '{}' was not found on the OLIVE server")


    def get_update_status(self, plugin, domain):
        self.info = self.fullobj = None
        request = GetUpdateStatusRequest()
        request.plugin = plugin
        request.domain = domain

        _, env = _wrap_message(self.client_id, request)
        # Now send the message
        logging.debug("Sending a global score request message")
        result = self._sync_request(env)
        return result.update_ready, result

    def load_plugin_domain(self, plugin, domain):
        self.info = self.fullobj = None
        request = LoadPluginDomainRequest()
        request.plugin = plugin
        request.domain = domain

        _, env = _wrap_message(self.client_id, request)
        # Now send the message
        logging.debug("Sending a global score request message")
        result = self._sync_request(env)
        return result.successful

    def unload_plugin_domain(self, plugin, domain):
        self.info = self.fullobj = None
        request = RemovePluginDomainRequest()
        request.plugin = plugin.strip()
        request.domain = domain.strip()

        _, env = _wrap_message(self.client_id, request)
        # Now send the message
        logging.debug("Sending a global score request message")
        result = self._sync_request(env)
        return result.successful

    def update_plugin_domain(self, plugin, domain, metadata):
        self.info = self.fullobj = None
        request = ApplyUpdateRequest()
        request.plugin = plugin
        request.domain = domain
        
        mds = request.params
        for key, item in metadata:
            md = Metadata()
            md.name = key
            if isinstance(item, str):
                md.type = 1
            elif isinstance(item, int):
                md.type = 2
            elif isinstance(item, float):
                md.type = 3
            elif isinstance(item, bool):
                md.type = 4
            elif isinstance(item, list):
                md.type = 5
            else:
                raise Exception('Metadata {} had a {} type that was not str, int, float, bool, or list.'
                                .format(key, str(type(item))))
            md.value = item
            mds.append(md)

        _, env = _wrap_message(self.client_id, request)
        # Now send the message
        logging.debug("Sending a global score request message")
        result = self._sync_request(env)
        return result.successful

    def get_info(self):
        """
        :return: the info data from the last call to the server. Will return None if the last call did not return any info.
        """
        return self.info
    
    def get_fullobj(self):
        """
        This object should be used for debugging only.  Example use::success = client.enroll('sid-embed-v5-py3', 'multilang-v1', 'joshua', 'file') \
           if troubleshooting:
               fullobj = client.get_fullobj()
               print('Whole object returned from server: '+str(fullobj))

        :return: the full object returned from the last call to the server.
        """
        return self.fullobj    

    def get_active(self):
        self.info = self.fullobj = None
        request = GetActiveRequest()

        _, env = _wrap_message(self.client_id, request)
        # Now send the message
        logging.debug("Sending a global score request message")
        result = self._sync_request(env)

        return result.message_id

    def get_status(self):
        self.info = self.fullobj = None
        request = GetStatusRequest()

        _, env = _wrap_message(self.client_id, request)
        # Now send the message
        logging.debug("Sending a global score request message")
        result = self._sync_request(env)
        version = None
        if result.HasField('version'):
            version = result.version
        return (result.num_pending, result.num_busy, result.num_finished, result.version )

    def analyze_frames(self, plugin, domain, filename, data_msg=None,  opts=None, classes=None,  mode=AudioTransferType.AUDIO_PATH):
        """
         Request a analysis of 'filename' returning frame scores.

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param filename: the name of the audio file to score.  if None, then provide (audio) input as a
        :param data_msg: Optionally specify the data input as a fully formed Audio or BinaryMedia message instead of creating from filename
        :param opts: a dictionary of name/value pair options
        :param classes: optionally,  a list of classes classes to be scored

        :return: the analysis as a list of (frame) scores
        """

        self.info = self.fullobj = None
        frame_score_result = self._request_frame_scores(plugin, domain, filename, data_msg= data_msg, opts=opts, classes=classes, mode=mode)
        if frame_score_result is not None:
            return frame_score_result.score

        return []


    def analyze_regions(self, plugin, domain, filename, data_msg=None, mode=AudioTransferType.AUDIO_PATH, opts=None, classes=None):
        """
         Request a analysis of 'filename' returning regions

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param filename: the name of the audio file to score
        :param mode: the way audio is submitted to the server
        :param opts: a dictionary of name/value pair options
        :param classes: optionally,  a list of classes classes to be scored

        :return: a list of (start, end) regions in seconds, each region indicates a speech region found in the submitted file.
        """
        self.info = self.fullobj = None
        region_score_result = self._request_region_scores(plugin, domain, filename, data_msg=data_msg, mode=mode, opts=opts, classes=classes)
        self.fullobj = region_score_result
        return region_score_result

    def analyze_bounding_box(self, plugin, domain, filename, data_msg=None, mode=AudioTransferType.AUDIO_PATH, opts=None, classes=None):
        """
         Request a analysis of 'filename' returning bounding box scores

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param filename: the name of the audio file to score
        :param mode: the way audio is submitted to the server
        :param opts: a dictionary of name/value pair options
        :param classes: optionally,  a list of classes classes to be scored

        :return: a list of (start, end) regions in seconds, each region indicates a speech region found in the submitted file.
        """
        self.info = self.fullobj = None
        region_score_result = self._request_bounding_box_scores(plugin, domain, filename, data_msg=data_msg, mode=mode, opts=opts, classes=classes)
        self.fullobj = region_score_result
        return region_score_result

    def _request_region_scores(self, plugin, domain, filename, data_msg=None, mode=AudioTransferType.AUDIO_PATH, opts=None, classes=None):
        """
         Request a analysis of 'filename'

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param filename: the name of the audio file to score
        :param mode: the way audio is submitted to the server
        :param opts: a dictionary of name/value pair options
        :param classes: optionally,  a list of classes classes to be scored

        :return: the analysis as a list of (region) scores
        """

        request = RegionScorerRequest()
        request.plugin = plugin
        request.domain = domain

        final_mode = InputTransferType.PATH
        if mode == AudioTransferType.AUDIO_SERIALIZED:
            final_mode = InputTransferType.SERIALIZED

        if data_msg:
            request.audio.CopyFrom(data_msg)
        else:
            audio = request.audio
            package_audio(audio, filename, mode=final_mode)

        self._add_options(request, opts)
        self._add_classes(request, classes)

        # Wrap message in an Envelope
        _, env = _wrap_message(self.client_id, request)
        # Now send the envelope
        logging.debug("Sending a (region score request) message")
        result = self._sync_request(env)
        return result.region

    def _request_bounding_box_scores(self, plugin, domain, filename, data_msg=None, mode=AudioTransferType.AUDIO_PATH, opts=None, classes=None):
        """
         Request a analysis of 'filename'

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param filename: the name of the input (image or video) file to score
        :param mode: the way input is submitted to the server
        :param opts: a dictionary of name/value pair options
        :param classes: optionally,  a list of classes classes to be scored

        :return: the analysis as a list of (region) scores
        """

        request = BoundingBoxScorerRequest()
        request.plugin = plugin
        request.domain = domain
        if data_msg:
            request.data.CopyFrom(data_msg)
        else:
            data = request.data
            package_binary_media(data, filename, mode=mode)

        self._add_options(request, opts)
        self._add_classes(request, classes)

        # Wrap message in an Envelope
        _, env = _wrap_message(self.client_id, request)
        # Now send the envelope
        logging.debug("Sending a (bounding box score request) message")
        result = self._sync_request(env)
        return result.region


    def _add_options(self, request, opts):
        if opts is not None:
            for key in opts.keys():
                opt = request.option.add()
                opt.name = key
                opt.value = opts[key]

    def _add_classes(self, request, classes):
        if classes is not None:
            for id in classes:
                request.class_id.append(id)

    def _request_frame_scores(self, plugin, domain, filename, data_msg=None, mode=AudioTransferType.AUDIO_PATH, opts=None, classes=None):
        """
         Request a analysis of 'filename'

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param filename: the name of the audio file to score
        :param mode: the threshold to use when converting the speech frame scores into regions
        :param opts: a dictionary of name/value pair options
        :param classes: optionally,  a list of classes classes to be scored

        :return: the analysis as a list of (frame) scores
        """

        request = FrameScorerRequest()
        request.plugin = plugin
        request.domain = domain

        if data_msg:
            request.audio.CopyFrom(data_msg)
        else:
            audio = request.audio
            package_audio(audio, filename, mode=mode)

        self._add_options(request, opts)
        self._add_classes(request, classes)
        # if opts is not None:
        #     for key in opts.keys():
        #         opt = request.option.add()
        #         opt.name = key
        #         opt.value = opts[key]

        # Wrap message in an Envelope
        _, env = _wrap_message(self.client_id, request)
        # Now send the envelope
        logging.debug("Sending a (frame score request) message")
        result = self._sync_request(env)
        return result.result[0]

    def enroll(self, plugin, domain, class_id, filename, data_msg=None):
        """
         Request a enrollment of 'audio'

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param class_id: the name of the class (speaker) to enroll
        :param filename: the filename to add as an audio only enrollment addition
        :param data_msg: an BinaryMedia message to add as an enrollment addition

        :return: True if enrollment successful
        """

        self.info = self.fullobj = None
        enrollment = ClassModificationRequest()
        enrollment.plugin = plugin
        enrollment.domain = domain
        enrollment.class_id = class_id
        enrollment.finalize = True
        if data_msg:
            if isinstance(data_msg, Audio):
                enrollment.addition.append(data_msg)
            else:
                enrollment.addition_media.append(data_msg)
        else:
            audio = Audio()
            package_audio(audio, filename)
            enrollment.addition.append(audio)

        # Wrap message in an Envelope
        _, env = _wrap_message(self.client_id, enrollment)
        # Now send the envelope
        logging.debug("Sending an enrollment message")
        result = self._sync_request(env)

        return result  # ClassModificationResult

        # Wrap message in an Envelope
        # request = self._wrap_message(enrollment)

        # # Now send the message
        # logging.debug("Sending a class modification request (enrollment) message")
        # self.request_socket.send(request.SerializeToString())
        # logging.debug("Sending a class modification request (enrollment) message")
        # # TODO THIS IS A SYNC REQUST, CAN BE DONE ASYN WITH A CALLBACK...
        # # Wait for the response from the server
        # # logging.info("checking for response")
        # protobuf_data = self.request_socket.recv()
        # logging.info("Received message from server...")
        # envelope = Envelope()
        # envelope.ParseFromString(protobuf_data)
        #
        # # for this use case the server will only have one response in the evevelope:
        # for i in range(len(envelope.message)):
        #     olive_msg = envelope.message[i]
        #
        #     if olive_msg.HasField("info"):
        #         self.info = olive_msg.info
        #     if olive_msg.HasField("error"):
        #         raise ExceptionFromServer('Got an error from the server: ' + olive_msg.error)
        #     else:
        #         enrollment_msg = ClassModificationResult()
        #         enrollment_msg.ParseFromString(olive_msg.message_data[0])
        #
        #         # Assume there is only one result set (for 'speech'):  frame_score_msg.result[0]
        #         # TODO - clean up return.  Maybe do something with message.
        #         self.fullobj = enrollment_msg
        #         self.info = enrollment_msg.addition_result[0].message  # CLG this would only be set if there was an issue with the enrollment
        #         return enrollment_msg.addition_result[0].successful
        #
        # return False

        # alternatively, you could send an audio buffer:
        # from scipy.io import wavfile
        # sample_rate, data = wavfile.read(filename)
        # package_buffer_audio(audio, data, data.shape[0], sample_rate)

    def unenroll(self, plugin, domain, class_id):
        """
         Unenrollment the class_id

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param class_id: the name of the class (speaker) to enroll

        :return: True if enrollment successful
        """

        self.info = self.fullobj = None
        removal = ClassRemovalRequest()
        removal.plugin = plugin
        removal.domain = domain
        removal.class_id = class_id

        # Wrap message in an Envelope
        _, request = _wrap_message(self.client_id, removal)

        logging.debug("Sending a class modification request (removal) message")
        result = self._sync_request(request)
        # do something?
        return True


    def apply_threshold(self, scores, threshold, rate):
        """
        Very simple  method to convert frame scores to regions.  If speech regions are desired
        we can provide a SAD plugin that returns regions instead of frame scores

        :param scores:
        :param threshold:
        :param rate:

        :return: frame scores a regions
        """
        inSegment = False
        start = 0
        segments = []

        for i in range (len(scores)):
            if not inSegment and scores[i] >= threshold:
                inSegment = True
                start = i
            elif inSegment and (scores[i] < threshold or i == len(scores)- 1):
                inSegment = False
                startT = ((1.0*start / rate))
                endT = (1.0* i / rate)
                segments.append((startT, endT))

        return segments

    # def apply_threshold2(self, scores, threshold, rate):
    #

    def analyze_global(self, plugin, domain, filename, data_msg=None , mode=AudioTransferType.AUDIO_PATH, opts=None, classes=None):
        """
         Request a LID analysis of 'filename'

        :param plugin: the name of the LID plugin
        :param domain: the name of the plugin domain
        :param filename: the name of the audio file to score
        :param mode: the audio transfer mode
        :param opts: a dictionary of name/value pair options
        :param classes: optionally,  a list of classes classes to be scored

        :return: the analysis result as a list of (global) scores
        """

        self.info = self.fullobj = None
        request = GlobalScorerRequest()
        request.plugin = plugin
        request.domain = domain
        if data_msg:
            request.audio.CopyFrom(data_msg)
        else:
            audio = request.audio
            package_audio(audio, filename, mode=mode)

        self._add_options(request, opts)
        self._add_classes(request, classes)

        # alternatively, you could send an audio buffer:
        # from scipy.io import wavfile
        # sample_rate, data = wavfile.read(filename)
        # package_buffer_audio(audio, data, data.shape[0], sample_rate)

        _, env = _wrap_message(self.client_id, request)
        # Now send the message
        logging.debug("Sending a global score request message")
        result = self._sync_request(env)
        return result.score

    
    def audio_modification(self, plugin, domain, filename, data_msg=None, mode=AudioTransferType.AUDIO_PATH):
        """
        Do an audio modification (such as an enhansement). This function only accepts one audio and returns on modified audio.
        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param filename: the name of the audio file to score

        :return: the analysis as a list of (frame) scores
        """

        if mode != AudioTransferType.AUDIO_PATH:
            raise Exception('oliveclient.audio_modification requires an filename path and will not work with binary audio data.')
        request = AudioModificationRequest()
        request.plugin = plugin
        request.domain = domain
        request.requested_channels = 1
        request.requested_rate = 8000

        if data_msg:
            request.modifications.append(data_msg)
        else:
            audio = Audio()
            package_audio(audio, filename, mode=mode)
            # audio = Audio()
            # audio.path = filename
            request.modifications.append(audio)

        _, env = _wrap_message(self.client_id, request)
        # Now send the message
        logging.debug("Sending a audio modification/enhancement request message")
        result = self._sync_request(env)
        return result.successful, result.modification_result[0]

    def requst_sad_adaptation(self):
        """
        Example of performing SAD adaptation
        :return:
        """

        # todo move to client example (i.e. olivelearn)

        # using Julie's sadRegression dataset...

        # Assume the working directory is root directory for the SAD regression tests

        # Setup processing variables (get this config or via command line optons
        plugin = "sad-dnn-v6a"
        domain = "multi-v1"
        new_domain_name = "python_adapted_multi-v2"

        # Build the list of files plus the regions in the those files to adaptn by parsing the input file:
        file_annotations = self.parse_annotation_file("lists/adapt_ms.lst")

        return self.adapt_supervised_old(plugin, domain, file_annotations, new_domain_name)

    def parse_annotation_file(self, filename):
        """
        Parse a file for the names of files of audio files and their regions to use for adaptation.
        :param filename: the path and name of the file that contains the input. This file must have one or more lines having 4 columns:
        # filename, class, start_region_ms, end_region_ms
        :return: the parsed output, in a dictionary indexed by the filename, each element having one or more regions,
        for example {test.wav: [(2618, 6200, 'S'), (7200, 9500, 'NS')]}
        """
        data_lines = []
        file_annotations = {}

        if not os.path.exists(filename):
            raise Exception("The annotation file '{}' does not exist".format(filename))

        with open(filename) as f:
            data_lines.extend([line.strip() for line in f.readlines()])

        # process the file
        for line in data_lines:
            pieces = line.split()

            if len(pieces) != 4:
                raise Exception("The annotation file does not contain data in the correct format, found line '{}'".format(line))

            adapt_audio_path = pieces[0]

            # assume a relative file is used, so the full path must be specified since being sent to server
            # This is being sent to server.  If full path is given, do nothing.  Otherwise make absolute.
            # TODO: this will not work from UNIX to Windows or other way around.
            # TODO: should use Python's abspath here, don't you think?
            if adapt_audio_path[0] != '/' and adapt_audio_path[1] != ':':
                adapt_audio_path = os.path.join(os.getcwd(), adapt_audio_path)

            # todo validate file is valid...

            if adapt_audio_path not in file_annotations:
                file_annotations[adapt_audio_path] = []

            class_id = pieces[1]
            start    = float(pieces[2])
            end      = float(pieces[3])

            file_annotations[adapt_audio_path].append((start, end, class_id))

        return file_annotations

    def adapt_supervised(self, plugin, domain, annotations_file_name, new_domain_name):
        """
        :param plugin: the plugin for adaptation
        :param domain: the domain for adaptation
        :param adapt_workspace: a unique label for this client's adaptation
        :param annotations_file_name: the name of a file containing annotations.
                This file contains lines with four tokens: filename, start, end, and class.
                start and end are in milliseconds, but that should change to seconds.

        :return: the full path name of the new domain.
        """
        adapt_workspace = 'adapt-'+ msgutil.get_uuid()
        processed_audio_list = []
        
        file_annotations = self.parse_annotation_file(annotations_file_name)
        for filename, regions in file_annotations.items():
            audio_id = self.preprocess_supervised_audio(plugin, domain, filename, adapt_workspace)
            if audio_id:
                processed_audio_list.append([audio_id, regions])

        if len(processed_audio_list) == 0:
            raise Exception("All audio requests failed")

        # Now convert the file based annotations into class based annotations
        protobuf_class_annots = self.convert_preprocessed_annotations(processed_audio_list)

        #Finally, complete the adaptation request by making a finalize reqeust
        return self.finalize_supervised_adaptation(plugin, domain, new_domain_name, protobuf_class_annots, adapt_workspace)

    # TODO: when requst_sad_adaptation goes away, this should go away, also.
    def adapt_supervised_old(self, plugin, domain, file_annotations, new_domain_name):
        """
        :param plugin: the plugin for adaptation
        :param domain: the domain for adaptation
        :param adapt_workspace: a unique label for this client's adaptation
        :param file_annotations: a dictionary of files to preprocess, each file has one or more annotated regions for
                processing {filename: [(start_ms, end_ms, class)]}, for example {test.wav: [(2618, 6200, 'S'), (7200, 9500, 'NS')]}
        :return: the full path name of the new domain.
        """
        adapt_workspace = 'adapt-'+ msgutil.get_uuid()
        processed_audio_list = []
        for filename, regions in file_annotations.items():
            audio_id = self.preprocess_supervised_audio(plugin, domain, filename, adapt_workspace)
            if audio_id:
                processed_audio_list.append([audio_id, regions])

        if len(processed_audio_list) == 0:
            raise Exception("All audio requests failed")

        # Now convert the file based annotations into class based annotations
        protobuf_class_annots = self.convert_preprocessed_annotations(processed_audio_list)

        #Finally, complete the adaptation request by making a finalize reqeust
        return self.finalize_supervised_adaptation(plugin, domain, new_domain_name, protobuf_class_annots, adapt_workspace)

    def preprocess_supervised_audio(self, plugin, domain, filename, adapt_workspace):
        """
         Submit audio for pre-processing phase of adaptation.

        :param plugin: the name of the plugin to adapt
        :param domain: the name of the plugin domain to adapt
        :param filename: the name of the audio file to submit to the server/plugin/domain for preprocessing
        :return: the unique id generated by the server for the preprocess audio, which must be used
        """

        # [(2.618, 6.2, 'S'), (7.2, 9.5, 'NS')]

        self.info = self.fullobj = None
        request = PreprocessAudioAdaptRequest()
        request.plugin = plugin
        request.domain = domain
        request.adapt_space = adapt_workspace
        request.class_id = "supervised"         # HACK: for supervised validation in the backend - we will fix this in a future release so not needed
        # we currently don't need to set annotations (start_t, end_t) when doing pre-processing

        # finally, set the audio:
        audio = request.audio
        # send the name of the file to the server:
        audio.path = filename
        # alternatively, you could send an audio buffer:
        # from scipy.io import wavfile
        # sample_rate, data = wavfile.read(filename)
        # package_buffer_audio(audio, data, data.shape[0], sample_rate)
        # TODO SERIALIZE EXAMPLE...

        # package the request
        _, request = _wrap_message(self.client_id, request)

        # Now send the message
        logging.debug("Sending a preprocess audio (for adaptation) message")
        self.request_socket.send(request.SerializeToString())

        # Wait for the response from the server
        # logging.info("checking for response")
        protobuf_data = self.request_socket.recv()
        logging.info("Received message from server...")

        #Unpack message
        envelope = Envelope()
        envelope.ParseFromString(protobuf_data)

        # for this use case the server will only have one response in the envelope:
        for i in range(len(envelope.message)):
            olive_msg = envelope.message[i]

            if olive_msg.HasField("info"):
                self.info = olive_msg.info
            if olive_msg.HasField("error"):
                raise ExceptionFromServer('Got an error from the server: ' + olive_msg.error)
            else:
                result_msg = PreprocessAudioAdaptResult()
                result_msg.ParseFromString(olive_msg.message_data[0])

                # get audio id from results, use for final annotations...
                # print("Preprocess audio ID {} having duration {}".format(result_msg.audio_id, result_msg.duration))
                self.fullobj = result_msg
                return result_msg.audio_id

        # preprocessing failed... TODO: thrown exception instead?
        return None

    def convert_preprocessed_annotations(self, processed_audio_list):
        """
        Convert the file annotations (a dictionary grouped by file ID, where annotations are grouped by file ID, which
        has one or more regions/classes) into class annotations (where annotations are grouped by class ID, with each
        class having one or more files, then each file having one or more regions).
        :param processed_audio_list: the list of files (indexed by an OLIVE generated ID) and
        the regions/classes annotated in that file
        :return: a dictionary of ClassAnnotation objects, indexed by class ID
        """
        # Now convert the annotations that are grouped by file into a list of annotations grouped by class ID
        # (speech, non-speech).  This is done in two passes, the first passes builds then new mapping of
        # class_id -->* audio_id -->* region,
        # then we convert this new data structure into ClassAnnotation (Protobuf) message(s)
        class_annots = {}
        for audio_id, regions in processed_audio_list:
            for region in regions:
                start    = region[0]
                end      = region[1]
                class_id = region[2]

                if class_id not in class_annots:
                    class_annots[class_id] = {}

                if audio_id not in class_annots[class_id]:
                    class_annots[class_id][audio_id] = []

                class_annots[class_id][audio_id].append((start, end))

        # now that the annotations have been grouped by class id, create the annotation protobuf(s)
        protobuf_class_annots = {}
        for class_id in class_annots.keys():
            protobuf_class_annots[class_id] = ClassAnnotation()
            protobuf_class_annots[class_id].class_id = class_id
            # Add AudioAnnotation(s)
            for audio_id in class_annots[class_id]:
                aa = AudioAnnotation() # aa = protobuf_class_annots[class_id].annotations.add() in python2.7?
                aa.audio_id = audio_id
                for region in class_annots[class_id][audio_id]:
                    # times are in milliseconds
                    ar = AnnotationRegion()  # might need to do ar = aa.regions.add() for Python2.7
                    ar.start_t = region[0]
                    ar.end_t =  region[1]
                    aa.regions.append(ar)
                protobuf_class_annots[class_id].annotations.append(aa)

        return protobuf_class_annots

    def finalize_supervised_adaptation(self, plugin, domain, new_domain_name, class_annotations, adapt_workspace):
        """
         Complete the adaptation

        :param plugin: the name of the plugin to adapt
        :param domain: the name of the plugin domain to adapt
        :param new_domain_name: the name of the new domain that is created within the plugin

        :param class_annotations: the audio annotations, grouped by class ID

        :return: the name of the new domain
        """

        self.info = self.fullobj = None
        request = SupervisedAdaptationRequest()
        request.plugin = plugin
        request.domain = domain
        request.adapt_space = adapt_workspace
        request.new_domain = new_domain_name

        # Add the class annotations
        for class_id in class_annotations:
            request.class_annotations.append(class_annotations[class_id])  # request.class_annotations.extend([class_annotations[class_id]]) for Python2.7?

        # package the request
        _, request = _wrap_message(self.client_id, request)

        # Now send the message
        logging.debug("Sending a finalize adatation message")
        self.request_socket.send(request.SerializeToString())
        # Wait for the response from the server
        protobuf_data = self.request_socket.recv()
        logging.info("Received message from server...")

        #Unpack message - boiler plate code, this can be simplified
        envelope = Envelope()
        envelope.ParseFromString(protobuf_data)

        # for this use case the server will only have one response in the envelope:
        for i in range(len(envelope.message)):
            olive_msg = envelope.message[i]

            if olive_msg.HasField("info"):
                self.info = olive_msg.info
            if olive_msg.HasField("error"):
                raise ExceptionFromServer('Got an error from the server: ' + olive_msg.error)
            else:
                result_msg = SupervisedAdaptationResult()
                result_msg.ParseFromString(olive_msg.message_data[0])
                # get the new domain
                #if hasattr(result_msg, 'new_domain') and result_msg.new_domain is not None:
                #    print("Adaptation successfully created new domain: '{}'".format(result_msg.new_domain))
                self.fullobj = result_msg
                return result_msg.new_domain

        # adapt failed... TODO: thrown exception instead?
        return None

    def _sync_request(self, env):

        # Now send the message
        logging.debug("Sending message")
        self.request_socket.send(env.SerializeToString())

        # Wait for the response from the server
        # logging.info("checking for response")
        protobuf_data = self.request_socket.recv()
        # Received message from server...  deserialize
        envelope = Envelope()
        envelope.ParseFromString(protobuf_data)

        # we should try to handle each message:
        # for i in range(len(envelope.message)):
        #     self._process_response(envelope.message[i])
        return _unwrap_reponse(envelope.message[0])

    # This should be part of the uuid module.


    def version(self):
        return olive.__version__

# todo move to message utio?

def package_buffer_audio(audio, data, num_samples, sample_rate=8000, num_channels=1):
    """
    Helper function to wrap audio data (decoded samples) into a AudioBuffer message that can submitted to the
    server instead of a file name.

    :param data:  the data as a numpy ndarray
    :param num_samples:  the number of samples
    :param sample_rate: the audio sample rate
    :param num_channels: the number of channels in the audio
    :return:
    """

    # from scipy.io import wavfile
    # sample_rate, data = wavfile.read('somefilename.wav')

    buffer = audio.audioSamples
    buffer.channels = num_channels
    buffer.samples = num_samples  #data.shape[0]
    buffer.rate = sample_rate
    buffer.bit_depth = get_bit_depth(data)
    buffer.data = data.tostring()

    return audio


def get_bit_depth(audio):
    """Not using since not assuming numpy is available..."""
    # Numpy is needed to support this...
    dt = audio.dtype
    if dt == np.int8:
        return  BIT_DEPTH_8
    elif dt == np.int16:
        return  BIT_DEPTH_16
    elif dt == np.int32:
        return BIT_DEPTH_24
    else:
        return BIT_DEPTH_32


BLOCK_TIMEOUT_MS = 200



# Utility to monitor the status of the server - can be helpful if having problems reaching the server
class ClientBrokerWorker(threading.Thread):
    """
    Performs async interactions with Olive
    """

    def __init__(self, status_socket, client_id):

        threading.Thread.__init__(self)
        self.status_socket   = status_socket
        self.client_id      = client_id
        self.request_queue  =  Queue()
        self.response_queue = {}
        self.working = False
        self.event_callback = None
        # There is some disagreement as to if this is a good thing to do.
        # The goal is to have this thread end when the main thread ends.
        # There are other ways to do it.
        # https://stackoverflow.com/questions/2564137/how-to-terminate-a-thread-when-main-program-ends
        # https://stackoverflow.com/questions/20596918/python-exception-in-thread-thread-1-most-likely-raised-during-interpreter-shutd/20598791#20598791
        self.daemon = True


    def enqueueRequest(self, message, callback):
        self.request_queue.put((message, callback))

    def stopWorker(self):
        self.working = False

    def add_event_callback(self, callback):
        self.event_callback = callback

    def run(self):
        logging.debug("Starting Olive Status Monitor Worker for id: {}".format(self.client_id))

        self.working = True
        self.status_socket.subscribe("")

        poller = zmq.Poller()
        poller.register(self.status_socket, zmq.POLLIN)

        while self.working:

            # Now check for any results from the server
            # logging.info("checking for response")
            socks = dict(poller.poll(BLOCK_TIMEOUT_MS))
            if self.status_socket in socks:
                logging.debug("Received status message from OLIVE...")
                heatbeat_data = self.status_socket.recv()
                heatbeat = Heartbeat()
                heatbeat.ParseFromString(heatbeat_data)

                # do something with heartbeat...
                if heatbeat.HasField("stats"):
                    stats = heatbeat.stats

                    logging.info("System CPU Used:    %02.01f%%" % stats.cpu_percent)
                    logging.info("System CPU Average: %02.01f%%" % stats.cpu_average)
                    logging.info("System MEM Used:    %02.01f%%" % stats.mem_percent)
                    logging.info("System MEM Max:     %02.01f%%" % stats.max_mem_percent)
                    logging.info("System SWAP Used:   %02.01f%%" % stats.swap_percent)
                    logging.info("System SWAP Max:    %02.01f%%" % stats.max_swap_percent)
                    logging.debug("Number active jobs: " + str(stats.pool_busy))
                    logging.debug("Number pending jobs: " + str(stats.pool_pending))
                    logging.debug("Number finished jobs: " + str(stats.pool_finished))
                    logging.debug("Max number jobs: " + str(stats.max_num_jobs))
                    logging.debug("Server version: " + str(stats.server_version))

        self.status_socket.close()








