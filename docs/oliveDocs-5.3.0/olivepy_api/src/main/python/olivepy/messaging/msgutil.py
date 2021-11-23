"""
Contains data structures that map message_type enum values
to protobuf types and vice versa. Should be updated whenever
new message types are added.
"""
import logging
import os
import uuid
from enum import Enum

from . import olive_pb2

from .olive_pb2 import *
from .olive_pb2 import GLOBAL_SCORER, REGION_SCORER, FRAME_SCORER, CLASS_ENROLLER, CLASS_MODIFIER, SUPERVISED_TRAINER, \
    SUPERVISED_ADAPTER, UNSUPERVISED_ADAPTER, AUDIO_CONVERTER, AUDIO_VECTORIZER, CLASS_EXPORTER, UPDATER, \
    LEARNING_TRAIT, \
    GLOBAL_COMPARER, \
    ScenicMessage, \
    Envelope, BIT_DEPTH_16, PCM16


from typing import (
    Optional, Generator,
    List, Set, Iterable, Tuple, Dict, AnyStr
)


def get_uuid():
  return str(uuid.uuid4())

def _wrap_message(client_id, msg):
    """
    Helper method to wrap a message for sending to the OLIVE server

    :param msg: the message to wrap in a Scenic envelope
    :param type:  The type of message to send (i.e. FRAME_SCORER_REQUEST)
    :return:  msg wrapped in an envelope, which can be sent to the OLIVE server
    """

    msg_id = get_uuid()

    # Assume message is not a list
    sm = ScenicMessage()
    sm.message_id = msg_id
    sm.message_type = message_type_map[msg.DESCRIPTOR]
    sm.message_data.append(msg.SerializeToString())

    request = Envelope()
    request.sender_id = client_id
    request.message.extend([sm])

    return msg_id, request


def _unwrap_reponse(olive_msg):
    # Check if there is was an error
    if olive_msg.HasField("error"):
        raise ExceptionFromServer('Request failed with error: ' + olive_msg.error)
    elif olive_msg.HasField('info'):
        raise AllowableErrorFromServer(olive_msg.info)

    #  Either extract the response message or the error message if the request failed
    if len(olive_msg.message_data) > 0:
        # We received a message
        response_msg = type_class_map[olive_msg.message_type]()
        response_msg.ParseFromString(olive_msg.message_data[0])
        logging.debug("Received message type: {},  ".format(olive_msg.message_type, response_msg.DESCRIPTOR))
        return response_msg
    else:
        # Unexpected - should have error or info set...
        raise ExceptionFromServer('Unexpected failure: server response message type {} contained no data'.format( type_class_map[olive_msg.message_type]().DESCRIPTOR))

class ExceptionFromServer(Exception):
    """
    This exception means that an error occured on the server side, and this error is
       being sent "up the chain" on the client side.  Otherwise, it is identical to
       Python's plain old Exception
    """
    pass


class AllowableErrorFromServer(Exception):
    """
    This exception means that the server could not complete a request; however, the reason it could not do isn't
    considered an error.  This special case most often occurs when requesting analysis of a submission that contains
    no speech, in which case the analysis could not complete since there was not speech and not due to an error running
    the plugin.  Otherwise, this is identical to Python's plain old Exception
    """
    pass

# Depricated, use InputTransferType instead of AudioTransferType
class AudioTransferType(Enum):
    """
    The method used to send audio to the OLIVE server.   There are three options for sending audio to the server:

    1. AUDIO_PATH: Send the path of the audio file to the server.  NOTE: If using this option, the path must be
    accessible to the server

    2. AUDIO_DECODED: Send the audio as a buffer of decoded samples (PCM-16).  This option is not well supported
    by this client since it does not

    3. AUDIO_SERIALIZED: Send the file as a binary buffer
    """
    AUDIO_PATH          = 1
    AUDIO_DECODED       = 2
    AUDIO_SERIALIZED    = 3


class InputTransferType(Enum):
    """
    The method used to send audio/data to the OLIVE server.   There are three options for sending data to the server:

    1. PATH: Send the path of the audio file to the server.  NOTE: If using this option, the path must be
    accessible to the server

    2. DECODED: Send the audio as a buffer of decoded samples (PCM-16).  This option is not well supported
    by this client since it does not

    3. SERIALIZED: Send the file as a binary buffer
    """
    PATH          = 1
    DECODED       = 2
    SERIALIZED    = 3

