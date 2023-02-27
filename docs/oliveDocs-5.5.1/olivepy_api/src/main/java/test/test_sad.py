import glob
import shutil
import subprocess
import os
import unittest

from test_utils import TestUtils

class TestSad(unittest.TestCase):
    '''Tests the SAD plugin.'''
    
    @classmethod
    def setUpClass(cls):
        TestUtils.check_plugin('sad-dnn')
        TestUtils.clean_logs()    
    
    def test_js_speaking(self):        
        result = TestUtils.cmd('OliveAnalyze --plugin sad-dnn --sad --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'test', 'JoshuaSarahSpeaking.wav')))

        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stderr, '')
        # Checking should be expanded, but for now, just check that the file is the right size
        self.assertEqual(TestUtils.lines_in_file(
            os.path.join('OUTPUT', 'JoshuaSarahSpeaking.wav.scores')), 19004)

    def test_10777a_speaking(self):        
        result = TestUtils.cmd('OliveAnalyze --plugin sad-dnn --sad --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sad', 'test', '20131209T225239UTC_10777_A.wav')))

        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stderr, '')
        # Checking should be expanded, but for now, just check that the file is the right size
        self.assertEqual(TestUtils.lines_in_file(
            os.path.join('OUTPUT', '20131209T225239UTC_10777_A.wav.scores')), 11998)       
