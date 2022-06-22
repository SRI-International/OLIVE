
import os
import time
import unittest

import olivepy.api.oliveclient as oc
from test_utils import TestUtils

class TestLong(unittest.TestCase):
    '''This test suite contains tests that take a long time to run.  
       Usually this is because they use longer audio files.
    '''
    
    print('Warning: this test will take a long time to run.')
    longfile = os.path.join(TestUtils.test_data_root, 'testSuite', 'long', 'MLKDream8.wav')

    @classmethod
    def setUpClass(cls):
        cls.client = oc.OliveClient(cls.__class__.__name__, "localhost", 5588, 10)
        cls.client.connect()

    @classmethod
    def tearDownClass(cls):    
        cls.client.disconnect()
        
    def test_sid_embed_mc(self):
        TestUtils.check_plugin('sid-embed-v5-py3')
        joshua = 0
        start_time = time.monotonic()
        results = self.client.analyze_global('sid-embed-v5-py3', 'multicond-v1', self.longfile) 
        end_time = time.monotonic()
        self.assertGreater(len(results), 0)
        
        for result in results:
            if result.class_id == 'joshua':
                joshua = result.score
        self.assertAlmostEqual(joshua, -9.2720, 3)
        if TestUtils.verbose:
            print('Results for sid-embed-v5-py3, multicond-v1, JoshuaSarahSpeaking.wav')
            for result in results:
                print('{} is {}'.format(result.class_id, result.score))
            minutes, seconds = divmod((end_time-start_time), 60)
            print('Used {:.0f}:{:.0f} to process a 16:27 minute file.'
                  .format(minutes, round(seconds))) 
            
    def test_sid_embed_ml(self):
        TestUtils.check_plugin('sid-embed-v5-py3')
        joshua = 0
        start_time = time.monotonic()
        results = self.client.analyze_global('sid-embed-v5-py3', 'multilang-v1', self.longfile) 
        end_time = time.monotonic()
        self.assertGreater(len(results), 0)
        
        for result in results:
            if result.class_id == 'joshua':
                joshua = result.score
        self.assertAlmostEqual(joshua, -8.4938, 3)
        if TestUtils.verbose:
            print('Results for sid-embed-v5-py3, multilang-v1, JoshuaSarahSpeaking.wav')
            for result in results:
                print('{} is {}'.format(result.class_id, result.score))
            minutes, seconds = divmod((end_time-start_time), 60)
            print('Used {:.0f}:{:.0f} to process a 16:27 minute file.'
                  .format(minutes, round(seconds)))           
        
    def test_lid_eng(self):
        TestUtils.check_plugin('lid-embedplda-v1b-py3')
        start_time = time.monotonic()
        results = self.client.analyze_global('lid-embedplda-v1b-py3', 'multi-v1', self.longfile)
        end_time = time.monotonic()
        
        self.assertEqual(len(results), 19)
        for result in results:
            if result.class_id == 'eng':
                eng = result.score
                self.assertAlmostEqual(result.score, -0.350, 3)
            else:
                key = result.class_id
                value = result.score
                self.assertLess(result.score, 0)
        if TestUtils.verbose:
            print('Got {} results.  Eng is {} and {} is {}.'.format(len(results), eng, key, value))
            minutes, seconds = divmod((end_time-start_time), 60)
            print('Used {:.0f}:{:.0f} to process a 16:27 minute file.'
                  .format(minutes, round(seconds)))
            
    def test_audio_mod(self):
        TestUtils.check_plugin('enh-mmse-v1')
        start_time = time.monotonic()
        success, result = self.client.audio_modification('enh-mmse-v1', 'multi-v1', self.longfile)
        end_time = time.monotonic()
        
        self.assertTrue(success)
        self.assertEqual(result.message, '')
        self.assertAlmostEqual(len(result.audio.data), 31597716, 4)
        self.assertEqual(len(result.scores), 3)
        if TestUtils.verbose:
            print('returns {}, {}'.format(success, str(type(result)))) 
            minutes, seconds = divmod((end_time-start_time), 60)
            print('Used {:.0f}:{:.0f} to process a 16:27 minute file.'
                  .format(minutes, round(seconds)))            