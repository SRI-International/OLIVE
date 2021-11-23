

import logging
# import numpy as np  Not currently used.  maybe later...
# from queue import Queue
import queue
import signal
import threading
import zmq
import time

import olivepy.messaging.msgutil
import olivepy.messaging.msgutil as msgutil
import olivepy.messaging.response as response
# import numpy as np

import olivepy.api.oliveclient as oc

from olivepy.messaging.olive_pb2 import *


from typing import (
    List, Dict, AnyStr, Tuple, Callable
)

BLOCK_TIMEOUT_MS = 200
HEARTBEAT_TIMEOUT_SECONDS = 30

class AsyncOliveClient(threading.Thread):
    """
    This class is used to make asynchronous requests to the OLIVE server
    """

    # These constants are used by the analyze functions
    # AUDIO_PATH = 1
    # AUDIO_DECODED = 2
    # AUDIO_SERIALIZED = 3

    def __init__(self, client_id : str, address='localhost', request_port=5588, timeout_second=10):
        """
        :param client_id: The unique name of this client.  Due to a ZMQ bug this ID can not end in '1' on some systems
        :param address: the address of the olive server, such as localhost
        :param request_port: default olive port is 5588
        :param timeout_second:  time in seconds, to wait for a response from the server
        """
        threading.Thread.__init__(self)
        self.client_id = client_id

        # due to a ZMQ bug the last character of the client ID can not be 1, so remove it
        if client_id[-1] == "1":
            self.client_id = client_id[:-1]
            logging.warning("Last character of the client ID can not be '1', removing to avoid a ZMQ bug")

        self.server_address = address
        self.server_request_port = request_port
        self.server_status_port = request_port+1

        self.timeout_seconds = timeout_second

        self.request_queue = queue.Queue()
        # special queue used to emulate blocking requests
        # self.completed_sync_request_queue = queue.Queue()
        self.sync_message = {}
        self.response_queue = {}
        self.working = False
        self.request_socket = None
        self.status_socket = None
        # thread to monitor OLIVE server heartbeats
        self.worker = None

        # self.status_socket = context.socket(zmq.SUB)

        self.olive_connected = False
        self.monitor_status = False

        oc.OliveClient.setup_multithreading()


    def connect(self, monitor_status=False):
        """
        Connect this client to the server

        :param monitor_server: if true, starts a thread to monitor the server status connection for heartbeat messages
        """

        # logging.debug("Starting Olive async monitor...")
        self.monitor_status = monitor_status
        self.connection_done = threading.Event()
        self.start()
        # block until connected
        self.olive_connected = True
        self.connection_done.wait()


        logging.debug("Olive async client ready")

    def add_heartbeat_listener(self, heartbeat_callback: Callable[[Heartbeat], None]):
        """
        Register a callback function to be notified when a heartbeat is received from the OLIVE server

        :param heartbeat_callback: The callback method that is notified each time a heartbeat message is received \
        from the OLIVE server
        """
        if self.worker:
            self.worker.add_event_callback(heartbeat_callback)
        else:
            print("Unable to add a heartbeat listener because this client was not started with the status  "
                  " heartbeat monitor enabled")

    def clear_heartbeat_listeners(self):
        """
        Remove all heartbeat listeners
        """
        if self.worker:
            self.worker.clear_callback()

    def enqueue_request(self, message, callback, wrapper=None):
        """
        Add a message request to the outbound queue

        :param message:  the request message to send
        :param callback: this is called when response message is received from the server
        :param wrapper: the message wrapper
        """

        if wrapper is None:
            wrapper = response.OliveServerResponse()
        self.request_queue.put((message, callback, wrapper))

    def sync_request(self, message, wrapper=None):
        """
        Send a request to the OLIVE server, but wait for a response from the server

        :param message: the request message to send to the OLIVE server

        :return: the response from the server
        """

        if wrapper is None:
            wrapper = response.OliveServerResponse()

        # create an ID for this sync_request
        sync_id = msgutil.get_uuid()
        result_available = threading.Event()
        # result_event = None

        cb = lambda response: self._sync_callback(response, sync_id, result_available)

        self.enqueue_request(message, cb, wrapper)

        result_available.wait()
        # get the result
        if sync_id in self.sync_message:
            return self.sync_message.pop(sync_id)
        else:
            # unexpected.... callback event completed with no result
            raise Exception("Error waiting for a response from the server")


        # self.completed_sync_request_queue.put()


    def _sync_callback(self, response, msg_id, event):
        self.sync_message[msg_id] = response
        event.set()

    def run(self):
        """
        Starts the thread to handle async messages
        """
        try:
            logging.debug("Starting OLIVE Async Message Worker for id: {}".format(self.client_id))

            context = zmq.Context()
            self.request_socket = context.socket(zmq.DEALER)

            # init the request and status socket
            request_addr = "tcp://" + self.server_address + ":" + str(self.server_request_port)
            status_addr = "tcp://" + self.server_address + ":" + str(self.server_status_port)
            self.request_socket.connect(request_addr)

            if self.monitor_status:
                logging.debug("connecting to status socket...")
                self.status_socket = context.socket(zmq.SUB)
                self.status_socket.connect(status_addr)
                self.worker = ClientMonitorThread(self.status_socket, self.client_id)
                self.worker.start()
            else:
                self.worker = None

            self.working = True

            poller = zmq.Poller()
            poller.register(self.request_socket, zmq.POLLIN)
        except Exception as e:
            logging.error("Error connecting to the OLIVE server: {}".format(e))
            self.olive_connected = False
        finally:
            self.connection_done.set()

        while self.working:
            # First, send any client requests
            while not self.request_queue.empty():
                request_msg, cb, wrapper = self.request_queue.get()
                msg_id, env = msgutil._wrap_message(self.client_id, request_msg)
                # Add to our callback Q
                self.response_queue[msg_id] = (request_msg, cb, wrapper)
                # Now send the message
                logging.debug("Sending client request msg type: {}".format(env.message[0].message_type))
                self.request_socket.send(env.SerializeToString())

            # Now check for any results from the server
            # logging.info("checking for response")
            socks = dict(poller.poll(BLOCK_TIMEOUT_MS))
            if self.request_socket in socks:
                # logging.info("Received message from OLIVE...")
                protobuf_data = self.request_socket.recv()
                envelope = Envelope()
                envelope.ParseFromString(protobuf_data)

                for i in range(len(envelope.message)):
                    self._process_response(envelope.message[i])


        poller.unregister(self.request_socket)
        self.request_socket.close()
        # todo what do we do with the status socket?
        # self.status_socket.close()
        # print("Async client worker stopped")

    def _process_response(self, olive_msg):

        # Handle call and response messages (synchronous)
        if olive_msg.message_id in self.response_queue:
            # get the callback
            request_msg, cb, wrapper = self.response_queue.pop(olive_msg.message_id)

            # Check if there is was an error
            if olive_msg.HasField("error"):
                errMsg = olive_msg.error
            else:
                errMsg = None

            # todo handle exceptions
            try:
                response_msg = msgutil._unwrap_reponse(olive_msg)
                wrapper.parse_from_response(request_msg, response_msg, errMsg)
            except Exception as e:
                # request failed (although could be an allowable error)
                wrapper.parse_from_response(request_msg, None, str(e))

            # Notify the callback
            cb(wrapper)

        else:
            logging.error("Received unexpected message type: {}".format(olive_msg.message_type))

    def disconnect(self):
        """
        Closes the connection to the  OLIVE server
        """
        if self.worker:
            self.worker.stopWorker()
        self.working = False
        self.olive_connected = False
        self.join()
        self.request_socket.close()

    def is_connected(self):
        """
        Status of the connection to the OLIVE server

        :return: True if connected
        """
        return self.olive_connected

    @classmethod
    def setup_multithreading(cls):
        '''This function is only needed for multithreaded programs.  For those programs,
           you must call this function from the main thread, so it can properly set up
           your signals so that control-C will work properly to exit your program.
        '''
        # https://stackoverflow.com/questions/17174001/stop-pyzmq-receiver-by-keyboardinterrupt
        # https://stackoverflow.com/questions/23206787/check-if-current-thread-is-main-thread-in-python
        if threading.current_thread() is threading.main_thread():
            signal.signal(signal.SIGINT, signal.SIG_DFL)


    def request_plugins(self, callback: Callable[[response.OliveServerResponse], None] = None):
        """
        Used to make a PluginDirectoryRequest

        :param callback: optional method called when the OLIVE server returns a response to this request. \
        If a callback is not provided, this call blocks until a response is received from the OLIVE server.  \
        The callback method accepts one argument: OliveServerResponse

        :return: a OliveServerResponse containing information about available plugin/domains (PluginDirectoryResult)
        """
        request = PluginDirectoryRequest()
        if callback:
            self.enqueue_request(request, callback)
        else:
            return self.sync_request(request)

    def get_update_status(self, plugin, domain, callback: Callable[[response.OliveServerResponse], None] = None):
        """
        Used to make a GetUpdateStatusRequest

        :param plugin: the name of the plugin to query
        :param domain: the name of the domain to query
        :param callback: optional method called when the OLIVE server returns a response to this request. \
        If a callback is not provided, this call blocks until a response is received from the OLIVE server.  \
        The callback method accepts one argument: OliveServerResponse

        :return: a OliveServerResponse containing the update status of the requested plugin/domain  (GetUpdateStatusResult
        """
        request = GetUpdateStatusRequest()
        request.plugin = plugin
        request.domain = domain

        if callback:
            self.enqueue_request(request, callback)
        else:
            return self.sync_request(request)

    def load_plugin_domain(self, plugin, domain, callback: Callable[[response.OliveServerResponse], None]):
        """
        Used to make a request to pre-load a plugin/domain (via a LoadPluginDomainRequest message)

        :param plugin: the name of the plugin to pre-load
        :param domain: the name of hte domain to pre-load
        :param callback: optional method called when the OLIVE server returns a response to this request. \
        If a callback is not provided, this call blocks until a response is received from the OLIVE server.  \
        The callback method accepts one argument: OliveServerResponse

        :return: a OliveServerResponse containing the update status of the request  (LoadPluginDomainResult)

        """
        request = LoadPluginDomainRequest()
        request.plugin = plugin
        request.domain = domain

        if callback:
            self.enqueue_request(request, callback)
        else:
            return self.sync_request(request)

    def unload_plugin_domain(self, plugin, domain, callback: Callable[[response.OliveServerResponse], None]):
        """
        Used to make a unload plugin/domain request (RemovePluginDomainRequest).  This request will un-load a loaded \
        plugin from server memory)

        :param plugin: the name of the plugin to unload
        :param domain: the name of hte domain to unload
        :param callback: optional method called when the OLIVE server returns a response to this request. \
        If a callback is not provided, this call blocks until a response is received from the OLIVE server.  \
        The callback method accepts one argument: OliveServerResponse

        :return: a OliveServerResponse containing the status of the request  (RemovePluginDomainResult)
        """
        request = RemovePluginDomainRequest()
        request.plugin = plugin.strip()
        request.domain = domain.strip()

        if callback:
            self.enqueue_request(request, callback)
        else:
            return self.sync_request(request)


    def update_plugin_domain(self, plugin, domain, metadata, callback: Callable[[response.OliveServerResponse], None]):
        """
        Used to make a ApplyUpdateRequest

        :param plugin: the name of the plugin to update
        :param domain: the name of hte domain to update
        :param callback: optional method called when the OLIVE server returns a response to this request. \
        If a callback is not provided, this call blocks until a response is received from the OLIVE server.  \
        The callback method accepts one argument: OliveServerResponse

        :return: a OliveServerResponse containing the status of the request  (ApplyUpdateResult)
        """
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

        if callback:
            self.enqueue_request(request, callback)
        else:
            return self.sync_request(request)

    def get_active(self, callback: Callable[[response.OliveServerResponse], None]):
        """
        Used to make a GetActiveRequest

        :param callback: optional method called when the OLIVE server returns a response to this request. \
        If a callback is not provided, this call blocks until a response is received from the OLIVE server.  \
        The callback method accepts one argument: OliveServerResponse

        :return: a OliveServerResponse containing the status of the request  (GetActiveResult)
        """
        request = GetActiveRequest()

        if callback:
            self.enqueue_request(request, callback)
        else:
            return self.sync_request(request)

    def get_status(self, callback: Callable[[response.OliveServerResponse], None] = None):
        """
        Used to make a GetStatusRequest and receive a GetStatusResult

        :param callback: optional method called when the OLIVE server returns a response to the request.  If a callback is not provided, this call blocks until a response is received from the OLIVE server.  The callback method accepts one argument: OliveServerResponse

        :return: a OliveServerResponse that contains the most recent server status (GetStatusResult)
        """
        request = GetStatusRequest()
        if callback:
            self.enqueue_request(request, callback)
        else:
            return self.sync_request(request)

    def analyze_frames(self, plugin, domain, audio_input, callback: Callable[[response.OliveServerResponse], None], mode=olivepy.messaging.msgutil.AudioTransferType.AUDIO_SERIALIZED, opts=None):
        """
         Request a analysis of 'filename', returning frame scores.

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param audio_input: the audio to score
        :param callback: optional method called when the OLIVE server returns a response to this request. \
        If a callback is not provided, this call blocks until a response is received from the OLIVE server.  \
        The callback method accepts one argument: OliveServerResponse
        :param mode: the audio transfer mode
        :param opts: a dictionary of name/value pair options for this plugin request

        :return: a OliveServerResponse containing the status of the request  (FrameScorerResult)
        """

        request = FrameScorerRequest()
        request.plugin = plugin
        request.domain = domain
        audio = request.audio
        olivepy.messaging.msgutil.package_audio(audio, audio_input, mode=mode)

        if callback:
            self.enqueue_request(request, callback)
        else:
            return self.sync_request(request)

    # TODO: rename _request_region_scores to analyze_regions and generally merge the two.
    def analyze_regions(self, plugin, domain, filename, callback: Callable[[response.OliveServerResponse], None], mode=olivepy.messaging.msgutil.AudioTransferType.AUDIO_SERIALIZED):
        """
         Request a analysis of 'filename', returning regions

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param filename: the name of the audio file to score
        :param callback: optional method called when the OLIVE server returns a response to the request.  If a \
        callback is not provided, this call blocks until a response is received from the OLIVE server.  The callback \
        method accepts one argument: OliveServerResponse
        :param mode: the audio transfer mode

        :return: a OliveServerResponse containing the status of the request  (RegionScorerResult)
        """

        request = RegionScorerRequest()
        request.plugin = plugin
        request.domain = domain
        audio = request.audio
        olivepy.messaging.msgutil.package_audio(audio, filename, mode=mode)

        if callback:
            self.enqueue_request(request, callback)
        else:
            return self.sync_request(request)

    def analyze_global(self, plugin, domain, audio_input, callback: Callable[[response.OliveServerResponse], None], mode=olivepy.messaging.msgutil.AudioTransferType.AUDIO_SERIALIZED):
        """
         Request a global score analysis of 'filename'

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param audio_input: the name of the audio file to score
        :param callback: optional method called when the OLIVE server returns a response to the request.  If a \
        callback is not provided, this call blocks until a response is received from the OLIVE server.  The callback \
        method accepts one argument: OliveServerResponse
        :param mode: the audio transfer mode

        :return: a OliveServerResponse containing the status of the request  (GlobalScorerResult)
        """

        self.info = self.fullobj = None
        request = GlobalScorerRequest()
        request.plugin = plugin
        request.domain = domain
        audio = request.audio
        olivepy.messaging.msgutil.package_audio(audio, audio_input, mode=mode)

        self.enqueue_request(request, callback)
        if callback:
            self.enqueue_request(request, callback)
        else:
            return self.sync_request(request)

    def enroll(self, plugin, domain, class_id, audio_input, callback: Callable[[response.OliveServerResponse], None], mode=olivepy.messaging.msgutil.AudioTransferType.AUDIO_SERIALIZED):
        """
         Request a enrollment of 'audio'

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param class_id: the name of the class (i.e. speaker) to enroll
        :param audio_input: the Audio message to add as an enrollment addition
        :param callback: optional method called when the OLIVE server returns a response to the request.  If a \
        callback is not provided, this call blocks until a response is received from the OLIVE server.  The callback \
        method accepts one argument: OliveServerResponse
        :param mode: the audio transfer mode

        :return: a OliveServerResponse containing the status of the request  (ClassModificationResult)
        """

        enrollment = ClassModificationRequest()
        enrollment.plugin = plugin
        enrollment.domain = domain
        enrollment.class_id = class_id
        enrollment.finalize = True
        audio = Audio()
        olivepy.messaging.msgutil.package_audio(audio, audio_input, mode=mode)
        enrollment.addition.append(audio)

        if callback:
            self.enqueue_request(enrollment, callback)
        else:
            return self.sync_request(enrollment)


    def unenroll(self, plugin, domain, class_id, callback: Callable[[response.OliveServerResponse], None]):
        """
         Unenroll class_id

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param class_id: the name of the class (i.e. speaker) to remove
        :param callback: optional method called when the OLIVE server returns a response to the request.  If a \
        callback is not provided, this call blocks until a response is received from the OLIVE server.  The callback \
        method accepts one argument: OliveServerResponse

        :return: a OliveServerResponse containing the status of the request  (ClassRemovalResult)

        """

        removal = ClassRemovalRequest()
        removal.plugin = plugin
        removal.domain = domain
        removal.class_id = class_id

        if callback:
            self.enqueue_request(removal, callback)
        else:
            return self.sync_request(removal)


    def audio_modification(self, plugin, domain, audio_input, callback: Callable[[response.OliveServerResponse], None], mode=olivepy.messaging.msgutil.AudioTransferType.AUDIO_SERIALIZED):
        """
        Used to make a AudioModificationRequest (enhancement).

        :param plugin: the name of the plugin
        :param domain: the name of the plugin domain
        :param audio_input: the audio path or buffer to submit for modification
        :param callback: optional method called when the OLIVE server returns a response to the request.  If a \
        callback is not provided, this call blocks until a response is received from the OLIVE server.  The callback \
        method accepts one argument: OliveServerResponse
        :param mode: the audio transfer mode
        :return: a OliveServerResponse containing the status of the request  (AudioModificationResult)
        """
        if mode != olivepy.messaging.msgutil.AudioTransferType.AUDIO_PATH:
            raise Exception('oliveclient.audio_modification requires an filename path and will not work with binary audio data.')
        request = AudioModificationRequest()
        request.plugin = plugin
        request.domain = domain
        request.requested_channels = 1
        request.requested_rate = 8000
        audio = Audio()
        olivepy.messaging.msgutil.package_audio(audio, audio_input, mode=mode)
        request.modifications.append(audio)

        if callback:
            self.enqueue_request(request, callback)
        else:
            return self.sync_request(request)

