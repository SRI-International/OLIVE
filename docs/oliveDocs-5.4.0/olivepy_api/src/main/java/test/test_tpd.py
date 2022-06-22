import glob
import shutil
import subprocess
import os
import unittest

from test_utils import TestUtils

class TestTpd(unittest.TestCase):
    '''Tests the TPD plugin.'''
    
    @classmethod
    def setUpClass(cls):
        TestUtils.check_plugin('tpd-embed')
        TestUtils.clean_logs()    
    
    def test_joshua2_js(self): 
        result = TestUtils.cmd(r'%s\OliveEnroll --plugin tpd-embed --enroll windy --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testdata', 'TaadA_1min.wav')))
        
        self.assertEqual(result.returncode, 0)
        self.assertIn("Successfully enrolled: 'windy'", result.stdout)
        self.assertEqual(result.stderr, '') 
        
        result = TestUtils.cmd(r'%s\OliveAnalyze --plugin tpd-embed --region --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'test', 'JoshuaSarahSpeaking.wav')))

        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stderr, '')
        # Checking should be expanded, but for now, just check that the file is the right size
        self.assertEqual(TestUtils.lines_in_file('region.output.txt'), 1)

     