class OliveInputDataType(Enum):
    """
    The type of input data send to the OLIVE server.
    """
    BINARY_DATA_TYPE    = 1 # Generic type for all media types -
    AUDIO_DATA_TYPE     = 2 # Legacy data type for audio only data (image/video not supported)
    TEXT_DATA_TYPE      = 3 # Text input used for MT plugins/workflows


data_type_class_map = {
  OliveInputDataType.BINARY_DATA_TYPE:     BINARY,
  OliveInputDataType.AUDIO_DATA_TYPE:      AUDIO,
  OliveInputDataType.TEXT_DATA_TYPE:       TEXT,
}

#fixme:  rename - since we can serialize any binary file this way
def serialize_audio(filename: str) -> AnyStr:
    """
    Helper function used to read in an audio file and output a serialized buffer.  Can be used with package_audio() \
    when using the AUDIO_SERIALIZED mode and the audio input has not already been serialized

    :param filename: the local path to the file to serialize

    :return: the contents of the file as a byte buffer, otherwise an exception if the file can not be opened.  \
    This buffer contains the raw content of the file, it does NOT contain encoded samples
    """

    if not os.path.exists(os.path.expanduser(filename)):
        raise Exception(
            "Error serializing an audio file, the  file '{}' does not exist.".format(filename))

    with open(os.path.expanduser(filename), 'rb') as f:
        serialized_buffer = f.read()

    # return the buffer
    return serialized_buffer

def package_audio(audio_msg: Audio,
                  audio_data: AnyStr,
                  annotations=None,
                  selected_channel=None,
                  mode=InputTransferType.PATH,
                  num_channels=None,
                  sample_rate=None,
                  num_samples=None,
                  validate_local_path=True,
                  label=None):
    """
    :param audio_msg: the Olive Audio message to populate
    :param audio_data:  either a filename or binary buffer
    :param annotations: a list of tuple start/end regions (in seconds)
    :param selected_channel: if audio_data is multi-channel then select this channel for processing
    :param mode: the submission mode: pathname, serialized, samples
    :param num_channels: the number of channels in the audio
    :param sample_rate: the sample rate of the audio
    :param num_samples: the number of samples in the audio.
    :param validate_local_path: if sending audio as a path, throw an exception if the file does not exist. We let this be an option for the possible case where the client may want to provide a path on the server's filesystem, but not the local filesystem.

    :return: a valid Audio message

    :raises Exception if unable to package the audio for the specified mode.
    """

    if mode != InputTransferType.PATH and mode != InputTransferType.DECODED and mode != InputTransferType.SERIALIZED:
        raise Exception(
            'Called package_audio with an unknown mode. Must be PATH, DECODED, or SERIALIZED.')

    # only supporting pathname now
    if mode == InputTransferType.PATH:
        if validate_local_path:
            if not os.path.exists(audio_data):
                raise Exception(
                    "Error creating an OLIVE Audio message, the Audio file '{}' does not exist.".format(audio_data))

        audio_msg.path = audio_data
    else:
        audio_buffer = audio_msg.audioSamples
        if isinstance(audio_data, bytes):
            # audio has already been converted to a buffer... no need to change
            audio_buffer.data = audio_data
        else:
            # Assume we have a filename with we will serialize (decoded samples not supported)
            if mode != InputTransferType.SERIALIZED:
                raise Exception("Converting '{}' into a decoded buffer is not supported.  Client must "
                                "manually decode the file and pass bytes to package_audio()".format(audio_data))
            buffer = serialize_audio(audio_data)
            audio_buffer.data = buffer

        if mode == InputTransferType.SERIALIZED:
            # olive.proto says these are all ignored for serialized buffers:
            # channels, rate, bitdepth, channels
            audio_buffer.serialized_file = True

        if mode == InputTransferType.DECODED:
            # This mode assumes the client has passed in a numpy array of samples, but we don't assume numpy is
            # installed for all clients so we don't do checks in ths this code

            # Get the data as shorts:
            # not if audio_data.dtype.kind == np.dtype(np.integer).kind:
            # audio_data = audio_data.astype( np.int16 ).flatten().tolist()
            # raise Exception("Error: Transferring decoded samples not supported")

            problem = ''
            if num_channels is None or num_channels == 0:
                problem += 'channel '
            if sample_rate is None or sample_rate == 0:
                problem += 'sample_rate '
            if num_samples is None or num_samples == 0:
                problem += 'num_samples'
            if problem != '':
                raise Exception('Error: can not create an OLIVE audio message from decoded samples because missing required argument(s): {}'.format(problem))
            audio_buffer.serialized_file    = False
            audio_buffer.channels = num_channels
            audio_buffer.rate = sample_rate
            audio_buffer.samples = num_samples
            audio_buffer.bit_depth = BIT_DEPTH_16
            audio_buffer.encoding = PCM16

    if annotations:
        for a in annotations:
            # np.float32(a[0] would be better but can we assume numpy is installed?
            region = audio_msg.regions.add()
            region.start_t = a[0]
            region.end_t = a[1]

    if selected_channel:
        # we can't do much validation, but if they selected a channel and specified the number of channels
        if num_channels:
            if selected_channel > num_channels:
                raise Exception(
                    "Error: can not select channel '{}' if audio only contains '{}' channel(s)".format(selected_channel, num_channels))

        if selected_channel < 1:
            raise Exception(
                "Error: invalid value for selected channel '{}'.  Channel must be 1 or higher ".format(selected_channel))

        audio_msg.selected_channel = selected_channel

    if label:
        audio_msg.label = label

    return audio_msg