class ClientMonitorThread(threading.Thread):
    """
    Helper used to monitor the status of the Oliveserver
    """

    def __init__(self, status_socket, client_id):

        threading.Thread.__init__(self)
        self.status_socket   = status_socket
        self.client_id      = client_id
        self.working = False
        self.event_callback = None
        # There is some disagreement as to if this is a good thing to do.
        # The goal is to have this thread end when the main thread ends.
        # There are other ways to do it.
        # https://stackoverflow.com/questions/2564137/how-to-terminate-a-thread-when-main-program-ends
        # https://stackoverflow.com/questions/20596918/python-exception-in-thread-thread-1-most-likely-raised-during-interpreter-shutd/20598791#20598791
        self.daemon = True
        self.event_callback = []


    def stopWorker(self):
        self.working = False

    def add_event_callback(self, callback: Callable[[Heartbeat], None]):
        """
        Callback function that is notified of a heartbeat

        :param callback: the function that is called with a Heartbeat object
        """
        self.event_callback.append(callback)

    def clear_callbacks(self):
        self.event_callback.clear()

    def run(self):
        # print("Starting Olive Status Monitor  for id: {}".format(self.client_id))

        self.working = True
        self.status_socket.subscribe("")

        poller = zmq.Poller()
        poller.register(self.status_socket, zmq.POLLIN)
        last_heartbeat = time.time()

        while self.working:

            # Now check for any results from the server
            # logging.info("checking for response")
            socks = dict(poller.poll(BLOCK_TIMEOUT_MS))
            if self.status_socket in socks:
                last_heartbeat = time.time()
                # print("Received status message from OLIVE...")
                heatbeat_data = self.status_socket.recv()
                heatbeat = Heartbeat()
                heatbeat.ParseFromString(heatbeat_data)

                for cb in self.event_callback:
                    cb(heatbeat)
            else:
                # Consider using the same timeout for messages?
                if time.time() - last_heartbeat > HEARTBEAT_TIMEOUT_SECONDS:
                    print("heartbeat timeout")
                    # it has been too long since a heatbeat message was received from the server... assume there server is down
                    for cb in self.event_callback:
                        cb(None)

        self.status_socket.close()








