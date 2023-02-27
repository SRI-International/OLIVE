
import os
import unittest

import olivepy.api.oliveclient as oc
from test_utils import TestUtils

class TestModification(unittest.TestCase):
    '''Tests audio_modification.
       Read README.txt for general information on running tests.'''

    @classmethod
    def setUpClass(cls):
        cls.client = oc.OliveClient(cls.__class__.__name__, "localhost", 5588, 10)
        cls.client.connect()

    @classmethod
    def tearDownClass(cls):    
        cls.client.disconnect()   
   
    def test_audio_mod1(self):
        TestUtils.check_plugin('enh-mmse-v1')
        filename = os.path.join(TestUtils.test_data_root, 'testSuite', 'sid','test', 
                                'BI_1038-0000_M_Sm_Ara_S2.wav')
        success, result = self.client.audio_modification('enh-mmse-v1', 'multi-v1', filename)
        
        self.assertTrue(success)
        self.assertEqual(result.message, '')
        self.assertAlmostEqual(len(result.audio.data), 7964800, 4)
        self.assertEqual(len(result.scores), 3)
        if TestUtils.verbose:
            # TODO print out more details of result
            print('returns {}, {}'.format(success, str(type(result)))) 
                       