def package_binary_media(binary_media_msg: BinaryMedia,
                         media_data: AnyStr,
                         annotations=None,
                         mode=InputTransferType.PATH,
                         validate_local_path=True,
                         label=None,
                         selected_channel=None):
    """
    :param binary_media_msg: the Olive BinaryMedia message to populate
    :param media_data: either a filename or binary buffer
    :param annotations: a list of tuple start/end regions (in seconds)
    :param mode: the submission mode: pathname, serialized, samples
    :param validate_local_path: if sending audio as a path, throw an exception if the file does not exist. We let this be an option for the possible case where the client may want to provide a path on the server's filesystem, but not the local filesystem.

    :return: a valid Audio message

    :raises Exception if unable to package the audio for the specified mode.
    """

    print("adding binary media")
    # todo support selected channel (if audio is to be handled form this data), label, and annotations
    if mode != InputTransferType.PATH  and mode != InputTransferType.SERIALIZED:
        raise Exception(
            'Called package_visual_media with an unknown mode. Must be AUDIO_PATH, or AUDIO_SERIALIZED.')

    # only supporting pathname now
    if mode == InputTransferType.PATH:
        if validate_local_path:
            if not os.path.exists(media_data):
                raise Exception(
                    "Error creating an OLIVE media message, the Audio file '{}' does not exist.".format(media_data))

        binary_media_msg.path = media_data
    else:
        media_buffer = binary_media_msg.buffer
        if isinstance(media_data, bytes):
            # audio has already been converted to a buffer... no need to change
            media_buffer.data = media_data
        else:
            # Assume we have a filename with we will serialize (decoded samples not supported)
            if mode != InputTransferType.SERIALIZED:
                raise Exception("Converting '{}' into a decoded buffer is not supported.".format(media_data))
            buffer = serialize_audio(media_data)
            media_buffer.data = buffer

    if label:
        binary_media_msg.label = label
    if selected_channel:
        binary_media_msg.selected_channel = selected_channel

    if annotations:
        classic_region = binary_media_msg.regions.add()
        for a in annotations:
            # np.float32(a[0] would be better but can we assume numpy is installed?
            region = classic_region.regions.add()
            # print("Adding region: {} to {}".format(a[0], a[1]))
            region.start_t = a[0]
            region.end_t = a[1]

    return binary_media_msg



