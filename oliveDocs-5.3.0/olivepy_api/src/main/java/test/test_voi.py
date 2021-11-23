import glob
import shutil
import subprocess
import os
import unittest

from test_utils import TestUtils

class TestVoi(unittest.TestCase):
    '''Tests the VOI plugin.'''
    
    @classmethod
    def setUpClass(cls):
        TestUtils.check_plugin('voi-speakingstyle')
        TestUtils.clean_logs()    
    
    def test_jss1(self): 
        
        result = TestUtils.cmd(r'%s\OliveAnalyze --plugin voi-speakingstyle --timeout 120 --region --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'test', 'JoshuaSarahSpeaking.wav')))

        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stderr, '')
        # Checking should be expanded, but for now, just check that the file is the right size
        self.assertEqual(TestUtils.lines_in_file('region.output.txt'), 9)

     
