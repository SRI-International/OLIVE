
import os
import unittest
import numpy as np

import olivepy.messaging.msgutil
# from .test_utils import TestUtils
from olivepy.messaging.olive_pb2 import *
from olivepy.test.test_utils import TestUtils

TEST_WORKFLOW_DEF = "sad_lid.workflow"
TEST_WORKFLOW_W_ERRORS_DEF = "sad_exception_frames_workflow.wkf"

class TestAudio(unittest.TestCase):
    '''Tests for packaging audion'''
    
    def setUp(self):

        test_data_root = os.path.join(TestUtils.test_data_root)
        if not os.path.exists(test_data_root):
            self.skipTest("Test data dir  '{}' does not exist".format(TestUtils.TEST_DATA_ROOT_NAME))

        # confirm we have our expected test data
        self.test_short_file = TestUtils.check_resource(
            os.path.join(test_data_root, "testSuite", "stress", "short_30ms_16k_1ch_16b.wav"))


    # @classmethod
    # def setUpClass(cls):
    #
    #     # First make sure our test data exists
    #
    #
    #     cls.client = oc.OliveClient(cls.__class__.__name__, "localhost", 5588, 10)
    #     cls.client.connect()

    # @classmethod
    # def tearDownClass(cls):
    #     cls.client.disconnect()

    def test_audiio_path(self):
        """Tests packaging an audio input as a file path"""

        audio_msg = Audio()

        # test basic path:
        olivepy.messaging.msgutil.package_audio(audio_msg, self.test_short_file)

        self.assertEqual(audio_msg.path, self.test_short_file)
        self.assertEqual(len(audio_msg.regions), 0)
        self.assertFalse(audio_msg.HasField('audioSamples'))
        self.assertFalse(audio_msg.HasField('selected_channel'))
        self.assertFalse(audio_msg.HasField('label'))

        # make sure it handles the transfer type:
        audio_msg = Audio()
        olivepy.messaging.msgutil.package_audio(audio_msg, self.test_short_file, mode=olivepy.messaging.msgutil.AudioTransferType.AUDIO_PATH)
        self.assertEqual(audio_msg.path, self.test_short_file)
        self.assertEqual(len(audio_msg.regions), 0)
        self.assertFalse(audio_msg.HasField('audioSamples'))
        self.assertFalse(audio_msg.HasField('selected_channel'))
        self.assertFalse(audio_msg.HasField('label'))


        # test it handles  bad path:
        audio_msg = Audio()
        olivepy.messaging.msgutil.package_audio(audio_msg, "/tmp/foo/bar/nofile_test.wav", mode=olivepy.messaging.msgutil.AudioTransferType.AUDIO_PATH, validate_local_path=False)
        self.assertEqual(audio_msg.path, "/tmp/foo/bar/nofile_test.wav")
        self.assertEqual(len(audio_msg.regions), 0)
        self.assertFalse(audio_msg.HasField('audioSamples'))
        self.assertFalse(audio_msg.HasField('selected_channel'))
        self.assertFalse(audio_msg.HasField('label'))

        # try again with a bad file, but make sure an exception is thrown
        try:
            audio_msg = Audio()
            olivepy.messaging.msgutil.package_audio(audio_msg, "/tmp/foo/bar/nofile_test.wav", mode=olivepy.messaging.msgutil.AudioTransferType.AUDIO_PATH)
            self.fail("Audio message created with a file that does not exist locally")
        except:
            pass



    def test_annotations(self):
        # just sent the audio as path for these tests
        audio_msg = Audio()

        # make 3 regions (we don't validate so they don't match the duration of the audio:
        regions = {(0.5, 2.5), (3.75, 6.210), (9.34, 11)}
        # test basic path:
        olivepy.messaging.msgutil.package_audio(audio_msg, self.test_short_file, annotations=regions)

        self.assertEqual(len(audio_msg.regions), 3)
        self.assertFalse(audio_msg.HasField('audioSamples'))
        self.assertFalse(audio_msg.HasField('selected_channel'))
        self.assertFalse(audio_msg.HasField('label'))

        # NOTE: the region float values may have double precision, so convert to float32?

        # check the regions:
        for region in audio_msg.regions:
            match = False
            # r = (np.float(region.start_t), np.float(region.end_t))
            r = (region.start_t, region.end_t)
            for expectd in regions:
                result = np.isclose(list(expectd), list(r))
                if np.all(result):
                # if r == expectd:
                    match = True
                    break
            self.assertTrue(match)

        # also test selected channel
        audio_msg = Audio()
        regions = {(0.5, 2.5), (3.75, 6.210), (9.34, 11)}
        olivepy.messaging.msgutil.package_audio(audio_msg, self.test_short_file, annotations=regions, selected_channel=2)

        self.assertEqual(len(audio_msg.regions), 3)
        self.assertFalse(audio_msg.HasField('audioSamples'))
        self.assertEqual(audio_msg.selected_channel, 2)
        self.assertFalse(audio_msg.HasField('label'))

        # NOTE: the region float values may have double precision, so convert to float32?

        # check the regions:
        for region in audio_msg.regions:
            match = False
            # r = (np.float(region.start_t), np.float(region.end_t))
            r = (region.start_t, region.end_t)
            for expectd in regions:
                result = np.isclose(list(expectd), list(r))
                if np.all(result):
                    # if r == expectd:
                    match = True
                    break
            self.assertTrue(match)

        # test invalid channel selections
        audio_msg = Audio()
        try:
            olivepy.messaging.msgutil.package_audio(audio_msg, self.test_short_file, selected_channel=2, num_channels=1)
            self.fail("Specified an invalid channel")
        except:
            pass

        audio_msg = Audio()
        try:
            olivepy.messaging.msgutil.package_audio(audio_msg, self.test_short_file, selected_channel=0)
            self.fail("Specified an invalid channel (0)")
        except:
            pass


    def test_audiio_buffer(self):
        """Tests packaging an audio input as a buffer"""

        # Now try sending as a buffer
        with open(self.test_short_file, 'rb') as f:
            serialized_buffer = f.read()

        self.assertIsNotNone(serialized_buffer)

        audio_msg = Audio()
        olivepy.messaging.msgutil.package_audio(audio_msg, serialized_buffer, mode=olivepy.messaging.msgutil.AudioTransferType.AUDIO_SERIALIZED)
        self.assertFalse(audio_msg.HasField('path'))
        self.assertEqual(len(audio_msg.regions), 0)
        self.assertFalse(audio_msg.HasField('selected_channel'))
        self.assertFalse(audio_msg.HasField('label'))
        abuff = audio_msg.audioSamples
        self.assertTrue(np.array_equal(abuff.data, serialized_buffer))
        self.assertTrue(abuff.serialized_file)
        self.assertFalse(abuff.HasField('channels'))
        self.assertFalse(abuff.HasField('samples'))
        self.assertFalse(abuff.HasField('rate'))
        self.assertFalse(abuff.HasField('bit_depth'))
        self.assertFalse(abuff.HasField('encoding'))

        # try again, but set some fields that
        audio_msg = Audio()
        olivepy.messaging.msgutil.package_audio(audio_msg, serialized_buffer, mode=olivepy.messaging.msgutil.AudioTransferType.AUDIO_SERIALIZED, sample_rate=16000, num_channels=2)
        self.assertFalse(audio_msg.HasField('path'))
        self.assertEqual(len(audio_msg.regions), 0)
        self.assertFalse(audio_msg.HasField('selected_channel'))
        self.assertFalse(audio_msg.HasField('label'))
        abuff = audio_msg.audioSamples
        self.assertTrue(np.array_equal(abuff.data, serialized_buffer))
        self.assertTrue(abuff.serialized_file)
        self.assertFalse(abuff.HasField('channels'))
        self.assertFalse(abuff.HasField('samples'))
        self.assertFalse(abuff.HasField('rate'))
        self.assertFalse(abuff.HasField('bit_depth'))
        self.assertFalse(abuff.HasField('encoding'))

        # try sending decoded samples
        # NOTE: we just reuse the serialized buffer which is not correct but we don't assume the client has any
        # audio libs installed so not sure how to task.  Could be libsndfile WavReader, FFMPEG, etc
        audio_msg = Audio()
        olivepy.messaging.msgutil.package_audio(audio_msg, serialized_buffer, mode=olivepy.messaging.msgutil.AudioTransferType.AUDIO_DECODED,
                                                sample_rate=16000,
                                                num_samples=400,
                                                num_channels=1)
        self.assertFalse(audio_msg.HasField('path'))
        self.assertEqual(len(audio_msg.regions), 0)
        self.assertFalse(audio_msg.HasField('selected_channel'))
        self.assertFalse(audio_msg.HasField('label'))
        abuff = audio_msg.audioSamples
        self.assertTrue(np.array_equal(abuff.data, serialized_buffer))
        self.assertFalse(abuff.serialized_file)
        self.assertEqual(abuff.channels, 1)
        self.assertEqual(abuff.samples, 400)
        self.assertEqual(abuff.rate, 16000)
        self.assertEqual(abuff.bit_depth, BIT_DEPTH_16)
        self.assertEqual(abuff.encoding, PCM16)