# Message types that require a plugin/domain
plugin_message_map ={
    REGION_SCORER_REQUEST,
    GLOBAL_SCORER_REQUEST,
    GLOBAL_COMPARER_REQUEST,
    FRAME_SCORER_REQUEST,
    SUPERVISED_ADAPTATION_REQUEST,
    UNSUPERVISED_ADAPTATION_REQUEST,
    TEXT_TRANSFORM_REQUEST,
    AUDIO_ALIGN_REQUEST,
    CLASS_MODIFICATION_REQUEST,
    CLASS_REMOVAL_REQUEST,
    PLUGIN_2_PLUGIN_REQUEST,
    BOUNDING_BOX_REQUEST
}
# Internal pimientos
debug_message_map ={
    SCORE_OUTPUT_TRANSFORMER_RESULT,
    DATA_OUTPUT_TRANSFORMER_RESULT
}

type_class_map = {
  PLUGIN_DIRECTORY_REQUEST:     olive_pb2.PluginDirectoryRequest,
  PLUGIN_DIRECTORY_RESULT:      olive_pb2.PluginDirectoryResult,
  GLOBAL_SCORER_REQUEST:        olive_pb2.GlobalScorerRequest,
  GLOBAL_SCORER_RESULT:         olive_pb2.GlobalScorerResult,
  REGION_SCORER_REQUEST:        olive_pb2.RegionScorerRequest,
  REGION_SCORER_RESULT:         olive_pb2.RegionScorerResult,
  FRAME_SCORER_REQUEST:         olive_pb2.FrameScorerRequest,
  FRAME_SCORER_RESULT:          olive_pb2.FrameScorerResult,
  CLASS_MODIFICATION_REQUEST:   olive_pb2.ClassModificationRequest,
  CLASS_MODIFICATION_RESULT:    olive_pb2.ClassModificationResult,
  CLASS_REMOVAL_REQUEST:        olive_pb2.ClassRemovalRequest,
  CLASS_REMOVAL_RESULT:         olive_pb2.ClassRemovalResult,
  GET_ACTIVE_REQUEST:           olive_pb2.GetActiveRequest,
  GET_ACTIVE_RESULT:            olive_pb2.GetActiveResult,
  LOAD_PLUGIN_REQUEST:          olive_pb2.LoadPluginDomainRequest,
  LOAD_PLUGIN_RESULT:           olive_pb2.LoadPluginDomainResult,
  GET_STATUS_REQUEST:           olive_pb2.GetStatusRequest,
  GET_STATUS_RESULT:            olive_pb2.GetStatusResult,
  HEARTBEAT:                    olive_pb2.Heartbeat,
  PREPROCESS_AUDIO_TRAIN_REQUEST:     olive_pb2.PreprocessAudioTrainRequest,
  PREPROCESS_AUDIO_TRAIN_RESULT:      olive_pb2.PreprocessAudioTrainResult,
  PREPROCESS_AUDIO_ADAPT_REQUEST:     olive_pb2.PreprocessAudioAdaptRequest,
  PREPROCESS_AUDIO_ADAPT_RESULT:      olive_pb2.PreprocessAudioAdaptResult,
  SUPERVISED_TRAINING_REQUEST:        olive_pb2.SupervisedTrainingRequest,
  SUPERVISED_TRAINING_RESULT:         olive_pb2.SupervisedTrainingResult,
  SUPERVISED_ADAPTATION_REQUEST:      olive_pb2.SupervisedAdaptationRequest,
  SUPERVISED_ADAPTATION_RESULT:       olive_pb2.SupervisedAdaptationResult,
  UNSUPERVISED_ADAPTATION_REQUEST:    olive_pb2.UnsupervisedAdaptationRequest,
  UNSUPERVISED_ADAPTATION_RESULT:     olive_pb2.UnsupervisedAdaptationResult,
  CLASS_ANNOTATION:                   olive_pb2.ClassAnnotation,
  AUDIO_ANNOTATION:                   olive_pb2.AudioAnnotation,
  ANNOTATION_REGION:                  olive_pb2.AnnotationRegion,
  REMOVE_PLUGIN_REQUEST:              olive_pb2.RemovePluginDomainRequest,
  REMOVE_PLUGIN_RESULT:               olive_pb2.RemovePluginDomainResult,
  AUDIO_MODIFICATION_REQUEST:         olive_pb2.AudioModificationRequest,
  AUDIO_MODIFICATION_RESULT:          olive_pb2.AudioModificationResult,
  PLUGIN_AUDIO_VECTOR_REQUEST:        olive_pb2.PluginAudioVectorRequest,
  PLUGIN_AUDIO_VECTOR_RESULT:         olive_pb2.PluginAudioVectorResult,

  CLASS_EXPORT_REQUEST:               olive_pb2.ClassExportRequest,
  CLASS_EXPORT_RESULT:                olive_pb2.ClassExportResult,
  CLASS_IMPORT_REQUEST:               olive_pb2.ClassImportRequest,
  CLASS_IMPORT_RESULT:                olive_pb2.ClassImportResult,

  APPLY_UPDATE_REQUEST:               olive_pb2.ApplyUpdateRequest,
  APPLY_UPDATE_RESULT:                olive_pb2.ApplyUpdateResult,
  GET_UPDATE_STATUS_REQUEST:          olive_pb2.GetUpdateStatusRequest,
  GET_UPDATE_STATUS_RESULT:           olive_pb2.GetUpdateStatusResult,

  GLOBAL_COMPARER_REQUEST:            olive_pb2.GlobalComparerRequest,
  GLOBAL_COMPARER_RESULT:             olive_pb2.GlobalComparerResult,

  WORKFLOW_ACTUALIZE_REQUEST:                    olive_pb2.WorkflowActualizeRequest,
  WORKFLOW_ACTUALIZE_RESULT:                    olive_pb2.WorkflowActualizeResult,
  WORKFLOW_ANALYSIS_REQUEST:           olive_pb2.WorkflowAnalysisRequest,
  WORKFLOW_ANALYSIS_RESULT:           olive_pb2.WorkflowAnalysisResult,
  WORKFLOW_ENROLL_REQUEST:             olive_pb2.WorkflowEnrollRequest,
  WORKFLOW_UNENROLL_REQUEST:           olive_pb2.WorkflowUnenrollRequest,
  WORKFLOW_ADAPT_REQUEST:              olive_pb2.WorkflowAdaptRequest,
  WORKFLOW_ADAPT_RESULT:              olive_pb2.WorkflowAdaptResult,
  WORKFLOW_DEFINITION:                olive_pb2.WorkflowDefinition,
  WORKFLOW_TASK:                      olive_pb2.WorkflowTask,
  ABSTRACT_WORKFLOW_TASK:             olive_pb2.AbstractWorkflowPluginTask,
  CONDITIONAL_WORKFLOW_TASK:          olive_pb2.ConditionalWorkflowPluginTask,


  OLIVE_NODE:                         olive_pb2.OliveNodeWorkflow,
  WORKFLOW_JOB_RESULT:                olive_pb2.WorkflowJobResult,
  WORKFLOW_TASK_RESULT:               olive_pb2.WorkflowTaskResult,
  WORKFLOW_DATA_REQUEST:              olive_pb2.WorkflowDataRequest,
  WORKFLOW_DATA_RESULT:               olive_pb2.WorkflowDataResult,
  WORKFLOW_CLASS_REQUEST:             olive_pb2.WorkflowClassStatusRequest,
  WORKFLOW_CLASS_RESULT:             olive_pb2.WorkflowClassStatusResult,

  AUDIO_ALIGN_REQUEST:                olive_pb2.AudioAlignmentScoreRequest,
  AUDIO_ALIGN_RESULT:                 olive_pb2.AudioAlignmentScoreResult,
  TEXT_TRANSFORM_REQUEST:             olive_pb2.TextTransformationRequest,
  TEXT_TRANSFORM_RESULT:              olive_pb2.TextTransformationResult,
  PREPROCESSED_AUDIO_RESULT:          olive_pb2.PreprocessedAudioResult,
  DYNAMIC_PLUGIN_REQUEST:             olive_pb2.DynamicPluginRequest,
  PLUGIN_2_PLUGIN_REQUEST:            olive_pb2.Plugin2PluginRequest,
  PLUGIN_2_PLUGIN_RESULT:             olive_pb2.Plugin2PluginResult,
  WORKFlOW_TEXT_RESULT:             olive_pb2.WorkflowTextResult,
  SCORE_OUTPUT_TRANSFORMER_REQUEST: olive_pb2.ScoreOutputTransformRequest,
  SCORE_OUTPUT_TRANSFORMER_RESULT:  olive_pb2.ScoreOutputTransformResult,
  DATA_OUTPUT_TRANSFORMER_REQUEST: olive_pb2.DataOutputTransformRequest,
  DATA_OUTPUT_TRANSFORMER_RESULT:  olive_pb2.DataOutputTransformResult,
  BOUNDING_BOX_REQUEST:  olive_pb2.BoundingBoxScorerRequest,
  BOUNDING_BOX_RESULT:  olive_pb2.BoundingBoxScorerResult,
  BINARY_MEDIA_RESULT: olive_pb2.WorkflowBinaryMediaResult,

  INVALID_MESSAGE:                    None,
}

