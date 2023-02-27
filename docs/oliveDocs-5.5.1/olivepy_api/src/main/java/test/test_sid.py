import glob
import shutil
import subprocess
import os
import unittest

from test_utils import TestUtils

class TestSid(unittest.TestCase):
    '''Tests the SID plugin.'''
    
    @classmethod
    def setUpClass(cls):
        TestUtils.check_plugin('sid-embed')
        TestUtils.clean_logs()    
    
    def test_joshua2_js(self): 
        result = TestUtils.cmd(r'%s\OliveEnroll --plugin sid-embed3 --enroll joshua --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'enroll', 'joshua2.wav')))
        
        self.assertEqual(result.returncode, 0)
        self.assertIn("Successfully enrolled: 'joshua'", result.stdout)
        self.assertEqual(result.stderr, '') 
        
        result = TestUtils.cmd(r'%s\OliveAnalyze --plugin sid-embed --sid --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'test', 'JoshuaSarahSpeaking.wav')))

        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stderr, '')
        # Checking should be expanded, but for now, just check that the file is the right size
        self.assertEqual(TestUtils.lines_in_file('global.output.txt'), 1)

     
