

import logging
import os
import time
import unittest

import olivepy.api.oliveclient as oc

class TestNew(unittest.TestCase):
    
    test_data_root = os.environ['TEST_DATA_ROOT']
    verbose = 'TEST_VERBOSE' in os.environ

    def setUp(self):
        #logging.basicConfig(level=logging.DEBUG)
        client_id = "TestNew"
        self.client = oc.OliveClient(client_id, "localhost", 5588, 10)
        self.client.connect()

    def tearDown(self):    
        self.client.disconnect()    
   
    def test_audio_mod1(self):
        filename = os.path.join(self.test_data_root, 'testSuite', 'sid','test', 'BI_1038-0000_M_Sm_Ara_S2.wav')
        success, result = self.client.audio_modification('enh-mmse', 'multi-v1', filename)
        self.assertTrue(success)
        self.assertEqual(result.message, '')
        self.assertAlmostEqual(len(result.audio.data), 7964800, 4)
        self.assertEqual(len(result.scores), 3)
        if self.verbose:
            print('returns {}, {}'.format(success, str(type(result))))
            
    def test_audio_mod2(self):
        filename = os.path.join(self.test_data_root, 'testSuite', 'long', 'MLKDream8.wav')
        success, result = self.client.audio_modification('enh-mmse-v1', 'multi-v1', filename)
        self.assertTrue(success)
        self.assertEqual(result.message, '')
        self.assertAlmostEqual(len(result.audio.data), 31597716, 4)
        self.assertEqual(len(result.scores), 3)
        if self.verbose:
            print('returns {}, {}'.format(success, str(type(result))))