class_type_map = {v:k for k, v in list(type_class_map.items())}

message_type_map = {
  olive_pb2._PLUGINDIRECTORYREQUEST:    PLUGIN_DIRECTORY_REQUEST,
  olive_pb2._PLUGINDIRECTORYRESULT:     PLUGIN_DIRECTORY_RESULT,
  olive_pb2._GLOBALSCORERREQUEST:       GLOBAL_SCORER_REQUEST,
  olive_pb2._GLOBALSCORERRESULT:        GLOBAL_SCORER_RESULT,
  olive_pb2._REGIONSCORERREQUEST:       REGION_SCORER_REQUEST,
  olive_pb2._REGIONSCORERRESULT:        REGION_SCORER_RESULT,
  olive_pb2._FRAMESCORERREQUEST:        FRAME_SCORER_REQUEST,
  olive_pb2._FRAMESCORERRESULT:         FRAME_SCORER_RESULT,
  olive_pb2._CLASSMODIFICATIONREQUEST:  CLASS_MODIFICATION_REQUEST,
  olive_pb2._CLASSMODIFICATIONRESULT:   CLASS_MODIFICATION_RESULT,
  olive_pb2._CLASSREMOVALREQUEST:       CLASS_REMOVAL_REQUEST,
  olive_pb2._CLASSREMOVALRESULT:        CLASS_REMOVAL_RESULT,
  olive_pb2._GETACTIVEREQUEST:          GET_ACTIVE_REQUEST,
  olive_pb2._GETACTIVERESULT:           GET_ACTIVE_RESULT,
  olive_pb2._LOADPLUGINDOMAINREQUEST:   LOAD_PLUGIN_REQUEST,
  olive_pb2._LOADPLUGINDOMAINRESULT:    LOAD_PLUGIN_RESULT,
  olive_pb2._GETSTATUSREQUEST:          GET_STATUS_REQUEST,
  olive_pb2._GETSTATUSRESULT:           GET_STATUS_RESULT,
  olive_pb2._HEARTBEAT:                 HEARTBEAT,
  olive_pb2._PREPROCESSAUDIOTRAINREQUEST:	PREPROCESS_AUDIO_TRAIN_REQUEST,
  olive_pb2._PREPROCESSAUDIOTRAINRESULT:	PREPROCESS_AUDIO_TRAIN_RESULT,
  olive_pb2._PREPROCESSAUDIOADAPTREQUEST:	PREPROCESS_AUDIO_ADAPT_REQUEST,
  olive_pb2._PREPROCESSAUDIOADAPTRESULT:	PREPROCESS_AUDIO_ADAPT_RESULT,
  olive_pb2._SUPERVISEDTRAININGREQUEST:	SUPERVISED_TRAINING_REQUEST,
  olive_pb2._SUPERVISEDTRAININGRESULT:	    SUPERVISED_TRAINING_RESULT,
  olive_pb2._SUPERVISEDADAPTATIONREQUEST:	SUPERVISED_ADAPTATION_REQUEST,
  olive_pb2._SUPERVISEDADAPTATIONRESULT:	SUPERVISED_ADAPTATION_RESULT,
  olive_pb2._UNSUPERVISEDADAPTATIONREQUEST:UNSUPERVISED_ADAPTATION_REQUEST,
  olive_pb2._UNSUPERVISEDADAPTATIONRESULT:	UNSUPERVISED_ADAPTATION_RESULT,
  olive_pb2._CLASSANNOTATION:	            CLASS_ANNOTATION,
  olive_pb2._AUDIOANNOTATION:	            AUDIO_ANNOTATION,
  olive_pb2._ANNOTATIONREGION:	            ANNOTATION_REGION,
  olive_pb2._REMOVEPLUGINDOMAINREQUEST:	REMOVE_PLUGIN_REQUEST,
  olive_pb2._REMOVEPLUGINDOMAINRESULT:	    REMOVE_PLUGIN_RESULT,
  olive_pb2._AUDIOMODIFICATIONREQUEST:	    AUDIO_MODIFICATION_REQUEST,
  olive_pb2._AUDIOMODIFICATIONRESULT:	    AUDIO_MODIFICATION_RESULT,
  olive_pb2._PLUGINAUDIOVECTORREQUEST:	PLUGIN_AUDIO_VECTOR_REQUEST,
  olive_pb2._PLUGINAUDIOVECTORRESULT:	PLUGIN_AUDIO_VECTOR_RESULT,

  olive_pb2._CLASSEXPORTREQUEST:	CLASS_EXPORT_REQUEST,
  olive_pb2._CLASSEXPORTRESULT:	CLASS_EXPORT_RESULT,
  olive_pb2._CLASSIMPORTREQUEST:	CLASS_IMPORT_REQUEST,
  olive_pb2._CLASSIMPORTRESULT:	CLASS_IMPORT_RESULT,

  olive_pb2._APPLYUPDATEREQUEST:            APPLY_UPDATE_REQUEST,
  olive_pb2._APPLYUPDATERESULT:             APPLY_UPDATE_RESULT,
  olive_pb2._GETUPDATESTATUSREQUEST:   GET_UPDATE_STATUS_REQUEST,
  olive_pb2.GET_UPDATE_STATUS_RESULT:  GET_UPDATE_STATUS_RESULT,

  olive_pb2._GLOBALCOMPARERREQUEST:  GLOBAL_COMPARER_REQUEST,
  olive_pb2._GLOBALCOMPARERRESULT:  GLOBAL_SCORER_RESULT,

  olive_pb2._WORKFLOWACTUALIZEREQUEST: WORKFLOW_ACTUALIZE_REQUEST,
  olive_pb2._WORKFLOWACTUALIZERESULT: WORKFLOW_ACTUALIZE_RESULT,
  olive_pb2._WORKFLOWANALYSISREQUEST: WORKFLOW_ANALYSIS_REQUEST,
  olive_pb2._WORKFLOWANALYSISRESULT: WORKFLOW_ANALYSIS_RESULT,
  olive_pb2._WORKFLOWENROLLREQUEST: WORKFLOW_ENROLL_REQUEST,
  olive_pb2._WORKFLOWUNENROLLREQUEST: WORKFLOW_UNENROLL_REQUEST,
  olive_pb2._WORKFLOWADAPTREQUEST: WORKFLOW_ADAPT_REQUEST,
  olive_pb2._WORKFLOWADAPTRESULT: WORKFLOW_ADAPT_RESULT,
  olive_pb2._WORKFLOWDEFINITION: WORKFLOW_DEFINITION,
  olive_pb2._WORKFLOWTASK: WORKFLOW_TASK,
  olive_pb2._ABSTRACTWORKFLOWPLUGINTASK: ABSTRACT_WORKFLOW_TASK,
  olive_pb2._CONDITIONALWORKFLOWPLUGINTASK: CONDITIONAL_WORKFLOW_TASK,
  olive_pb2._OLIVENODEWORKFLOW:  OLIVE_NODE,
  olive_pb2._WORKFLOWJOBRESULT:  WORKFLOW_JOB_RESULT,
  olive_pb2._WORKFLOWTASKRESULT:  WORKFLOW_TASK_RESULT,
  olive_pb2._WORKFLOWDATAREQUEST: WORKFLOW_DATA_REQUEST,
  olive_pb2._WORKFLOWDATARESULT:  WORKFLOW_DATA_RESULT,
  olive_pb2._WORKFLOWCLASSSTATUSREQUEST:  WORKFLOW_CLASS_REQUEST,
  olive_pb2._WORKFLOWCLASSSTATUSRESULT:  WORKFLOW_CLASS_RESULT,

  olive_pb2._AUDIOALIGNMENTSCOREREQUEST:  AUDIO_ALIGN_REQUEST,
  olive_pb2._AUDIOALIGNMENTSCORERESULT:  AUDIO_ALIGN_RESULT,
  olive_pb2._TEXTTRANSFORMATIONREQUEST:  TEXT_TRANSFORM_REQUEST,
  olive_pb2._TEXTTRANSFORMATIONRESULT:  TEXT_TRANSFORM_RESULT,
  olive_pb2._PREPROCESSEDAUDIORESULT:  PREPROCESSED_AUDIO_RESULT,
  olive_pb2._DYNAMICPLUGINREQUEST:  DYNAMIC_PLUGIN_REQUEST,
  olive_pb2._PLUGIN2PLUGINREQUEST:  PLUGIN_2_PLUGIN_REQUEST,
  olive_pb2._PLUGIN2PLUGINRESULT:  PLUGIN_2_PLUGIN_RESULT,
  olive_pb2._WORKFLOWTEXTRESULT:  WORKFlOW_TEXT_RESULT,
  olive_pb2._SCOREOUTPUTTRANSFORMREQUEST:  SCORE_OUTPUT_TRANSFORMER_REQUEST,
  olive_pb2._SCOREOUTPUTTRANSFORMRESULT:  SCORE_OUTPUT_TRANSFORMER_RESULT,
  olive_pb2._DATAOUTPUTTRANSFORMREQUEST:  DATA_OUTPUT_TRANSFORMER_REQUEST,
  olive_pb2._DATAOUTPUTTRANSFORMRESULT:  DATA_OUTPUT_TRANSFORMER_RESULT,
  olive_pb2._BOUNDINGBOXSCORERREQUEST:  BOUNDING_BOX_REQUEST,
  olive_pb2._BOUNDINGBOXSCORERRESULT:  BOUNDING_BOX_RESULT,
  olive_pb2._WORKFLOWBINARYMEDIARESULT:  BINARY_MEDIA_RESULT,

}

type_message_map = {v:k for k, v in list(message_type_map.items())}

metadata_type_class_map = {
  STRING_META:     olive_pb2.StringMetadata,
  INTEGER_META:      olive_pb2.IntegerMetadata,
  DOUBLE_META:        olive_pb2.DoubleMetadata,
  BOOLEAN_META:         olive_pb2.BooleanMetadata,
}

metadata_class_type_map = {v:k for k, v in list(metadata_type_class_map.items())}

#
trait_type_class_map = {
  GLOBAL_SCORER:      olive_pb2.GlobalScorerRequest,
  REGION_SCORER:      olive_pb2.RegionScorerRequest,
  FRAME_SCORER:       olive_pb2.FrameScorerRequest,
  CLASS_ENROLLER:     olive_pb2.ClassModificationRequest,
  CLASS_MODIFIER:     olive_pb2.ClassModificationRequest,
  SUPERVISED_TRAINER: olive_pb2.SupervisedTrainingRequest,
  SUPERVISED_ADAPTER: olive_pb2.SupervisedAdaptationRequest,
  UNSUPERVISED_ADAPTER: olive_pb2.UnsupervisedAdaptationRequest,
  AUDIO_CONVERTER:    olive_pb2.AudioModificationRequest,
  AUDIO_VECTORIZER:   olive_pb2.PluginAudioVectorRequest,
  CLASS_EXPORTER:     olive_pb2.ClassExportRequest,
  UPDATER:            olive_pb2.ApplyUpdateRequest,
  GLOBAL_COMPARER:    olive_pb2.GlobalComparerRequest,
  AUDIO_ALIGNMENT_SCORER: olive_pb2.AudioAlignmentScoreRequest,
  TEXT_TRANSFORMER:   olive_pb2.TextTransformationRequest,
  PLUGIN_2_PLUGIN:   olive_pb2.Plugin2PluginRequest,
  SCORE_OUTPUT_TRANSFORMER: olive_pb2.ScoreOutputTransformRequest,
  DATA_OUTPUT_TRANSFORMER: olive_pb2.DataOutputTransformRequest,
  BOUNDING_BOX_SCORER: olive_pb2.BoundingBoxScorerRequest
}

trait_class_type_map = {v:k for k, v in list(trait_type_class_map.items())}



